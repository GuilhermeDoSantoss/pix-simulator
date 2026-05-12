package com.pix_simulator.Pix.Simulator.pix.entity;

/**
 * Ciclo de vida de uma transação PIX.
 *
 * PENDING    → Aceita pelo HTTP, chave de idempotência persistida, evento publicado no Kafka.
 *              Nenhum saldo foi movido ainda.
 *
 * PROCESSING → Consumer Kafka recebeu o evento e está processando.
 *              Verificação de anomalia em andamento. Saldo ainda não movido.
 *
 * COMPLETED  → Processamento concluído com sucesso.
 *              Saldo debitado do remetente e creditado ao destinatário.
 *              Estado final de sucesso.
 *
 * FAILED     → Processamento falhou por razão técnica ou de negócio.
 *              Exemplos: saldo insuficiente no momento do processamento,
 *              conta desativada entre PENDING e PROCESSING.
 *              Nenhum saldo foi movido.
 *
 * FLAGGED    → Anomalia detectada pelo AnomalyDetectorService.
 *              O valor da transação excede o threshold configurado
 *              em relação à média histórica do remetente.
 *              Aguarda revisão manual. Nenhum saldo foi movido.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    FLAGGED
}
