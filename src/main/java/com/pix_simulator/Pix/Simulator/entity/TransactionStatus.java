package com.pix_simulator.Pix.Simulator.entity;

/**
 * Status possíveis de uma transação PIX.
 *
 * Fluxo normal:
 * PENDING → PROCESSING → COMPLETED
 *
 * Fluxo de erro:
 * PENDING → PROCESSING → FAILED
 *
 * Idempotência:
 * Se a transação já existe com qualquer status, a segunda requisição
 * retorna a transação existente sem reprocessar.
 */
public enum TransactionStatus {

    /**
     * Transação criada mas ainda não iniciada.
     * O saldo ainda NÃO foi debitado.
     */
    PENDING,

    /**
     * Transação em processamento.
     * O saldo foi reservado (debitado da conta origem) mas ainda não creditado no destino.
     * Útil para simular latência e processamento assíncrono.
     */
    PROCESSING,

    /**
     * Transação concluída com sucesso.
     * Saldo debitado da origem E creditado no destino.
     * Evento publicado no Kafka para análise de anomalia.
     */
    COMPLETED,

    /**
     * Transação falhou por algum motivo (saldo insuficiente, conta inativa, etc.).
     * Saldo devolvido à conta origem se já havia sido debitado.
     */
    FAILED,

    /**
     * Transação cancelada antes do processamento.
     */
    CANCELLED
}
