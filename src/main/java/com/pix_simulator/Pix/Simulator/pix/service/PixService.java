package com.pix_simulator.Pix.Simulator.pix.service;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import com.pix_simulator.Pix.Simulator.account.service.AccountService;
import com.pix_simulator.Pix.Simulator.idempotency.dto.IdempotencyEntry;
import com.pix_simulator.Pix.Simulator.idempotency.service.IdempotencyService;
import com.pix_simulator.Pix.Simulator.pix.dto.PixDTO;
import com.pix_simulator.Pix.Simulator.pix.entity.Transaction;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import com.pix_simulator.Pix.Simulator.pix.event.PixEvent;
import com.pix_simulator.Pix.Simulator.pix.event.PixEventProducer;
import com.pix_simulator.Pix.Simulator.pix.repository.TransactionRepository;
import com.pix_simulator.Pix.Simulator.shared.exception.BusinessException;
import com.pix_simulator.Pix.Simulator.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serviço principal de transferências PIX — refatorado com Redis.
 *
 * Responsabilidade: orquestrar o fluxo completo de um PIX,
 * agora com idempotência em dois níveis para máxima robustez.
 *
 * ARQUITETURA DE IDEMPOTÊNCIA EM DOIS NÍVEIS:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  NÍVEL 1 — Redis (rápido, principal)                                │
 * │  - Lookup O(1) em ~0.1ms                                           │
 * │  - Retorna resposta completa sem query ao banco                     │
 * │  - Lock distribuído (SETNX) previne processamento concorrente       │
 * │  - TTL de 24h: chaves expiram automaticamente                       │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  NÍVEL 2 — Banco H2 (fallback, permanente)                          │
 * │  - UNIQUE constraint na coluna idempotencyKey                       │
 * │  - Ativado se Redis estiver indisponível                            │
 * │  - Garante que nunca haverá duas linhas com a mesma chave           │
 * │  - Resposta ligeiramente mais lenta (~5-20ms) mas sempre segura    │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * FLUXO COMPLETO (9 etapas):
 * ┌─────────────────────────────────────────────────────────┐
 * │  1. Receber request com idempotencyKey (UUID v4)         │
 * │  2. Validar formato UUID v4                              │
 * │  3. JWT já validado pelo JwtAuthenticationFilter         │
 * │  4. Checar Redis → cache hit = retorno imediato          │
 * │  5. Adquirir lock distribuído no Redis                   │
 * │  6. Validar contas e saldo                               │
 * │  7. Salvar transação como PENDING no banco H2            │
 * │  8. Armazenar no Redis com TTL de 24h                    │
 * │  9. Publicar evento no Kafka → consumer processa async   │
 * └─────────────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixService {

    private final TransactionRepository txRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final PixEventProducer eventProducer;
    private final IdempotencyService idempotencyService;

    /**
     * Inicia uma transferência PIX com idempotência garantida via Redis.
     *
     * SEGURANÇA: senderId vem sempre do JWT (AccountPrincipal), nunca do body.
     * Isso previne IDOR — um usuário não pode enviar PIX em nome de outro.
     *
     * IDEMPOTÊNCIA: o mesmo UUID pode ser reenviado quantas vezes o cliente quiser.
     * Somente o primeiro processamento toca o saldo.
     *
     * @param senderId ID do remetente extraído do JWT
     * @param request  dados do PIX incluindo idempotencyKey (UUID v4)
     */
    @Transactional
    public PixDTO.PixResponse send(Long senderId, PixDTO.SendRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // ── ETAPA 2: VALIDAÇÃO UUID v4 ───────────────────────────────────────
        // Rejeita chaves com formato inválido antes de qualquer operação.
        // UUID v4 garante 122 bits de entropia — praticamente impossível colidir.
        if (!idempotencyService.isValidUuidV4(idempotencyKey)) {
            throw new BusinessException(
                    "Formato inválido para idempotencyKey. Use UUID v4: " +
                            "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx");
        }

        // ── ETAPA 4: VERIFICAÇÃO DE CACHE NO REDIS (Nível 1) ────────────────
        // Consulta Redis em ~0.1ms sem tocar o banco H2.
        Optional<IdempotencyEntry> cached = idempotencyService.findCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Cache hit Redis — chave={}", idempotencyKey);
            return buildPixResponseFromEntry(cached.get(), true);
        }

        // ── ETAPA 5: LOCK DISTRIBUÍDO ────────────────────────────────────────
        // SETNX atômico garante que apenas um thread processa esta chave por vez.
        // Sem este lock, dois requests simultâneos com a mesma chave poderiam
        // ambos passar pela verificação de cache antes que um deles armazenasse.
        if (!idempotencyService.acquireLock(idempotencyKey)) {
            throw new BusinessException(
                    "Transação com esta chave já está sendo processada. " +
                            "Aguarde e tente novamente em alguns segundos.");
        }

        try {
            // Segunda verificação após o lock — outro thread pode ter processado
            // enquanto este esperava o lock (double-checked locking pattern)
            Optional<IdempotencyEntry> cachedAfterLock = idempotencyService.findCachedResponse(idempotencyKey);
            if (cachedAfterLock.isPresent()) {
                log.info("Cache hit após lock — chave={}", idempotencyKey);
                return buildPixResponseFromEntry(cachedAfterLock.get(), true);
            }

            return processNewPixTransaction(senderId, request);

        } finally {
            // SEMPRE libera o lock, mesmo em caso de exceção
            idempotencyService.releaseLock(idempotencyKey);
        }
    }

    /**
     * Processa um PIX novo (não encontrado no cache Redis).
     * Separado do metodo send() para maior clareza e testabilidade.
     *
     * Contém as validações de negócio e a persistência.
     * O banco H2 ainda tem a constraint UNIQUE como barreira final (Nível 2).
     */
    private PixDTO.PixResponse processNewPixTransaction(Long senderId, PixDTO.SendRequest request) {

        // ── ETAPA 6: VALIDAÇÃO DE CONTAS ─────────────────────────────────────
        Account sender = accountService.findActive(senderId);

        Account receiver = accountRepository.findByPixKey(request.getReceiverPixKey())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhuma conta encontrada para a chave PIX: " + request.getReceiverPixKey()));

        if (!receiver.getActive()) {
            throw new BusinessException("Conta do destinatário está desativada");
        }

        if (sender.getId().equals(receiver.getId())) {
            throw new BusinessException("Não é possível fazer PIX para si mesmo");
        }

        // Validação de saldo (fail-fast — antes de qualquer escrita no banco)
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException(String.format(
                    "Saldo insuficiente. Disponível: R$%.2f | Solicitado: R$%.2f",
                    sender.getBalance(), request.getAmount()));
        }

        // ── ETAPA 7: SALVAR NO BANCO H2 COMO PENDING ─────────────────────────
        // Persiste antes do Kafka: trilha de auditoria existe mesmo se Kafka falhar.
        // A UNIQUE constraint do banco é o fallback de segurança (Nível 2).
        Transaction transaction = txRepository.save(Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .senderId(senderId)
                .receiverId(receiver.getId())
                .receiverPixKey(request.getReceiverPixKey())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .build());

        log.info("Transação PENDING id={} valor={}", transaction.getId(), transaction.getAmount());

        // ── ETAPA 8: ARMAZENAR NO REDIS COM TTL ──────────────────────────────
        // A partir daqui qualquer retentativa com a mesma chave
        // recebe esta resposta do Redis sem query ao banco.
        IdempotencyEntry entry = IdempotencyEntry.builder()
                .transactionId(transaction.getId())
                .idempotencyKey(request.getIdempotencyKey())
                .status(TransactionStatus.PENDING)
                .amount(transaction.getAmount())
                .receiverPixKey(transaction.getReceiverPixKey())
                .receiverName(receiver.getName())
                .createdAt(transaction.getCreatedAt())
                .build();

        idempotencyService.storeResponse(request.getIdempotencyKey(), entry);

        // ── ETAPA 9: PUBLICAR NO KAFKA ────────────────────────────────────────
        // PixEventConsumer vai: verificar anomalia → mover saldo → marcar COMPLETED
        eventProducer.publishPixCreated(PixEvent.builder()
                .transactionId(transaction.getId())
                .senderId(senderId)
                .receiverId(receiver.getId())
                .amount(request.getAmount())
                .receiverPixKey(request.getReceiverPixKey())
                .createdAt(transaction.getCreatedAt())
                .eventType("PIX_CREATED")
                .build());

        return buildPixResponseFromEntry(entry, false);
    }

    /**
     * Histórico de transferências do usuário autenticado, mais recente primeiro.
     */
    @Transactional(readOnly = true)
    public List<PixDTO.HistoryItem> getHistory(Long accountId) {
        return txRepository.findBySenderIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(tx -> {
                    String receiverName = accountRepository.findById(tx.getReceiverId())
                            .map(Account::getName).orElse("Desconhecido");
                    return PixDTO.HistoryItem.builder()
                            .id(tx.getId())
                            .idempotencyKey(tx.getIdempotencyKey())
                            .status(tx.getStatus())
                            .amount(tx.getAmount())
                            .receiverPixKey(tx.getReceiverPixKey())
                            .receiverName(receiverName)
                            .description(tx.getDescription())
                            .createdAt(tx.getCreatedAt())
                            .processedAt(tx.getProcessedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Detalhes de uma transação específica.
     *
     * SEGURANÇA (IDOR): verifica que a transação pertence ao usuário autenticado.
     * Mesmo que alguém adivinhe um transactionId, só o sender original pode vê-lo.
     */
    @Transactional(readOnly = true)
    public PixDTO.PixResponse getById(Long transactionId, Long accountId) {
        Transaction tx = txRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transação não encontrada: " + transactionId));

        if (!tx.getSenderId().equals(accountId)) {
            throw new BusinessException("Acesso negado: transação pertence a outra conta");
        }

        String receiverName = accountRepository.findById(tx.getReceiverId())
                .map(Account::getName).orElse("Desconhecido");

        return buildPixResponse(tx, receiverName, false);
    }

    // ── Métodos auxiliares de mapeamento ─────────────────────────────────────

    /** Constrói PixResponse a partir de um IdempotencyEntry do Redis */
    private PixDTO.PixResponse buildPixResponseFromEntry(IdempotencyEntry entry, boolean isIdempotentHit) {
        return PixDTO.PixResponse.builder()
                .transactionId(entry.getTransactionId())
                .idempotencyKey(entry.getIdempotencyKey())
                .status(entry.getStatus())
                .amount(entry.getAmount())
                .receiverPixKey(entry.getReceiverPixKey())
                .receiverName(entry.getReceiverName())
                .message(isIdempotentHit
                        ? "Requisição duplicada — retornando resultado original do cache"
                        : entry.getStatusMessage())
                .idempotentHit(isIdempotentHit)
                .processedAt(entry.getProcessedAt())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    /** Constrói PixResponse a partir de uma entidade Transaction (para getById) */
    private PixDTO.PixResponse buildPixResponse(Transaction tx, String receiverName, boolean isIdempotentHit) {
        return PixDTO.PixResponse.builder()
                .transactionId(tx.getId())
                .idempotencyKey(tx.getIdempotencyKey())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .receiverPixKey(tx.getReceiverPixKey())
                .receiverName(receiverName)
                .message(isIdempotentHit
                        ? "Requisição duplicada — retornando resultado original"
                        : tx.getStatusMessage())
                .idempotentHit(isIdempotentHit)
                .processedAt(tx.getProcessedAt())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
