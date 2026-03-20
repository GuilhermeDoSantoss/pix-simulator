package com.pix_simulator.Pix.Simulator.anomaly;


public enum AlertStatus {
    /** Alerta criado, ainda não revisado */
    OPEN,
    /** Um analista revisou e confirmou como suspeito */
    REVIEWED,
    /** Identificado como falso positivo após revisão */
    FALSE_POSITIVE
}
