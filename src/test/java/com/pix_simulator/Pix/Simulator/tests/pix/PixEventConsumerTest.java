package com.pix_simulator.Pix.Simulator.tests.pix;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import com.pix_simulator.Pix.Simulator.anomaly.service.AnomalyDetectorService;
import com.pix_simulator.Pix.Simulator.anomaly.consumer.PixEventConsumer;
import com.pix_simulator.Pix.Simulator.pix.entity.Transaction;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import com.pix_simulator.Pix.Simulator.pix.event.PixEvent;
import com.pix_simulator.Pix.Simulator.pix.event.PixEventProducer;
import com.pix_simulator.Pix.Simulator.pix.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do PixEventConsumer (Kafka consumer).
 *
 * Estratégia: o metodo onPixCreated é apenas um método Java com
 * @KafkaListener — pode ser chamado diretamente nos testes sem
 * precisar de um broker Kafka real.
 *
 * Cobertura:
 * - Fluxo normal → débito, crédito, status COMPLETED
 * - Transação anômala → status FLAGGED, saldo intocado
 * - Reentrega do Kafka (já processada) → ignorada silenciosamente
 * - Saldo insuficiente no momento do processamento → FAILED
 * - Transação não encontrada no banco → exceção capturada
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PixEventConsumer — Testes Unitários")
class PixEventConsumerTest {

    @Mock private TransactionRepository txRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AnomalyDetectorService anomalyService;
    @Mock private PixEventProducer eventProducer;

    @InjectMocks private PixEventConsumer consumer;

    private Transaction pendingTransaction;
    private Account sender;
    private Account receiver;
    private PixEvent event;

    @BeforeEach
    void setUp() {
        pendingTransaction = Transaction.builder()
                .id(1L)
                .senderId(10L)
                .receiverId(20L)
                .receiverPixKey("maria@test.com")
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.PENDING)
                .idempotencyKey("key-1")
                .build();

        sender = Account.builder()
                .id(10L)
                .balance(new BigDecimal("500.00"))
                .active(true)
                .build();

        receiver = Account.builder()
                .id(20L)
                .balance(new BigDecimal("200.00"))
                .active(true)
                .build();

        event = PixEvent.builder()
                .transactionId(1L)
                .senderId(10L)
                .receiverId(20L)
                .amount(new BigDecimal("100.00"))
                .receiverPixKey("maria@test.com")
                .eventType("PIX_CREATED")
                .build();
    }

    @Test
    @DisplayName("Deve debitar remetente, creditar destinatário e marcar COMPLETED")
    void onPixCreated_deveCompletarTransacaoNormal() {
        when(txRepository.findById(1L)).thenReturn(Optional.of(pendingTransaction));
        when(anomalyService.check(anyLong(), anyLong(), any())).thenReturn(false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(receiver));
        when(txRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPixCreated(event, 0, 0L);

        // Remetente: 500 - 100 = 400
        assertThat(sender.getBalance()).isEqualByComparingTo("400.00");
        // Destinatário: 200 + 100 = 300
        assertThat(receiver.getBalance()).isEqualByComparingTo("300.00");
        // Status final correto
        assertThat(pendingTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(pendingTransaction.getProcessedAt()).isNotNull();

        verify(accountRepository, times(2)).save(any(Account.class));
        verify(eventProducer).publishPixCompleted(any(PixEvent.class));
    }

    @Test
    @DisplayName("Deve marcar FLAGGED e NÃO tocar saldos quando transação é anômala")
    void onPixCreated_deveFlagarTransacaoAnomalosa() {
        when(txRepository.findById(1L)).thenReturn(Optional.of(pendingTransaction));
        when(anomalyService.check(anyLong(), anyLong(), any())).thenReturn(true); // anomalia!
        when(txRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPixCreated(event, 0, 0L);

        assertThat(pendingTransaction.getStatus()).isEqualTo(TransactionStatus.FLAGGED);
        assertThat(pendingTransaction.getProcessedAt()).isNotNull();
        assertThat(pendingTransaction.getStatusMessage()).isNotBlank();

        // Saldos não devem ser tocados em transação flagrada
        verify(accountRepository, never()).findById(anyLong());
        verify(accountRepository, never()).save(any(Account.class));
        verify(eventProducer, never()).publishPixCompleted(any());
    }

    @Test
    @DisplayName("Deve ignorar transação já processada (proteção contra reentrega Kafka)")
    void onPixCreated_deveIgnorarTransacaoJaProcessada() {
        // Simula reentrega do Kafka: transação já está COMPLETED
        pendingTransaction.setStatus(TransactionStatus.COMPLETED);
        when(txRepository.findById(1L)).thenReturn(Optional.of(pendingTransaction));

        consumer.onPixCreated(event, 0, 0L);

        // Nada deve acontecer — consumer deve sair silenciosamente
        verify(anomalyService, never()).check(anyLong(), anyLong(), any());
        verify(accountRepository, never()).save(any(Account.class));
        verify(eventProducer, never()).publishPixCompleted(any());
    }

    @Test
    @DisplayName("Deve marcar FAILED quando saldo foi consumido entre PENDING e processamento")
    void onPixCreated_deveFalharQuandoSaldoEsgotado() {
        // Simula saldo reduzido após o request HTTP (race condition)
        sender.setBalance(new BigDecimal("50.00")); // menos que os 100.00 do PIX

        when(txRepository.findById(1L)).thenReturn(Optional.of(pendingTransaction));
        when(anomalyService.check(anyLong(), anyLong(), any())).thenReturn(false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(receiver));
        when(txRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPixCreated(event, 0, 0L);

        assertThat(pendingTransaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(pendingTransaction.getStatusMessage()).contains("Saldo insuficiente");

        // Contas salvas nunca — débito não deve ocorrer
        verify(accountRepository, never()).save(any(Account.class));
        verify(eventProducer, never()).publishPixCompleted(any());
    }

    @Test
    @DisplayName("Deve capturar exceção sem propagar quando transação não é encontrada")
    void onPixCreated_deveCaptutarExcecaoQuandoTransacaoNaoEncontrada() {
        when(txRepository.findById(1L)).thenReturn(Optional.empty());

        // Não deve lançar exceção para fora do consumer (evita retry loop do Kafka)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> consumer.onPixCreated(event, 0, 0L));

        verify(anomalyService, never()).check(anyLong(), anyLong(), any());
    }
}

