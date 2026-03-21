package com.pix_simulator.Pix.Simulator.tests;


import com.pix_simulator.Pix.Simulator.account.Account;
import com.pix_simulator.Pix.Simulator.account.AccountRepository;
import com.pix_simulator.Pix.Simulator.account.AccountService;
import com.pix_simulator.Pix.Simulator.entity.Transaction;
import com.pix_simulator.Pix.Simulator.entity.TransactionRepository;
import com.pix_simulator.Pix.Simulator.entity.TransactionStatus;
import com.pix_simulator.Pix.Simulator.exception.BusinessException;
import com.pix_simulator.Pix.Simulator.exception.ResourceNotFoundException;
import com.pix_simulator.Pix.Simulator.pix.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do PixService.
 *
 * - @ExtendWith(MockitoExtension.class): ativa o Mockito sem subir o Spring context.
 * Muito mais rápido que @SpringBootTest - ideal para testes unitários puros.
 *
 * Estratégia de teste:
 * - Cada dependência do PixService é mockada (@Mock)
 * - O PixService é instanciado com os mocks injetados (@InjectMocks)
 * - Testamos apenas o comportamento do PixService, não das dependências
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PixService - Testes unitários")
class PixServiceTest {

    // ===== MOCKS =====
    // Cada @Mock cria um objeto falso que podemos programar para responder como quisermos

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PixEventProducer pixEventProducer;

    /**
     * @InjectMocks cria a instância real do PixService e injeta os mocks acima.
     * É o equivalente a chamar: new PixService(transactionRepository, accountRepository, ...)
     */
    @InjectMocks
    private PixService pixService;

    // ===== OBJETOS DE TESTE =====
    private Account sender;
    private Account receiver;
    private PixDTO.PixRequest pixRequest;
    private String idempotencyKey;

    /**
     * @BeforeEach executa antes de cada @Test.
     * Centraliza a criação dos objetos comuns para evitar repetição.
     */
    @BeforeEach
    void setUp() {
        // Conta remetente com saldo suficiente
        sender = Account.builder()
                .id(1L)
                .name("João Silva")
                .cpf("12345678901")
                .pixKey("joao@email.com")
                .balance(new BigDecimal("1000.00"))
                .active(true)
                .build();

        // Conta destinatária
        receiver = Account.builder()
                .id(2L)
                .name("Maria Souza")
                .cpf("98765432100")
                .pixKey("maria@email.com")
                .balance(new BigDecimal("500.00"))
                .active(true)
                .build();

        // Chave de idempotência única para cada teste
        idempotencyKey = UUID.randomUUID().toString();

        // Requisição de PIX padrão
        pixRequest = new PixDTO.PixRequest();
        pixRequest.setIdempotencyKey(idempotencyKey);
        pixRequest.setReceiverPixKey("maria@email.com");
        pixRequest.setAmount(new BigDecimal("150.00"));
        pixRequest.setDescription("Teste de PIX");
    }

    // ============================================================
    // TESTES DO FLUXO FELIZ (PIX bem-sucedido)
    // ============================================================

