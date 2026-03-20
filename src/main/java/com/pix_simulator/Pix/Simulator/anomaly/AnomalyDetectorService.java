package com.pix_simulator.Pix.Simulator.anomaly;


import com.pix_simulator.Pix.Simulator.entity.Transaction;
import com.pix_simulator.Pix.Simulator.entity.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de detecção de anomalias em transações PIX.
 *
 * ALGORITMO:
 * Usa Z-Score (desvio padrão normalizado) para identificar transações atípicas.
 *
 * Fórmula:
 *   z = (valor_atual - média_histórica) / desvio_padrão
 *
 * Se z > threshold (padrão: 3), o valor é considerado anômalo.
 *
 * EXEMPLO PRÁTICO:
 * - Usuário faz PIX de R$50, R$60, R$45, R$55, R$40 nos últimos 30 dias
 * - Média: R$50 | Desvio padrão: ~7.5
 * - Hoje tenta fazer PIX de R$5.000
 * - z = (5000 - 50) / 7.5 = 659 (muito acima de 3!)
 * - → ALERTA GERADO: "PIX de R$5.000 está 659x acima da média"
 *
 * LIMITAÇÃO CONHECIDA:
 * Para contas novas (< min-history-count transações), não há histórico suficiente
 * para calcular desvio padrão confiável. Nesses casos, o PIX passa sem análise.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectorService {

    private final TransactionRepository transactionRepository;
    private final AnomalyAlertRepository anomalyAlertRepository;

    /**
     * Multiplicador do desvio padrão para definir o threshold.
     * Padrão: 3.0 (qualquer valor acima de média + 3*desvio = anômalo).
     * Valores mais altos = menos sensível, menos falsos positivos.
     */
    @Value("${app.anomaly.threshold-multiplier}")
    private double thresholdMultiplier;

    /**
     * Mínimo de transações históricas para análise confiável.
     * Abaixo disso, não há base suficiente para comparação.
     */
    @Value("${app.anomaly.min-history-count}")
    private int minHistoryCount;

    /**
     * Janela de histórico: últimos 30 dias.
     */
    private static final int HISTORY_DAYS = 30;

    /**
     * Analisa se uma transação é anômala comparando com o histórico da conta.
     *
     * Chamado pelo consumer Kafka quando recebe um evento "PIX_COMPLETED".
     *
     * @param transactionId ID da transação a analisar
     * @param senderId      ID da conta remetente
     * @param amount        Valor do PIX atual
     */
    @Transactional
    public void analyzeTransaction(Long transactionId, Long senderId, BigDecimal amount) {
        log.info("Analisando transação {} - conta: {}, valor: R$ {}", transactionId, senderId, amount);

        LocalDateTime since = LocalDateTime.now().minusDays(HISTORY_DAYS);

        // Busca histórico de transações da conta nos últimos 30 dias
        List<Transaction> history = transactionRepository
                .findCompletedTransactionsSince(senderId, since);

        // Exclui a transação atual do histórico (ela acabou de ser criada)
        List<BigDecimal> historicalAmounts = history.stream()
                .filter(t -> !t.getId().equals(transactionId))
                .map(Transaction::getAmount)
                .collect(Collectors.toList());

        long historyCount = historicalAmounts.size();

        // Sem histórico suficiente - não é possível calcular desvio padrão confiável
        if (historyCount < minHistoryCount) {
            log.info("Histórico insuficiente para análise - conta: {}, transações: {}/{}",
                    senderId, historyCount, minHistoryCount);
            return;
        }

        // Calcula a média dos valores históricos
        BigDecimal average = calculateAverage(historicalAmounts);

        // Calcula o desvio padrão dos valores históricos
        BigDecimal stdDev = calculateStandardDeviation(historicalAmounts, average);

        // Se o desvio padrão é zero (todos os valores são iguais), não há como calcular Z-score
        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            log.info("Desvio padrão zero para conta {} - todos os PIX têm o mesmo valor", senderId);
            return;
        }

        // Calcula o Z-Score: quantos desvios padrão o valor atual está acima da média
        // z = (valor_atual - média) / desvio_padrão
        BigDecimal zScore = amount.subtract(average)
                .divide(stdDev, 4, RoundingMode.HALF_UP);

        BigDecimal threshold = BigDecimal.valueOf(thresholdMultiplier);

        log.info("Análise conta {} - valor: R${} | média: R${} | desvio: R${} | z-score: {}",
                senderId, amount, average.setScale(2, RoundingMode.HALF_UP),
                stdDev.setScale(2, RoundingMode.HALF_UP), zScore.setScale(2, RoundingMode.HALF_UP));

        // Verifica se o Z-Score ultrapassa o threshold
        if (zScore.compareTo(threshold) > 0) {
            log.warn("ANOMALIA DETECTADA! Conta: {} | Z-Score: {} > threshold: {}",
                    senderId, zScore, threshold);
            createAlert(transactionId, senderId, amount, average, stdDev, zScore, historyCount);
        } else {
            log.info("Transação dentro do padrão - conta: {}, z-score: {}", senderId, zScore);
        }
    }

    /**
     * Cria e persiste um alerta de anomalia no banco.
     * O alerta fica com status OPEN para revisão manual.
     */
    private void createAlert(Long transactionId, Long accountId, BigDecimal amount,
                             BigDecimal average, BigDecimal stdDev, BigDecimal zScore, long historyCount) {

        String reason = String.format(
                "PIX de R$ %.2f está %.1f desvios padrão acima da média histórica de R$ %.2f " +
                        "(desvio padrão: R$ %.2f, baseado em %d transações nos últimos %d dias)",
                amount, zScore.doubleValue(), average, stdDev, historyCount, HISTORY_DAYS
        );

        AnomalyAlert alert = AnomalyAlert.builder()
                .accountId(accountId)
                .transactionId(transactionId)
                .transactionAmount(amount)
                .historicalAverage(average.setScale(2, RoundingMode.HALF_UP))
                .standardDeviation(stdDev.setScale(2, RoundingMode.HALF_UP))
                .deviationScore(zScore.setScale(2, RoundingMode.HALF_UP))
                .historicalCount(historyCount)
                .alertReason(reason)
                .status(AlertStatus.OPEN)
                .build();

        anomalyAlertRepository.save(alert);
        log.warn("Alerta de anomalia salvo - conta: {}, transação: {}, motivo: {}", accountId, transactionId, reason);
    }

    /**
     * Calcula a média aritmética de uma lista de valores.
     * Usa MathContext.DECIMAL128 para máxima precisão com BigDecimal.
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128);
    }

    /**
     * Calcula o desvio padrão amostral de uma lista de valores.
     *
     * Fórmula do desvio padrão amostral:
     * σ = √( Σ(xi - μ)² / (n-1) )
     *
     * Usamos n-1 (desvio amostral) em vez de n (desvio populacional)
     * pois trabalhamos com uma amostra do histórico, não com toda a população.
     */
    private BigDecimal calculateStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }

        // Calcula Σ(xi - μ)²
        BigDecimal sumOfSquaredDiffs = values.stream()
                .map(v -> v.subtract(mean).pow(2))   // (xi - μ)²
                .reduce(BigDecimal.ZERO, BigDecimal::add);   // soma

        // Divide por (n-1) para desvio amostral
        BigDecimal variance = sumOfSquaredDiffs
                .divide(BigDecimal.valueOf(values.size() - 1L), MathContext.DECIMAL128);

        // Raiz quadrada da variância = desvio padrão
        return variance.sqrt(MathContext.DECIMAL128);
    }

    /**
     * Retorna todos os alertas de anomalia de uma conta.
     */
    public List<AnomalyAlert> getAlertsByAccount(Long accountId) {
        return anomalyAlertRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }
}
