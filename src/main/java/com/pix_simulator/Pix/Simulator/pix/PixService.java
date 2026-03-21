package com.pix_simulator.Pix.Simulator.pix;



import com.pix_simulator.Pix.Simulator.account.Account;
import com.pix_simulator.Pix.Simulator.account.AccountRepository;
import com.pix_simulator.Pix.Simulator.account.AccountService;
import com.pix_simulator.Pix.Simulator.entity.Transaction;
import com.pix_simulator.Pix.Simulator.entity.TransactionRepository;
import com.pix_simulator.Pix.Simulator.entity.TransactionStatus;
import com.pix_simulator.Pix.Simulator.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço principal do PIX.
 *
 * Responsabilidades:
 * 1. Verificar idempotência antes de qualquer processamento
 * 2. Validar remetente e destinatário
 * 3. Verificar saldo disponível
 * 4. Executar débito e crédito de forma atômica (@Transactional)
 * 5. Registrar log da transação
 * 6. Publicar evento no Kafka para análise de anomalia
 *
 * FLUXO COMPLETO DO PIX:
 * ┌─────────────────────────────────────────────────────────┐
 * │ 1. Recebe requisição com idempotencyKey                 │
 * │ 2. Verifica Redis: chave já existe?                     │
 * │    └─ SIM: retorna transação existente (sem debitar)    │
 * │    └─ NÃO: continua                                     │
 * │ 3. Valida contas e saldo                                │
 * │ 4. Cria transação com status PENDING                    │
 * │ 5. Debita da conta origem                               │
 * │ 6. Credita na conta destino                             │
 * │ 7. Atualiza status para COMPLETED                       │
 * │ 8. Salva chave no Redis (TTL 24h)                       │
 * │ 9. Publica evento no Kafka                              │
 * └─────────────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final IdempotencyService idempotencyService;
    private final PixEventProducer pixEventProducer;

    /**
     * Processa um PIX com garantia de idempotência.
     *
     * - @Transactional garante que todas as operações de banco são atômicas:
     * se qualquer passo falhar, TUDO é revertido (débito, crédito, log).
     * Sem @Transactional: poderia debitar e não creditar, ou vice-versa.
     *
     * - @param senderId       ID da conta remetente (extraído do JWT)
     * - @param request        dados do PIX (chave, valor, idempotencyKey)
     * - @return resultado do processamento ou resposta cacheada se for duplicata
     */
    @Transactional
    public PixDTO.PixResponse sendPix(Long senderId, PixDTO.PixRequest request) {
        log.info("Iniciando PIX - remetente: {}, destinatário: {}, valor: R$ {}, chave: {}",
                senderId, request.getReceiverPixKey(), request.getAmount(), request.getIdempotencyKey());

        // ============================================================
        // PASSO 1: VERIFICAÇÃO DE IDEMPOTÊNCIA
        // Antes de qualquer validação, verificamos se esse PIX já foi processado.
        // Isso evita duplo débito mesmo se as validações posteriores demorassem.
        // ============================================================
        if (idempotencyService.isDuplicate(senderId, request.getIdempotencyKey())) {
            log.info("Idempotência ativada - retornando resultado cacheado para chave: {}",
                    request.getIdempotencyKey());
            return buildIdempotentResponse(senderId, request.getIdempotencyKey());
        }

        // ============================================================
        // PASSO 2: VALIDAR CONTAS
        // ============================================================

        // Carrega e valida a conta remetente (ativa, existe no banco)
        Account sender = accountService.findActiveAccountById(senderId);

        // Impede que a conta faça PIX para si mesma
        Account receiver = accountRepository.findByPixKey(request.getReceiverPixKey())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhuma conta encontrada com a chave PIX: " + request.getReceiverPixKey()));

        if (!receiver.getActive()) {
            throw new BusinessException("A conta destinatária está inativa");
        }

        // Garante que remetente e destinatário são diferentes
        if (sender.getId().equals(receiver.getId())) {
            throw new BusinessException("Não é possível fazer PIX para a própria conta");
        }

        // ============================================================
        // PASSO 3: VALIDAR SALDO
        // ============================================================
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BusinessException(String.format(
                    "Saldo insuficiente. Disponível: R$ %.2f | Solicitado: R$ %.2f",
                    sender.getBalance(), request.getAmount()));
        }

        // ============================================================
        // PASSO 4: CRIAR REGISTRO DA TRANSAÇÃO (status PENDING)
        // Criamos o registro antes de movimentar o saldo para ter
        // um log completo mesmo em caso de falha.
        // ============================================================
        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .senderId(senderId)
                .receiverId(receiver.getId())
                .receiverPixKey(request.getReceiverPixKey())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Transação criada com ID: {} | status: PENDING", transaction.getId());

        try {
            // ============================================================
            // PASSO 5: DÉBITO DA CONTA REMETENTE
            // subtract() retorna novo BigDecimal - BigDecimal é imutável
            // ============================================================
            BigDecimal newSenderBalance = sender.getBalance().subtract(request.getAmount());
            sender.setBalance(newSenderBalance);
            accountRepository.save(sender);
            log.info("Débito realizado - conta: {} | novo saldo: R$ {}", senderId, newSenderBalance);

            // ============================================================
            // PASSO 6: CRÉDITO NA CONTA DESTINATÁRIA
            // ============================================================
            BigDecimal newReceiverBalance = receiver.getBalance().add(request.getAmount());
            receiver.setBalance(newReceiverBalance);
            accountRepository.save(receiver);
            log.info("Crédito realizado - conta: {} | novo saldo: R$ {}", receiver.getId(), newReceiverBalance);

            // ============================================================
            // PASSO 7: ATUALIZAR STATUS PARA COMPLETED
            // ============================================================
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setStatusMessage("PIX processado com sucesso");
            transaction.setProcessedAt(LocalDateTime.now());
            transaction = transactionRepository.save(transaction);
            log.info("Transação {} concluída com sucesso", transaction.getId());

        } catch (Exception e) {
            // Em caso de qualquer erro no débito/crédito, marca a transação como FAILED
            // @Transactional faz o rollback do banco automaticamente
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setStatusMessage("Erro ao processar: " + e.getMessage());
            transactionRepository.save(transaction);
            log.error("Falha ao processar transação {}: {}", transaction.getId(), e.getMessage());
            throw e; // Re-lança para o @Transactional fazer o rollback
        }

        // ============================================================
        // PASSO 8: SALVAR CHAVE DE IDEMPOTÊNCIA NO REDIS
        // Feito APÓS o sucesso para garantir que só salvamos se o PIX foi OK.
        // ============================================================
        idempotencyService.saveKey(senderId, request.getIdempotencyKey(), transaction.getId());

        // ============================================================
        // PASSO 9: PUBLICAR EVENTO NO KAFKA
        // O Anomaly Detector consome esse evento de forma assíncrona.
        // Feito APÓS commit para não publicar evento de transação com rollback.
        // ============================================================
        PixEvent event = PixEvent.builder()
                .transactionId(transaction.getId())
                .senderId(senderId)
                .receiverId(receiver.getId())
                .amount(request.getAmount())
                .receiverPixKey(request.getReceiverPixKey())
                .processedAt(transaction.getProcessedAt())
                .eventType("PIX_COMPLETED")
                .build();

        pixEventProducer.publishPixEvent(event);

        return buildPixResponse(transaction, receiver.getName(), false);
    }

    /**
     * Retorna o histórico de transações da conta autenticada.
     */
    @Transactional(readOnly = true)
    public List<PixDTO.TransactionHistoryItem> getHistory(Long accountId) {
        List<Transaction> transactions =
                transactionRepository.findBySenderIdOrderByCreatedAtDesc(accountId);

        return transactions.stream()
                .map(t -> {
                    // Busca o nome do destinatário para exibir no histórico
                    String receiverName = accountRepository.findById(t.getReceiverId())
                            .map(Account::getName)
                            .orElse("Conta não encontrada");

                    return PixDTO.TransactionHistoryItem.builder()
                            .id(t.getId())
                            .idempotencyKey(t.getIdempotencyKey())
                            .status(t.getStatus())
                            .amount(t.getAmount())
                            .receiverPixKey(t.getReceiverPixKey())
                            .receiverName(receiverName)
                            .description(t.getDescription())
                            .createdAt(t.getCreatedAt())
                            .processedAt(t.getProcessedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Busca uma transação específica pelo ID.
     * Valida que a transação pertence à conta autenticada (segurança).
     */
    @Transactional(readOnly = true)
    public PixDTO.PixResponse getTransaction(Long transactionId, Long accountId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transação não encontrada com ID: " + transactionId));

        // Garante que o usuário só vê suas próprias transações
        if (!transaction.getSenderId().equals(accountId)) {
            throw new BusinessException("Acesso negado: esta transação não pertence à sua conta");
        }

        String receiverName = accountRepository.findById(transaction.getReceiverId())
                .map(Account::getName)
                .orElse("Conta não encontrada");

        return buildPixResponse(transaction, receiverName, false);
    }

    /**
     * Constrói a resposta quando detectamos uma requisição duplicada (idempotência).
     * Busca a transação original pelo ID salvo no Redis e retorna com flag idempotentResponse=true.
     */
    private PixDTO.PixResponse buildIdempotentResponse(Long accountId, String idempotencyKey) {
        Long transactionId = idempotencyService.getTransactionId(accountId, idempotencyKey);

        if (transactionId == null) {
            throw new BusinessException("Erro interno: chave idempotente encontrada mas transação não localizada");
        }

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException("Transação original não encontrada"));

        String receiverName = accountRepository.findById(transaction.getReceiverId())
                .map(Account::getName)
                .orElse("Conta não encontrada");

        // Marca como idempotent para o cliente saber que veio do cache
        return buildPixResponse(transaction, receiverName, true);
    }

    /**
     * Monta o DTO de resposta a partir de uma entidade Transaction.
     */
    private PixDTO.PixResponse buildPixResponse(Transaction t, String receiverName, boolean idempotent) {
        String message = idempotent
                ? "Requisição duplicada detectada. Retornando resultado original."
                : (t.getStatus() == TransactionStatus.COMPLETED
                ? "PIX realizado com sucesso"
                : "PIX com status: " + t.getStatus());

        return PixDTO.PixResponse.builder()
                .transactionId(t.getId())
                .idempotencyKey(t.getIdempotencyKey())
                .status(t.getStatus())
                .amount(t.getAmount())
                .receiverPixKey(t.getReceiverPixKey())
                .receiverName(receiverName)
                .message(message)
                .idempotentResponse(idempotent)
                .processedAt(t.getProcessedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