    @Test
    @DisplayName("PIX bem-sucedido: deve debitar remetente e creditar destinatário")
    void sendPix_success_shouldDebitSenderAndCreditReceiver() {
        // ARRANGE - configura o comportamento dos mocks
        // Idempotência: não é duplicata
        when(idempotencyService.isDuplicate(1L, idempotencyKey)).thenReturn(false);

        // AccountService retorna o remetente válido
        when(accountService.findActiveAccountById(1L)).thenReturn(sender);

        // Repositório encontra o destinatário pela chave PIX
        when(accountRepository.findByPixKey("maria@email.com")).thenReturn(Optional.of(receiver));

        // Simula o save da transação retornando a própria transação com ID
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) t = Transaction.builder()
                    .id(10L)
                    .idempotencyKey(t.getIdempotencyKey())
                    .senderId(t.getSenderId())
                    .receiverId(t.getReceiverId())
                    .receiverPixKey(t.getReceiverPixKey())
                    .amount(t.getAmount())
                    .status(t.getStatus())
                    .description(t.getDescription())
                    .build();
            return t;
        });

        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        // ACT - executa o método sendo testado
        PixDTO.PixResponse response = pixService.sendPix(1L, pixRequest);

        // ASSERT - verifica os resultados
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getAmount()).isEqualByComparingTo("150.00");
        assertThat(response.isIdempotentResponse()).isFalse();

        // Verifica que o remetente foi debitado: 1000 - 150 = 850
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(2)).save(accountCaptor.capture());
        Account savedSender = accountCaptor.getAllValues().get(0);
        assertThat(savedSender.getBalance()).isEqualByComparingTo("850.00");

        // Verifica que o destinatário foi creditado: 500 + 150 = 650
        Account savedReceiver = accountCaptor.getAllValues().get(1);
        assertThat(savedReceiver.getBalance()).isEqualByComparingTo("650.00");

        // Verifica que o evento foi publicado no Kafka
        verify(pixEventProducer, times(1)).publishPixEvent(any(PixEvent.class));

        // Verifica que a chave de idempotência foi salva no Redis
        verify(idempotencyService, times(1)).saveKey(eq(1L), eq(idempotencyKey), anyLong());
    }

    // ============================================================
    // TESTES DE IDEMPOTÊNCIA
    // ============================================================

    @Test
    @DisplayName("Idempotência: requisição duplicada deve retornar resultado cacheado sem reprocessar")
    void sendPix_duplicate_shouldReturnCachedResult() {
        // ARRANGE
        // Simula que a chave já existe no Redis (requisição duplicada)
        when(idempotencyService.isDuplicate(1L, idempotencyKey)).thenReturn(true);

        // Redis retorna o ID da transação original
        when(idempotencyService.getTransactionId(1L, idempotencyKey)).thenReturn(10L);

        // Banco retorna a transação original
        Transaction existingTransaction = Transaction.builder()
                .id(10L)
                .idempotencyKey(idempotencyKey)
                .senderId(1L)
                .receiverId(2L)
                .receiverPixKey("maria@email.com")
                .amount(new BigDecimal("150.00"))
                .status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(existingTransaction));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(receiver));

        // ACT
        PixDTO.PixResponse response = pixService.sendPix(1L, pixRequest);

        // ASSERT
        assertThat(response.isIdempotentResponse()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        // CRUCIAL: verifica que NENHUMA operação bancária foi executada
        verify(accountService, never()).findActiveAccountById(anyLong());
        verify(accountRepository, never()).save(any(Account.class));
        verify(pixEventProducer, never()).publishPixEvent(any());
    }

    // ============================================================
    // TESTES DE VALIDAÇÃO DE NEGÓCIO
    // ============================================================

    @Test
    @DisplayName("Saldo insuficiente: deve lançar BusinessException")
    void sendPix_insufficientBalance_shouldThrowBusinessException() {
        // ARRANGE
        // Saldo do remetente: R$100, valor do PIX: R$150
        sender.setBalance(new BigDecimal("100.00"));

        when(idempotencyService.isDuplicate(anyLong(), anyString())).thenReturn(false);
        when(accountService.findActiveAccountById(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey("maria@email.com")).thenReturn(Optional.of(receiver));

        // ACT + ASSERT
        assertThatThrownBy(() -> pixService.sendPix(1L, pixRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Saldo insuficiente");

        // Garante que nada foi debitado
        verify(accountRepository, never()).save(any());
        verify(pixEventProducer, never()).publishPixEvent(any());
    }

    @Test
    @DisplayName("Chave PIX inexistente: deve lançar ResourceNotFoundException")
    void sendPix_receiverNotFound_shouldThrowResourceNotFoundException() {
        // ARRANGE
        when(idempotencyService.isDuplicate(anyLong(), anyString())).thenReturn(false);
        when(accountService.findActiveAccountById(1L)).thenReturn(sender);
        // Nenhuma conta com essa chave PIX
        when(accountRepository.findByPixKey("naoexiste@email.com")).thenReturn(Optional.empty());

        pixRequest.setReceiverPixKey("naoexiste@email.com");

        // ACT + ASSERT
        assertThatThrownBy(() -> pixService.sendPix(1L, pixRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("chave PIX");
    }

    @Test
    @DisplayName("PIX para si mesmo: deve lançar BusinessException")
    void sendPix_sameAccount_shouldThrowBusinessException() {
        // ARRANGE - destinatário tem a mesma chave PIX que o remetente
        receiver.setId(1L); // mesmo ID do sender

        when(idempotencyService.isDuplicate(anyLong(), anyString())).thenReturn(false);
        when(accountService.findActiveAccountById(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey("maria@email.com")).thenReturn(Optional.of(receiver));

        // ACT + ASSERT
        assertThatThrownBy(() -> pixService.sendPix(1L, pixRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("própria conta");
    }

    @Test
    @DisplayName("Destinatário inativo: deve lançar BusinessException")
    void sendPix_inactiveReceiver_shouldThrowBusinessException() {
        // ARRANGE
        receiver.setActive(false);

        when(idempotencyService.isDuplicate(anyLong(), anyString())).thenReturn(false);
        when(accountService.findActiveAccountById(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey("maria@email.com")).thenReturn(Optional.of(receiver));

        // ACT + ASSERT
        assertThatThrownBy(() -> pixService.sendPix(1L, pixRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativa");
    }

    @Test
    @DisplayName("Valor exato do saldo: PIX no limite deve ser processado com sucesso")
    void sendPix_exactBalance_shouldSucceed() {
        // ARRANGE - PIX com exatamente o valor disponível no saldo
        pixRequest.setAmount(new BigDecimal("1000.00")); // igual ao saldo

        when(idempotencyService.isDuplicate(anyLong(), anyString())).thenReturn(false);
        when(accountService.findActiveAccountById(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey("maria@email.com")).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any())).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            return Transaction.builder().id(99L).idempotencyKey(t.getIdempotencyKey())
                    .senderId(t.getSenderId()).receiverId(t.getReceiverId())
                    .receiverPixKey(t.getReceiverPixKey()).amount(t.getAmount())
                    .status(TransactionStatus.COMPLETED).build();
        });
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // ACT + ASSERT - não deve lançar exceção
        assertThatCode(() -> pixService.sendPix(1L, pixRequest))
                .doesNotThrowAnyException();
    }
}
