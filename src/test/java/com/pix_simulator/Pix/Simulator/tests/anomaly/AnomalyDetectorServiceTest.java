package com.pix_simulator.Pix.Simulator.tests.anomaly;

import com.pix_simulator.Pix.Simulator.anomaly.dto.AnomalyAlertDTO;
import com.pix_simulator.Pix.Simulator.anomaly.entity.AlertStatus;
import com.pix_simulator.Pix.Simulator.anomaly.entity.AnomalyAlert;
import com.pix_simulator.Pix.Simulator.anomaly.repository.AnomalyAlertRepository;
import com.pix_simulator.Pix.Simulator.anomaly.service.AnomalyDetectorService;
import com.pix_simulator.Pix.Simulator.pix.repository.TransactionRepository;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do AnomalyDetectorService.
 *
 * Cobertura:
 * - Transação normal → sem alerta
 * - Transação anômala → alerta salvo, retorna true
 * - Histórico insuficiente → sem alerta (não há baseline)
 * - Média histórica zero → sem alerta (evita divisão por zero)
 * - Exatamente no threshold → anômalo (> não >=)
 * - getAlertsForAccount → retorna DTOs mapeados corretamente
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnomalyDetectorService — Testes Unitários")
class AnomalyDetectorServiceTest {

    @Mock private TransactionRepository txRepository;
    @Mock private AnomalyAlertRepository alertRepository;

    @InjectMocks private AnomalyDetectorService service;

    @BeforeEach
    void setUp() {
        // Injeta campos @Value que não são populados sem o Spring context
        ReflectionTestUtils.setField(service, "thresholdMultiplier", 10.0);
        ReflectionTestUtils.setField(service, "minHistoryCount", 3);
    }

    @Test
    @DisplayName("Deve retornar false e NÃO salvar alerta para transação normal")
    void check_deveRetornarFalseParaTransacaoNormal() {
        when(txRepository.countByStatusSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(5L);
        when(txRepository.avgAmountBySenderSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(Optional.of(new BigDecimal("100.00")));

        // 150 / 100 = 1.5× — bem abaixo do threshold de 10×
        boolean anomalous = service.check(1L, 1L, new BigDecimal("150.00"));

        assertThat(anomalous).isFalse();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve retornar true e salvar alerta quando valor é muito acima da média")
    void check_deveSalvarAlertaParaTransacaoAnomalosa() {
        when(txRepository.countByStatusSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(5L);
        when(txRepository.avgAmountBySenderSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(Optional.of(new BigDecimal("50.00")));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 5000 / 50 = 100× — muito acima do threshold de 10×
        boolean anomalous = service.check(10L, 1L, new BigDecimal("5000.00"));

        assertThat(anomalous).isTrue();

        ArgumentCaptor<AnomalyAlert> captor = ArgumentCaptor.forClass(AnomalyAlert.class);
        verify(alertRepository).save(captor.capture());

        AnomalyAlert savedAlert = captor.getValue();
        assertThat(savedAlert.getTransactionId()).isEqualTo(10L);
        assertThat(savedAlert.getAccountId()).isEqualTo(1L);
        assertThat(savedAlert.getRatio()).isGreaterThan(BigDecimal.TEN);
        assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(savedAlert.getReason()).contains("R$5000");
    }

    @Test
    @DisplayName("Deve retornar false quando histórico é insuficiente")
    void check_deveRetornarFalseComHistoricoInsuficiente() {
        // Apenas 2 transações — abaixo do mínimo de 3
        when(txRepository.countByStatusSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(2L);

        // Mesmo um valor absurdo não gera alerta sem baseline
        boolean anomalous = service.check(1L, 1L, new BigDecimal("99999.00"));

        assertThat(anomalous).isFalse();
        verify(alertRepository, never()).save(any());
        // Não deve buscar a média se não há histórico suficiente
        verify(txRepository, never()).avgAmountBySenderSince(any(), any(), any());
    }

    @Test
    @DisplayName("Deve retornar false quando média histórica é zero (edge case)")
    void check_deveRetornarFalseComMediaZero() {
        when(txRepository.countByStatusSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(5L);
        when(txRepository.avgAmountBySenderSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        // Sem divisão por zero — deve retornar false graciosamente
        boolean anomalous = service.check(1L, 1L, new BigDecimal("500.00"));

        assertThat(anomalous).isFalse();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve detectar anomalia exatamente acima do threshold (1001/100 = 10.01×)")
    void check_deveDetectarAnomaliaNaFronteiraDoThreshold() {
        when(txRepository.countByStatusSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(5L);
        when(txRepository.avgAmountBySenderSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(Optional.of(new BigDecimal("100.00")));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 1001 / 100 = 10.01× — ligeiramente acima do threshold de 10×
        boolean anomalous = service.check(1L, 1L, new BigDecimal("1001.00"));

        assertThat(anomalous).isTrue();
    }

    @Test
    @DisplayName("NÃO deve detectar anomalia exatamente no threshold (1000/100 = 10.0×)")
    void check_naoDeveDetectarAnomaliaNoPropioThreshold() {
        when(txRepository.countByStatusSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(5L);
        when(txRepository.avgAmountBySenderSince(eq(1L), any(LocalDateTime.class), eq(TransactionStatus.COMPLETED)))
                .thenReturn(Optional.of(new BigDecimal("100.00")));

        // 1000 / 100 = 10.0× — exatamente no threshold, não anomalous (compareTo > 0, não >= 0)
        boolean anomalous = service.check(1L, 1L, new BigDecimal("1000.00"));

        assertThat(anomalous).isFalse();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAlertsForAccount deve retornar DTOs mapeados corretamente")
    void getAlertsForAccount_deveRetornarDtosMapeados() {
        AnomalyAlert alert = AnomalyAlert.builder()
                .id(1L)
                .accountId(1L)
                .transactionId(10L)
                .transactionAmount(new BigDecimal("5000.00"))
                .historicalAverage(new BigDecimal("50.00"))
                .ratio(new BigDecimal("100.00"))
                .reason("Teste de razão")
                .status(AlertStatus.OPEN)
                .build();

        when(alertRepository.findByAccountIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(alert));

        List<AnomalyAlertDTO> result = service.getAlertsForAccount(1L);

        assertThat(result).hasSize(1);
        AnomalyAlertDTO dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTransactionId()).isEqualTo(10L);
        assertThat(dto.getTransactionAmount()).isEqualByComparingTo("5000.00");
        assertThat(dto.getRatio()).isEqualByComparingTo("100.00");
        assertThat(dto.getStatus()).isEqualTo(AlertStatus.OPEN);
    }
}
