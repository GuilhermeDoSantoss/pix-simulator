package com.pix_simulator.Pix.Simulator.tests;


import com.pix_simulator.Pix.Simulator.anomaly.AlertStatus;
import com.pix_simulator.Pix.Simulator.anomaly.AnomalyAlert;
import com.pix_simulator.Pix.Simulator.anomaly.AnomalyAlertRepository;
import com.pix_simulator.Pix.Simulator.anomaly.AnomalyDetectorService;
import com.pix_simulator.Pix.Simulator.entity.Transaction;
import com.pix_simulator.Pix.Simulator.entity.TransactionRepository;
import com.pix_simulator.Pix.Simulator.entity.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do AnomalyDetectorService.
 *
 * Testa o algoritmo de detecção de anomalia (Z-Score) com diferentes cenários:
 * - Valor claramente anômalo (muito acima da média)
 * - Valor dentro do padrão histórico
 * - Histórico insuficiente para análise
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnomalyDetectorService - Testes unitários")
class AnomalyDetectorServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AnomalyAlertRepository anomalyAlertRepository;

    @InjectMocks
    private AnomalyDetectorService anomalyDetectorService;

    @BeforeEach
    void setUp() {
        // Injeta valores que viriam do application.yml
        ReflectionTestUtils.setField(anomalyDetectorService, "thresholdMultiplier", 3.0);
        ReflectionTestUtils.setField(anomalyDetectorService, "minHistoryCount", 5);
    }

    /**
     * Helper: cria uma lista de transações com valores similares para simular histórico normal.
     */
    private List<Transaction> buildHistory(Long senderId, double... amounts) {
        return IntStream.range(0, amounts.length)
                .mapToObj(i -> Transaction.builder()
                        .id((long) (i + 1000)) // ID diferente da transação atual
                        .senderId(senderId)
                        .amount(BigDecimal.valueOf(amounts[i]))
                        .status(TransactionStatus.COMPLETED)
                        .createdAt(LocalDateTime.now().minusDays(i + 1))
                        .build())
                .toList();
    }

    @Test
    @DisplayName("Anomalia detectada: valor muito acima da média histórica deve gerar alerta")
    void analyzeTransaction_highValue_shouldCreateAlert() {
        // ARRANGE
        // Histórico: usuário faz PIX de ~R$50 regularmente
        List<Transaction> history = buildHistory(1L, 50.0, 45.0, 55.0, 48.0, 52.0, 47.0, 53.0);
        // Transação atual: R$5000 (muito acima da média de ~R$50)
        Long transactionId = 999L;
        BigDecimal anomalousAmount = new BigDecimal("5000.00");

        when(transactionRepository.findCompletedTransactionsSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(history);
        when(anomalyAlertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // ACT
        anomalyDetectorService.analyzeTransaction(transactionId, 1L, anomalousAmount);

        // ASSERT - deve ter criado um alerta
        ArgumentCaptor<AnomalyAlert> alertCaptor = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(anomalyAlertRepository, times(1)).save(alertCaptor.capture());

        AnomalyAlert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getAccountId()).isEqualTo(1L);
        assertThat(savedAlert.getTransactionId()).isEqualTo(transactionId);
        assertThat(savedAlert.getTransactionAmount()).isEqualByComparingTo("5000.00");
        assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.OPEN);
        // Z-Score deve ser muito alto (>3) pois 5000 está bem distante da média ~50
        assertThat(savedAlert.getDeviationScore()).isGreaterThan(BigDecimal.valueOf(3));
        // Motivo deve conter informações úteis
        assertThat(savedAlert.getAlertReason()).contains("5000");
    }

    @Test
    @DisplayName("Valor normal: PIX dentro do padrão não deve gerar alerta")
    void analyzeTransaction_normalValue_shouldNotCreateAlert() {
        // ARRANGE - histórico de ~R$50, novo PIX de R$60 (dentro do padrão)
        List<Transaction> history = buildHistory(1L, 50.0, 45.0, 55.0, 48.0, 52.0, 47.0);
        BigDecimal normalAmount = new BigDecimal("60.00");

        when(transactionRepository.findCompletedTransactionsSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(history);

        // ACT
        anomalyDetectorService.analyzeTransaction(100L, 1L, normalAmount);

        // ASSERT - NÃO deve criar alerta
        verify(anomalyAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Histórico insuficiente: deve ignorar análise sem criar alerta")
    void analyzeTransaction_insufficientHistory_shouldSkipAnalysis() {
        // ARRANGE - apenas 3 transações no histórico (mínimo é 5)
        List<Transaction> thinHistory = buildHistory(1L, 50.0, 60.0, 45.0);

        when(transactionRepository.findCompletedTransactionsSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(thinHistory);

        // ACT - mesmo um valor muito alto não gera alerta sem histórico suficiente
        anomalyDetectorService.analyzeTransaction(100L, 1L, new BigDecimal("9999.00"));

        // ASSERT
        verify(anomalyAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Conta nova sem histórico: deve ignorar análise")
    void analyzeTransaction_noHistory_shouldSkipAnalysis() {
        // ARRANGE - sem histórico nenhum
        when(transactionRepository.findCompletedTransactionsSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // ACT
        anomalyDetectorService.analyzeTransaction(1L, 1L, new BigDecimal("99999.00"));

        // ASSERT - sem histórico, sem alerta
        verify(anomalyAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valores idênticos: desvio padrão zero não deve gerar alerta nem erro")
    void analyzeTransaction_allSameValues_shouldNotCrash() {
        // ARRANGE - todos os PIX têm exatamente R$100
        // Desvio padrão = 0 → não é possível calcular Z-Score → não gera alerta
        List<Transaction> history = buildHistory(1L, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0);

        when(transactionRepository.findCompletedTransactionsSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(history);

        // ACT - mesmo um valor diferente não deve lançar exceção (divisão por zero tratada)
        anomalyDetectorService.analyzeTransaction(100L, 1L, new BigDecimal("5000.00"));

        // ASSERT - sem divisão por zero, sem alerta
        verify(anomalyAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valor limítrofe: exatamente no threshold não deve gerar alerta")
    void analyzeTransaction_valueAtExactThreshold_shouldNotAlert() {
        // ARRANGE
        // Média = 50, desvio = ~3.74 (baseado nos valores abaixo)
        // Threshold = 50 + 3 * 3.74 = ~61.22
        // Valor de teste: R$61 (abaixo do threshold)
        List<Transaction> history = buildHistory(1L, 50.0, 45.0, 55.0, 48.0, 52.0);

        when(transactionRepository.findCompletedTransactionsSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(history);

        // ACT
        anomalyDetectorService.analyzeTransaction(100L, 1L, new BigDecimal("61.00"));

        // ASSERT - R$61 está abaixo do threshold, não deve gerar alerta
        verify(anomalyAlertRepository, never()).save(any());
    }
}
