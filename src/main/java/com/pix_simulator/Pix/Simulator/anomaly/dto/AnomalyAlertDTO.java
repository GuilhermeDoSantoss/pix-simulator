package com.pix_simulator.Pix.Simulator.anomaly.dto;

import com.pix_simulator.Pix.Simulator.anomaly.entity.AlertStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para alertas de anomalia.
 *
 * Responsabilidade: transportar dados de AnomalyAlert da camada de serviço
 * para o controller sem expor a entidade JPA diretamente.
 *
 * Por que não retornar a entidade diretamente?
 * - Evita expor campos internos (como proxies Hibernate) via serialização JSON
 * - Permite evoluir a entidade sem quebrar o contrato da API
 * - Protege contra serialização recursiva e LazyInitializationException
 * - Segue o princípio de separação entre camadas (Controller x Repository)
 */
@Data
@Builder
public class AnomalyAlertDTO {

    private Long id;
    private Long accountId;
    private Long transactionId;
    private BigDecimal transactionAmount;
    private BigDecimal historicalAverage;

    /**
     * Razão entre o valor atual e a média histórica.
     * Exemplo: 50.00 significa que o valor é 50× a média histórica.
     */
    private BigDecimal ratio;

    private String reason;
    private AlertStatus status;
    private LocalDateTime createdAt;
}
