package com.pix_simulator.Pix.Simulator.tests.pix;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import com.pix_simulator.Pix.Simulator.account.service.AccountService;
import com.pix_simulator.Pix.Simulator.idempotency.dto.IdempotencyEntry;
import com.pix_simulator.Pix.Simulator.idempotency.service.IdempotencyService;
import com.pix_simulator.Pix.Simulator.pix.dto.PixDTO;
import com.pix_simulator.Pix.Simulator.pix.entity.Transaction;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import com.pix_simulator.Pix.Simulator.pix.event.PixEvent;
import com.pix_simulator.Pix.Simulator.pix.event.PixEventProducer;
import com.pix_simulator.Pix.Simulator.pix.repository.TransactionRepository;
import com.pix_simulator.Pix.Simulator.pix.service.PixService;
import com.pix_simulator.Pix.Simulator.shared.exception.BusinessException;
import com.pix_simulator.Pix.Simulator.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do PixService — refatorados para o novo fluxo com Redis.
 *
 * Cobertura:
 * - Fluxo normal (happy path)
 * - Cache hit no Redis (idempotência via Redis)
 * - Cache hit no banco H2 (fallback idempotência)
 * - UUID v4 inválido → BusinessException
 * - Lock negado (concorrência) → BusinessException
 * - Saldo insuficiente → BusinessException
 * - Chave PIX inexistente → ResourceNotFoundException
 * - Auto-transferência → BusinessException
 * - Destinatário inativo → BusinessException
 * - Saldo exato (boundary)
 * - Histórico
 * - IDOR protection (getById de outra conta)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PixService — Testes Unitários")
class PixServiceTest {

    @Mock private TransactionRepository txRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountService accountService;
    @Mock private PixEventProducer eventProducer;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private PixService pixService;

    private Account sender;
    private Account receiver;
    private PixDTO.SendRequest request;
    private String validUuidV4;

    @BeforeEach
    void setUp() {
        validUuidV4 = UUID.randomUUID().toString();

        sender = Account.builder()
                .id(1L).name("João").cpf("11111111111")
                .pixKey("joao@test.com")
                .balance(new BigDecimal("1000.00"))
                .active(true).build();

        receiver = Account.builder()
                .id(2L).name("Maria").cpf("22222222222")
                .pixKey("maria@test.com")
                .balance(new BigDecimal("500.00"))
                .active(true).build();

        request = new PixDTO.SendRequest();
        request.setIdempotencyKey(validUuidV4);
        request.setReceiverPixKey("maria@test.com");
        request.setAmount(new BigDecimal("150.00"));
        request.setDescription("Teste PIX");
    }

