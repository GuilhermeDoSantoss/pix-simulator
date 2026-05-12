package com.pix_simulator.Pix.Simulator.anomaly.service;

import com.pix_simulator.Pix.Simulator.anomaly.dto.AnomalyAlertDTO;
import com.pix_simulator.Pix.Simulator.anomaly.entity.AnomalyAlert;
import com.pix_simulator.Pix.Simulator.anomaly.repository.AnomalyAlertRepository;
import com.pix_simulator.Pix.Simulator.pix.repository.TransactionRepository;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de detecção de anomalias em transações PIX.
 *
 * Responsabilidade: analisar se o valor de uma transação é suspeito
 * comparado ao histórico do remetente e gerar alertas quando necessário.
 *
 * ALGORITMO (razão histórica simples):
 *   1. Busca a média dos valores de transações COMPLETED dos últimos 30 dias
 *   2. Calcula razão = valor_atual / média_histórica
 *   3. Se razão > threshold (padrão: 10×), a transação é marcada FLAGGED
 *
 * EXEMPLO:
 *   Média histórica do usuário: R$ 50,00
 *   Transação atual:            R$ 5.000,00
 *   Razão: 100 → muito acima do threshold de 10 → FLAGGED
 *
 * GUARDS:
 *   - Sem histórico suficiente (< minHistoryCount): transação aprovada
 *   - Média histórica = zero: transação aprovada (evita divisão por zero)
 *
 * CORREÇÕES aplicadas neste refactoring:
 *   1. Removido import morto de TransactionStatus (agora usado como parâmetro)
 *   2. getAlertsForAccount() agora retorna DTO em vez de entidade JPA
 *   3. Assinatura de countCompletedSince/avgAmountBySenderSince atualizada
 *      para usar enum tipado (corrigido no repositório)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectorService {

    private final TransactionRepository txRepo;
    private final AnomalyAlertRepository alertRepo;

    @Value("${app.anomaly.threshold-multiplier}")
    private double thresholdMultiplier;

    @Value("${app.anomaly.min-history-count}")
    private int minHistoryCount;

    /** Janela de histórico em dias para cálculo da média */
    private static final int HISTORY_DAYS = 30;

    /**
     * Verifica se o valor da transação é anômalo para este remetente.
     *
     * Executado dentro da transação do PixEventConsumer (@Transactional
     * herdado do caller). Se anomalia detectada, salva AnomalyAlert
     * e retorna true — sinalizando ao consumer que deve marcar FLAGGED.
     *
     * param transactionId ID da transação (para vincular ao alerta)
     * param senderId      ID do remetente (para buscar histórico)
     * param amount        Valor da transação atual
     * return true se anomalia detectada, false se transação normal
     */
    @Transactional
    public boolean check(Long transactionId, Long senderId, BigDecimal amount) {
        LocalDateTime since = LocalDateTime.now().minusDays(HISTORY_DAYS);

        // Verifica se há histórico suficiente para estabelecer uma linha de base
        long historyCount = txRepo.countByStatusSince(senderId, since, TransactionStatus.COMPLETED);

        if (historyCount < minHistoryCount) {
            log.info("Verificação de anomalia ignorada: histórico insuficiente ({}/{}) para sender={}",
                    historyCount, minHistoryCount, senderId);
            return false;
        }

        BigDecimal avg = txRepo.avgAmountBySenderSince(senderId, since, TransactionStatus.COMPLETED)
                .orElse(BigDecimal.ZERO);

        // Guard: evita divisão por zero
        if (avg.compareTo(BigDecimal.ZERO) == 0) {
            log.info("Verificação de anomalia ignorada: média histórica é zero para sender={}", senderId);
            return false;
        }

        // Calcula razão = valor_atual / média_histórica
        BigDecimal ratio = amount.divide(avg, 4, RoundingMode.HALF_UP);
        BigDecimal threshold = BigDecimal.valueOf(thresholdMultiplier);

        boolean isAnomalous = ratio.compareTo(threshold) > 0;

        if (isAnomalous) {
            String reason = String.format(
                    "Transação de R$%.2f é %.1f× a média de 30 dias de R$%.2f (threshold: %.1f×)",
                    amount, ratio.doubleValue(), avg, thresholdMultiplier);

            alertRepo.save(AnomalyAlert.builder()
                    .accountId(senderId)
                    .transactionId(transactionId)
                    .transactionAmount(amount)
                    .historicalAverage(avg.setScale(2, RoundingMode.HALF_UP))
                    .ratio(ratio.setScale(2, RoundingMode.HALF_UP))
                    .reason(reason)
                    .build());

            log.warn("ANOMALIA DETECTADA transactionId={} senderId={} razão={} motivo={}",
                    transactionId, senderId, ratio, reason);
        } else {
            log.info("Verificação de anomalia aprovada transactionId={} senderId={} razão={}",
                    transactionId, senderId, ratio.setScale(2, RoundingMode.HALF_UP));
        }

        return isAnomalous;
    }

    /**
     * Retorna todos os alertas de anomalia de uma conta como DTOs.
     *
     * CORREÇÃO: retornava List<AnomalyAlert> (entidade JPA) diretamente,
     * expondo detalhes internos do Hibernate e arriscando
     * LazyInitializationException. Agora mapeia para AnomalyAlertDTO.
     *
     * @param accountId ID da conta autenticada
     * @return lista de alertas como DTOs, mais recente primeiro
     */
    @Transactional(readOnly = true)
    public List<AnomalyAlertDTO> getAlertsForAccount(Long accountId) {
        return alertRepo.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Converte entidade JPA para DTO de resposta */
    private AnomalyAlertDTO toDto(AnomalyAlert alert) {
        return AnomalyAlertDTO.builder()
                .id(alert.getId())
                .accountId(alert.getAccountId())
                .transactionId(alert.getTransactionId())
                .transactionAmount(alert.getTransactionAmount())
                .historicalAverage(alert.getHistoricalAverage())
                .ratio(alert.getRatio())
                .reason(alert.getReason())
                .status(alert.getStatus())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
