package com.pix_simulator.Pix.Simulator.anomaly;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade que registra alertas de anomalia detectados.
 *
 * Quando o Anomaly Detector identifica que um PIX foge do padrão histórico,
 * ele cria um registro aqui e publica um alerta no Kafka.
 *
 * Esse log serve para:
 * - Auditoria: histórico de todos os alertas gerados
 * - Dashboard: visualização de transações suspeitas
 * - Integração futura: notificações, bloqueio automático, análise manual
 */
@Entity
@Table(
        name = "anomaly_alerts",
        indexes = {
                @Index(name = "idx_anomaly_account_id", columnList = "accountId"),
                @Index(name = "idx_anomaly_transaction_id", columnList = "transactionId")
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

    /**
     * ID da conta que fez o PIX anômalo.
     */
    @Column(nullable = false)
    private Long accountId;

    /**
     * ID da transação que gerou o alerta - permite rastrear o PIX suspeito.
     */
    @Column(nullable = false)
    private Long transactionId;

    /**
     * Valor do PIX que foi considerado anômalo.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal transactionAmount;

    /**
     * Média histórica dos PIX dessa conta - linha de base para comparação.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal historicalAverage;

    /**
     * Desvio padrão histórico - mede a variação normal dos valores.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal standardDeviation;

    /**
     * Quantas vezes o desvio padrão o valor atual está acima da média.
     * Ex: 5.2 significa "5.2 desvios padrão acima da média" = muito anômalo.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deviationScore;

    /**
     * Número de transações históricas usadas no cálculo.
     */
    @Column(nullable = false)
    private Long historicalCount;

    /**
     * Descrição legível do motivo do alerta.
     * Ex: "Valor R$ 5.000,00 está 8.3x acima da média histórica de R$ 150,00"
     */
    @Column(nullable = false, length = 500)
    private String alertReason;

    /**
     * Status do alerta: OPEN (aberto), REVIEWED (revisado), FALSE_POSITIVE (falso positivo).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
