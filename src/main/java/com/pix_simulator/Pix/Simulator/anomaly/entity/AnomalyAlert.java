package com.pix_simulator.Pix.Simulator.anomaly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade JPA que representa um alerta de anomalia.
 *
 * Responsabilidade: persistir cada alerta gerado pelo AnomalyDetectorService
 * quando uma transação é identificada como suspeita.
 *
 * Um alerta é criado quando a razão entre o valor da transação e a média
 * histórica do remetente ultrapassa o threshold configurável (padrão: 10×).
 *
 * A transação vinculada fica com status FLAGGED e os saldos NÃO são movidos.
 * Em um sistema real, um operador revisaria os alertas OPEN e decidiria
 * aprovar (mover para REVIEWED) ou rejeitar (FALSE_POSITIVE).
 *
 * EXEMPLO:
 *   Remetente costuma enviar R$ 50/transação (média 30 dias)
 *   Tenta enviar R$ 5.000 → razão = 100× → FLAGGED
 *   AnomalyAlert criado: amount=5000, avg=50, ratio=100, status=OPEN
 */
@Entity
@Table(
        name = "anomaly_alerts",
        indexes = {
                @Index(name = "idx_alert_account_id",     columnList = "accountId"),
                @Index(name = "idx_alert_transaction_id", columnList = "transactionId"),
                @Index(name = "idx_alert_status",         columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID da conta remetente que gerou a transação suspeita */
    @Column(nullable = false)
    private Long accountId;

    /** ID da transação flagrada — vincula o alerta à transação */
    @Column(nullable = false)
    private Long transactionId;

    /** Valor da transação que disparou o alerta */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal transactionAmount;

    /** Média histórica de 30 dias usada como base de comparação */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal historicalAverage;

    /**
     * Razão = transactionAmount / historicalAverage.
     * Exemplo: 100.00 significa que o valor é 100× a média histórica.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal ratio;

    /** Descrição legível da anomalia — exibida ao operador na revisão */
    @Column(nullable = false, length = 500)
    private String reason;

    /**
     * Status do alerta no processo de revisão.
     * OPEN = aguardando revisão (padrão)
     * REVIEWED = revisado e confirmado como fraude
     * FALSE_POSITIVE = revisado e considerado legítimo
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    /** Momento em que o alerta foi criado */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
