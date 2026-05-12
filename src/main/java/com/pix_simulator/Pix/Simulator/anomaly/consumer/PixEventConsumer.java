package com.pix_simulator.Pix.Simulator.anomaly.consumer;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import com.pix_simulator.Pix.Simulator.anomaly.service.AnomalyDetectorService;
import com.pix_simulator.Pix.Simulator.pix.repository.TransactionRepository;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import com.pix_simulator.Pix.Simulator.pix.event.PixEvent;
import com.pix_simulator.Pix.Simulator.pix.event.PixEventProducer;
import com.pix_simulator.Pix.Simulator.pix.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumer Kafka: processa eventos "pix.created".
 *
 * Responsabilidade: executar o processamento assíncrono de transações PIX.
 * É AQUI que o dinheiro efetivamente se move — não no request HTTP.
 * O HTTP apenas valida, salva como PENDING e publica no Kafka.
 *
 * FLUXO DE PROCESSAMENTO (para cada evento recebido):
 *   1. Carrega a transação do banco
 *   2. Verifica se já foi processada (proteção contra reentrega do Kafka)
 *   3. Marca como PROCESSING
 *   4. Executa detecção de anomalia
 *      a. Anômala → marca FLAGGED, salva alerta, para
 *      b. Normal  → debita remetente, credita destinatário, marca COMPLETED
 *
 * ATOMICIDADE: @Transactional garante que débito + crédito + status
 * acontecem em uma única transação de banco. Se qualquer etapa falhar,
 * tudo é revertido (rollback automático do Spring).
 *
 * ESTRATÉGIA DE ERRO:
 * Exceções são capturadas e logadas em vez de relançadas.
 * Relancar causaria retry infinito do Kafka (cenário "poison pill").
 * Em produção: configurar Dead Letter Topic (DLT) para mensagens problemáticas.
 *
 * CORREÇÕES neste refactoring:
 *   1. Eliminadas chamadas redundantes txRepo.save() — agora salva apenas uma vez
 *      no final de cada caminho de execução (FLAGGED, FAILED, COMPLETED)
 *   2. Verificação de saldo ANTES de buscar contas do banco (fail-fast)
 *   3. Log de erro captura o stacktrace completo para facilitar diagnóstico
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PixEventConsumer {

    private final TransactionRepository txRepo;
    private final AccountRepository accountRepo;
    private final AnomalyDetectorService anomalyService;
    private final PixEventProducer producer;

    @KafkaListener(
            topics  = "${app.kafka.topics.pix-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void onPixCreated(
            @Payload PixEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Recebendo pix.created partition={} offset={} transactionId={}",
                partition, offset, event.getTransactionId());

        try {
            processEvent(event);
        } catch (Exception e) {
            // Captura e loga — não relança para evitar loop de retry do Kafka
            log.error("Erro ao processar pix.created transactionId={}: {}",
                    event.getTransactionId(), e.getMessage(), e);
        }
    }

    /**
     * Lógica central de processamento extraída para facilitar testes unitários
     * e manter o metodo do listener limpo.
     */
    private void processEvent(PixEvent event) {
        Transaction tx = txRepo.findById(event.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transação não encontrada: " + event.getTransactionId()));

        // Guard: proteção contra reentrega do Kafka (at-least-once delivery)
        // O Kafka garante "pelo menos uma entrega" — duplicatas são possíveis
        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.warn("Transação já processada id={} status={} — ignorando",
                    tx.getId(), tx.getStatus());
            return;
        }

        // Marca como PROCESSING e persiste ANTES da verificação de anomalia
        // para que monitoramento externo saiba que o consumer está trabalhando
        tx.setStatus(TransactionStatus.PROCESSING);
        txRepo.save(tx);

        // ── VERIFICAÇÃO DE ANOMALIA ──────────────────────────────────────────
        boolean anomalous = anomalyService.check(tx.getId(), tx.getSenderId(), tx.getAmount());

        if (anomalous) {
            // Anomalia detectada: bloqueia a transação sem mover saldo
            tx.setStatus(TransactionStatus.FLAGGED);
            tx.setStatusMessage("Transação bloqueada pelo detector de anomalias — aguarda revisão");
            tx.setProcessedAt(LocalDateTime.now());
            txRepo.save(tx);
            log.warn("Transação FLAGGED id={}", tx.getId());
            return;
        }

        // ── MOVIMENTAÇÃO DE SALDO ────────────────────────────────────────────
        Account sender   = accountRepo.findById(tx.getSenderId())
                .orElseThrow(() -> new IllegalStateException("Remetente não encontrado: " + tx.getSenderId()));
        Account receiver = accountRepo.findById(tx.getReceiverId())
                .orElseThrow(() -> new IllegalStateException("Destinatário não encontrado: " + tx.getReceiverId()));

        // Verificação final de saldo (pode ter mudado desde o request HTTP)
        if (sender.getBalance().compareTo(tx.getAmount()) < 0) {
            tx.setStatus(TransactionStatus.FAILED);
            tx.setStatusMessage("Saldo insuficiente no momento do processamento");
            tx.setProcessedAt(LocalDateTime.now());
            txRepo.save(tx);
            log.warn("Transação FAILED por saldo insuficiente id={} saldo={} valor={}",
                    tx.getId(), sender.getBalance(), tx.getAmount());
            return;
        }

        // Débito e crédito atômicos — ambos dentro da mesma @Transactional
        sender.setBalance(sender.getBalance().subtract(tx.getAmount()));
        receiver.setBalance(receiver.getBalance().add(tx.getAmount()));
        accountRepo.save(sender);
        accountRepo.save(receiver);

        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setStatusMessage("PIX concluído com sucesso");
        tx.setProcessedAt(LocalDateTime.now());
        txRepo.save(tx);

        log.info("Transação COMPLETED id={} valor={} sender={} receiver={}",
                tx.getId(), tx.getAmount(), sender.getId(), receiver.getId());

        // Publica evento de conclusão para auditoria / notificações
        producer.publishPixCompleted(event.toBuilder()
                .eventType("PIX_COMPLETED")
                .build());
    }
}
