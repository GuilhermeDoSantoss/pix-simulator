package com.pix_simulator.Pix.Simulator.tests.account;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import com.pix_simulator.Pix.Simulator.account.dto.AccountDTO;
import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import com.pix_simulator.Pix.Simulator.account.service.AccountService;
import com.pix_simulator.Pix.Simulator.shared.exception.BusinessException;
import com.pix_simulator.Pix.Simulator.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do AccountService.
 *
 * Cobertura:
 * - Criação de conta com sucesso
 * - Criação falha com CPF duplicado
 * - Criação falha com chave PIX duplicada
 * - Busca de conta ativa
 * - Busca falha por conta inexistente
 * - Busca falha por conta inativa
 * - Soft delete (desativação)
 * - Depósito
 * - Atualização parcial (nome e/ou senha)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService — Testes Unitários")
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder   passwordEncoder;

    @InjectMocks private AccountService accountService;

    private Account activeAccount;
    private AccountDTO.CreateRequest createRequest;

    @BeforeEach
    void setUp() {
        activeAccount = Account.builder()
                .id(1L)
                .name("João Silva")
                .cpf("11111111111")
                .password("$2a$10$hashedPassword")
                .pixKey("joao@email.com")
                .balance(new BigDecimal("500.00"))
                .active(true)
                .build();

        createRequest = new AccountDTO.CreateRequest();
        createRequest.setName("João Silva");
        createRequest.setCpf("11111111111");
        createRequest.setPassword("senha123");
        createRequest.setPixKey("joao@email.com");
        createRequest.setInitialBalance(new BigDecimal("500.00"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // CRIAÇÃO DE CONTA
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve criar conta com sucesso e retornar DTO sem senha")
    void create_deveCriarContaComSucesso() {
        when(accountRepository.existsByCpf("11111111111")).thenReturn(false);
        when(accountRepository.existsByPixKey("joao@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("$2a$10$hashed");
        when(accountRepository.save(any())).thenReturn(activeAccount);

        AccountDTO.Response response = accountService.create(createRequest);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("João Silva");
        assertThat(response.getCpf()).isEqualTo("11111111111");
        assertThat(response.getBalance()).isEqualByComparingTo("500.00");
        assertThat(response.getActive()).isTrue();

        verify(passwordEncoder).encode("senha123");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Deve lançar BusinessException quando CPF já está cadastrado")
    void create_deveLancarExcecaoComCpfDuplicado() {
        when(accountRepository.existsByCpf("11111111111")).thenReturn(true);

        assertThatThrownBy(() -> accountService.create(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CPF já cadastrado");

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar BusinessException quando chave PIX já está em uso")
    void create_deveLancarExcecaoComChavePixDuplicada() {
        when(accountRepository.existsByCpf(any())).thenReturn(false);
        when(accountRepository.existsByPixKey("joao@email.com")).thenReturn(true);

        assertThatThrownBy(() -> accountService.create(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chave PIX já em uso");

        verify(accountRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // BUSCA DE CONTA
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findActive deve retornar conta ativa existente")
    void findActive_deveRetornarContaAtiva() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeAccount));

        Account result = accountService.findActive(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getActive()).isTrue();
    }

    @Test
    @DisplayName("findActive deve lançar ResourceNotFoundException para ID inexistente")
    void findActive_deveLancarExcecaoParaIdInexistente() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findActive(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("findActive deve lançar BusinessException para conta desativada")
    void findActive_deveLancarExcecaoParaContaDesativada() {
        activeAccount.setActive(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> accountService.findActive(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conta desativada");
    }

    // ─────────────────────────────────────────────────────────────────────
    // SOFT DELETE
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete deve marcar conta como inativa (soft delete)")
    void delete_deveDesativarConta() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.delete(1L);

        assertThat(activeAccount.getActive()).isFalse();
        verify(accountRepository).save(activeAccount);
    }

    // ─────────────────────────────────────────────────────────────────────
    // DEPÓSITO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit deve adicionar valor ao saldo da conta")
    void deposit_deveAdicionarSaldo() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountDTO.Response response = accountService.deposit(1L, new BigDecimal("200.00"));

        // 500.00 + 200.00 = 700.00
        assertThat(activeAccount.getBalance()).isEqualByComparingTo("700.00");
        assertThat(response.getBalance()).isEqualByComparingTo("700.00");
    }

    // ─────────────────────────────────────────────────────────────────────
    // ATUALIZAÇÃO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update deve alterar apenas os campos fornecidos")
    void update_deveAlterarApenasOsCamposFornecidos() {
        AccountDTO.UpdateRequest updateRequest = new AccountDTO.UpdateRequest();
        updateRequest.setName("João Atualizado");
        // Senha não fornecida — não deve ser alterada

        when(accountRepository.findById(1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountDTO.Response response = accountService.update(1L, updateRequest);

        assertThat(response.getName()).isEqualTo("João Atualizado");
        // Senha não deve ter sido codificada novamente
        verify(passwordEncoder, never()).encode(any());
    }
}
