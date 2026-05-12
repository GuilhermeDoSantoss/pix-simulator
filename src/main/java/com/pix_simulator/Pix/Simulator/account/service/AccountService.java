package com.pix_simulator.Pix.Simulator.account.service;

import com.pix_simulator.Pix.Simulator.account.dto.AccountDTO;
import com.pix_simulator.Pix.Simulator.account.entity.Account;
import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import com.pix_simulator.Pix.Simulator.shared.exception.BusinessException;
import com.pix_simulator.Pix.Simulator.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de gerenciamento de contas.
 *
 * Responsabilidade: toda regra de negócio relacionada a contas —
 * criação, consulta, atualização, exclusão lógica e depósito.
 *
 * Segurança:
 * - Senhas são armazenadas como hash BCrypt — nunca em texto plano
 * - Exclusão é sempre lógica (soft delete: active=false) para
 *   preservar histórico de transações associadas à conta
 * - findActive() é chamado pelos outros serviços para garantir que
 *   operações nunca acontecem em contas desativadas
 *
 * Transações:
 * - Nível de classe: readOnly=true — operações de leitura não abrem
 *   transações de escrita desnecessárias (melhor performance)
 * - @Transactional nas escritas sobrescreve readOnly para read-write
 *
 * CORREÇÃO: findActive() agora tem @Transactional próprio.
 * Quando chamado de fora de uma transação ativa (ex: pelo PixService
 * antes de iniciar sua transação), o metodo abre uma transação
 * própria de leitura para garantir leitura consistente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder   passwordEncoder;

    /**
     * Cria uma nova conta com saldo inicial.
     *
     * Regras de negócio:
     * - CPF deve ser único no sistema (é o username de login)
     * - Chave PIX deve ser única globalmente (é o endereço de recebimento)
     * - Senha é hasheada com BCrypt antes de persistir
     *
     * param request dados da nova conta
     * return resposta com dados da conta criada (sem senha)
     */
    @Transactional
    public AccountDTO.Response create(AccountDTO.CreateRequest request) {
        if (accountRepository.existsByCpf(request.getCpf())) {
            throw new BusinessException("CPF já cadastrado: " + request.getCpf());
        }
        if (accountRepository.existsByPixKey(request.getPixKey())) {
            throw new BusinessException("Chave PIX já em uso: " + request.getPixKey());
        }

        Account account = Account.builder()
                .name(request.getName())
                .cpf(request.getCpf())
                .password(passwordEncoder.encode(request.getPassword()))
                .pixKey(request.getPixKey())
                .balance(request.getInitialBalance() != null
                        ? request.getInitialBalance()
                        : BigDecimal.ZERO)
                .active(true)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Conta criada id={} cpf={}", saved.getId(), saved.getCpf());
        return toResponse(saved);
    }

    /**
     * Retorna dados da conta ativa pelo ID.
     * Lança ResourceNotFoundException se não existir, BusinessException se inativa.
     */
    public AccountDTO.Response getById(Long id) {
        return toResponse(findActive(id));
    }

    /** Lista todas as contas (ativas e inativas) */
    public List<AccountDTO.Response> listAll() {
        return accountRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Atualiza nome e/ou senha da conta autenticada.
     * Apenas os campos fornecidos (não-nulos) são alterados.
     */
    @Transactional
    public AccountDTO.Response update(Long id, AccountDTO.UpdateRequest request) {
        Account account = findActive(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            account.setName(request.getName());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            account.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return toResponse(accountRepository.save(account));
    }

    /**
     * Desativa uma conta (soft delete).
     *
     * A conta é marcada como inactive em vez de removida fisicamente.
     * Isso preserva todo o historico de transações vinculadas ao accountId,
     * garantindo rastreabilidade e integridade do extrato.
     */
    @Transactional
    public void delete(Long id) {
        Account account = findActive(id);
        account.setActive(false);
        accountRepository.save(account);
        log.info("Conta desativada (soft delete) id={}", id);
    }

    /**
     * Adiciona saldo à conta. Endpoint auxiliar para testes/demos.
     * Em produção real, isso seria substituído por integração com sistema de depósito.
     */
    @Transactional
    public AccountDTO.Response deposit(Long id, BigDecimal amount) {
        Account account = findActive(id);
        account.setBalance(account.getBalance().add(amount));
        log.info("Depósito realizado id={} valor={}", id, amount);
        return toResponse(accountRepository.save(account));
    }

    /**
     * Busca conta ativa pelo ID.
     *
     * Metodo chamado por outros serviços (PixService, PixEventConsumer)
     * para garantir que operações PIX nunca acontecem em contas inativas.
     *
     * @Transactional garante leitura consistente mesmo quando chamado de fora de uma transação ativa.
     *
     * param id ID da conta
     * return entidade Account ativa
     * throws ResourceNotFoundException se a conta não existir
     * throws BusinessException se a conta estiver desativada
     */
    @Transactional(readOnly = true)
    public Account findActive(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + id));

        if (!account.getActive()) {
            throw new BusinessException("Conta desativada: " + id);
        }

        return account;
    }

    /**
     * Converte entidade Account para DTO de resposta.
     * NUNCA inclui a senha — a senha hasheada jamais deve sair do servidor.
     */
    private AccountDTO.Response toResponse(Account account) {
        return AccountDTO.Response.builder()
                .id(account.getId())
                .name(account.getName())
                .cpf(account.getCpf())
                .pixKey(account.getPixKey())
                .balance(account.getBalance())
                .active(account.getActive())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
