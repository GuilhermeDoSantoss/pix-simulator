package com.pix_simulator.Pix.Simulator.anomaly.entity;

/**
 * Status do processo de revisão de um alerta de anomalia.
 *
 * OPEN          → Alerta recém-criado, aguardando revisão manual.
 *                 A transação correspondente está FLAGGED e os saldos não foram movidos.
 *
 * REVIEWED      → Alerta revisado e confirmado como suspeito/fraude.
 *                 A transação deve ser cancelada definitivamente.
 *
 * FALSE_POSITIVE → Alerta revisado e considerado legítimo pelo operador.
 *                  A transação pode ser reprocessada manualmente.
 */
public enum AlertStatus {
    OPEN,
    REVIEWED,
    FALSE_POSITIVE
}