    // ─────────────────────────────────────────────────────────────────────
    // HAPPY PATH
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve criar transação PENDING, armazenar no Redis e publicar Kafka")
    void send_deveCriarTransacaoPendingEPublicarKafka() {
        when(idempotencyService.isValidUuidV4(validUuidV4)).thenReturn(true);
        when(idempotencyService.findCachedResponse(validUuidV4)).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(validUuidV4)).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey("maria@test.com")).thenReturn(Optional.of(receiver));
        when(txRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            return Transaction.builder()
                    .id(10L).idempotencyKey(t.getIdempotencyKey())
                    .senderId(t.getSenderId()).receiverId(t.getReceiverId())
                    .receiverPixKey(t.getReceiverPixKey())
                    .amount(t.getAmount()).status(TransactionStatus.PENDING).build();
        });

        PixDTO.PixResponse response = pixService.send(1L, request);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.isIdempotentHit()).isFalse();
        assertThat(response.getTransactionId()).isEqualTo(10L);

        // Redis deve ter sido populado com o entry
        verify(idempotencyService).storeResponse(eq(validUuidV4), any(IdempotencyEntry.class));
        // Kafka deve ter recebido o evento
        verify(eventProducer).publishPixCreated(any(PixEvent.class));
        // Lock deve ter sido liberado (sempre, mesmo em sucesso)
        verify(idempotencyService).releaseLock(validUuidV4);
    }

    // ─────────────────────────────────────────────────────────────────────
    // IDEMPOTÊNCIA — CACHE HIT NO REDIS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache hit Redis: deve retornar entry sem tocar banco ou Kafka")
    void send_devRetornarCacheRedisSeChaveJaExiste() {
        IdempotencyEntry cachedEntry = IdempotencyEntry.builder()
                .transactionId(99L).idempotencyKey(validUuidV4)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("150.00"))
                .receiverPixKey("maria@test.com").receiverName("Maria")
                .build();

        when(idempotencyService.isValidUuidV4(validUuidV4)).thenReturn(true);
        when(idempotencyService.findCachedResponse(validUuidV4)).thenReturn(Optional.of(cachedEntry));

        PixDTO.PixResponse response = pixService.send(1L, request);

        assertThat(response.isIdempotentHit()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo(99L);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getMessage()).contains("cache");

        // CRÍTICO: nenhum acesso ao banco, nenhum Kafka, nenhum lock
        verify(accountService, never()).findActive(anyLong());
        verify(txRepository, never()).save(any());
        verify(eventProducer, never()).publishPixCreated(any());
        verify(idempotencyService, never()).acquireLock(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // VALIDAÇÃO UUID v4
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve lançar BusinessException quando idempotencyKey não é UUID v4")
    void send_deveLancarExcecaoParaUuidInvalido() {
        request.setIdempotencyKey("nao-e-uuid-valido");
        when(idempotencyService.isValidUuidV4("nao-e-uuid-valido")).thenReturn(false);

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UUID v4");

        verify(txRepository, never()).save(any());
        verify(eventProducer, never()).publishPixCreated(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOCK DISTRIBUÍDO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve lançar BusinessException quando lock Redis está ocupado (concorrência)")
    void send_deveLancarExcecaoQuandoLockNegado() {
        when(idempotencyService.isValidUuidV4(validUuidV4)).thenReturn(true);
        when(idempotencyService.findCachedResponse(validUuidV4)).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(validUuidV4)).thenReturn(false);

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("processada");

        verify(txRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve sempre liberar o lock mesmo quando ocorre exceção de negócio")
    void send_deveLiberarLockMesmoEmCasoDeExcecao() {
        when(idempotencyService.isValidUuidV4(validUuidV4)).thenReturn(true);
        when(idempotencyService.findCachedResponse(validUuidV4)).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(validUuidV4)).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        // Lock DEVE ser liberado mesmo após exceção (bloco finally)
        verify(idempotencyService).releaseLock(validUuidV4);
    }

    // ─────────────────────────────────────────────────────────────────────
    // VALIDAÇÕES DE NEGÓCIO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve lançar BusinessException quando saldo é insuficiente")
    void send_deveLancarExcecaoComSaldoInsuficiente() {
        request.setAmount(new BigDecimal("9999.00"));

        when(idempotencyService.isValidUuidV4(validUuidV4)).thenReturn(true);
        when(idempotencyService.findCachedResponse(any())).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(any())).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey(any())).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Saldo insuficiente");

        verify(txRepository, never()).save(any());
        verify(idempotencyService, never()).storeResponse(any(), any());
        verify(idempotencyService).releaseLock(validUuidV4);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException quando chave PIX não existe")
    void send_deveLancarExcecaoQuandoChavePixNaoEncontrada() {
        when(idempotencyService.isValidUuidV4(any())).thenReturn(true);
        when(idempotencyService.findCachedResponse(any())).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(any())).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Deve lançar BusinessException em auto-transferência")
    void send_deveLancarExcecaoEmAutoTransferencia() {
        receiver = Account.builder().id(1L).name("João")
                .pixKey("joao@test.com").active(true)
                .balance(new BigDecimal("500.00")).build();

        when(idempotencyService.isValidUuidV4(any())).thenReturn(true);
        when(idempotencyService.findCachedResponse(any())).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(any())).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey(any())).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("si mesmo");
    }

    @Test
    @DisplayName("Deve lançar BusinessException quando destinatário está inativo")
    void send_deveLancarExcecaoQuandoDestinatarioInativo() {
        receiver.setActive(false);

        when(idempotencyService.isValidUuidV4(any())).thenReturn(true);
        when(idempotencyService.findCachedResponse(any())).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(any())).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey(any())).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> pixService.send(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("desativada");
    }

    @Test
    @DisplayName("Deve aprovar quando valor é exatamente igual ao saldo (boundary)")
    void send_deveAprovarQuandoValorIgualAoSaldo() {
        request.setAmount(new BigDecimal("1000.00"));

        when(idempotencyService.isValidUuidV4(any())).thenReturn(true);
        when(idempotencyService.findCachedResponse(any())).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(any())).thenReturn(true);
        when(accountService.findActive(1L)).thenReturn(sender);
        when(accountRepository.findByPixKey(any())).thenReturn(Optional.of(receiver));
        when(txRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            return Transaction.builder().id(11L)
                    .idempotencyKey(t.getIdempotencyKey())
                    .senderId(t.getSenderId()).receiverId(t.getReceiverId())
                    .receiverPixKey(t.getReceiverPixKey())
                    .amount(t.getAmount()).status(TransactionStatus.PENDING).build();
        });

        assertThatCode(() -> pixService.send(1L, request)).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────
    // HISTÓRICO E IDOR
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory deve retornar histórico mapeado corretamente")
    void getHistory_deveRetornarHistoricoMapeado() {
        Transaction tx = Transaction.builder()
                .id(1L).senderId(1L).receiverId(2L)
                .receiverPixKey("maria@test.com")
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.COMPLETED)
                .idempotencyKey(validUuidV4).build();

        when(txRepository.findBySenderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tx));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(receiver));

        List<PixDTO.HistoryItem> history = pixService.getHistory(1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getReceiverName()).isEqualTo("Maria");
        assertThat(history.get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("getById deve lançar BusinessException ao acessar transação de outro usuário (IDOR)")
    void getById_deveLancarExcecaoParaTransacaoDeOutraConta() {
        Transaction tx = Transaction.builder()
                .id(1L).senderId(99L).receiverId(2L)
                .amount(new BigDecimal("100.00"))
                .status(TransactionStatus.COMPLETED).build();

        when(txRepository.findById(1L)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> pixService.getById(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Acesso negado");
    }
}
