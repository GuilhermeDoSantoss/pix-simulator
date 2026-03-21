package com.pix_simulator.Pix.Simulator.account;

import com.pix_simulator.Pix.Simulator.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Serviço responsável pela lógica de negócio das contas bancárias.
 *
 * - @Service registra a classe como um bean Spring gerenciado.
 * - @RequiredArgsConstructor cria o construtor injetando todos os campos final (injeção por construtor).
 * - @Slf4j injeta o logger para registrar operações importantes.
 * - @Transactional(readOnly = true) por padrão - operações de escrita sobrescrevem para readOnly = false.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Cria uma nova conta bancária.
     *
     * @Transactional sem readOnly = false garante que o INSERT seja executado
     * e revertido se qualquer exceção ocorrer.
     */
    @Transactional
    public AccountDTO.Response createAccount(AccountDTO.CreateRequest request) {
        log.info("Criando conta para CPF: {}", maskCpf(request.getCpf()));

        // Valida unicidade do CPF - dois cadastros com mesmo CPF não são permitidos
        if (accountRepository.existsByCpf(request.getCpf())) {
            throw new BusinessException("CPF já cadastrado no sistema");
        }

        // Valida unicidade da chave PIX - cada chave deve apontar para uma única conta
        if (accountRepository.existsByPixKey(request.getPixKey())) {
            throw new BusinessException("Chave PIX já utilizada por outra conta");
        }

        // Constrói a entidade usando o Builder pattern gerado pelo Lombok
        Account account = Account.builder()
                .name(request.getName())
                .cpf(request.getCpf())
                // Criptografa a senha com BCrypt antes de salvar - NUNCA salve senha em texto puro
                .password(passwordEncoder.encode(request.getPassword()))
                .pixKey(request.getPixKey())
                // Se o saldo inicial for null, usa BigDecimal.ZERO
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .active(true)
                .build();

        // Persiste no banco e retorna a entidade salva com o ID gerado
        Account saved = accountRepository.save(account);
        log.info("Conta criada com ID: {}", saved.getId());

        return toResponse(saved);
    }

    /**
     * Busca uma conta pelo ID.
     * O accountId vem sempre do JWT, não da URL - garantindo isolamento entre contas.
     */
    public AccountDTO.Response getAccount(Long accountId) {
        Account account = findActiveAccountById(accountId);
        return toResponse(account);
    }

    /**
     * Deposita saldo em uma conta - útil para testes de simulação.
     */
    @Transactional
    public AccountDTO.Response deposit(Long accountId, BigDecimal amount) {
        log.info("Depósito de R$ {} na conta {}", amount, accountId);

        Account account = findActiveAccountById(accountId);

        // BigDecimal.add() retorna um novo objeto - BigDecimal é imutável
        account.setBalance(account.getBalance().add(amount));
        Account updated = accountRepository.save(account);

        log.info("Saldo após depósito: R$ {}", updated.getBalance());
        return toResponse(updated);
    }

    /**
     * Busca uma conta ativa pelo ID.
     * Metodo interno reutilizado pelos serviços de PIX também.
     * Lança exceção se a conta não existir ou estiver inativa.
     */
    public Account findActiveAccountById(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada com ID: " + accountId));

        if (!account.getActive()) {
            throw new BusinessException("Conta inativa. Operações não permitidas.");
        }

        return account;
    }

    /**
     * Converte a entidade Account para o DTO de resposta.
     * Mascara o CPF para não expor dados sensíveis.
     */
    private AccountDTO.Response toResponse(Account account) {
        return AccountDTO.Response.builder()
                .id(account.getId())
                .name(account.getName())
                // Mascara o CPF: exibe apenas os 3 primeiros e 2 últimos dígitos
                .cpf(maskCpf(account.getCpf()))
                .pixKey(account.getPixKey())
                .balance(account.getBalance())
                .active(account.getActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    /**
     * Mascara o CPF para exibição: 123****8901 -> 123.***.***.01
     * Formato simplificado para exemplo.
     */
    private String maskCpf(String cpf) {
        if (cpf == null || cpf.length() < 11) return cpf;
        return cpf.substring(0, 3) + ".***.***-" + cpf.substring(9);
    }
}
