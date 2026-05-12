package com.pix_simulator.Pix.Simulator.auth.security;

import com.pix_simulator.Pix.Simulator.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do UserDetailsService do Spring Security.
 *
 * Responsabilidade: carregar os dados do usuário (AccountPrincipal)
 * a partir do banco de dados usando o CPF como identificador.
 *
 * Este serviço é chamado em dois momentos distintos:
 *
 * 1. DURANTE O LOGIN (DaoAuthenticationProvider):
 *    O Spring Security chama loadUserByUsername(cpf) para carregar
 *    o usuário e então compara a senha fornecida com o hash BCrypt.
 *
 * 2. DURANTE CADA REQUISIÇÃO AUTENTICADA (JwtAuthenticationFilter):
 *    Após validar o JWT, o filtro chama este serviço para carregar
 *    os dados frescos da conta do banco e popular o SecurityContext.
 *
 * Transactional(readOnly = true): abre uma transação de leitura
 * para garantir que o Hibernate não deixe a sessão aberta desnecessariamente.
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    /**
     * Carrega o usuário pelo CPF.
     *
     * @param cpf CPF da conta (usado como username no Spring Security)
     * @return AccountPrincipal com dados da conta
     * @throws UsernameNotFoundException se não houver conta com este CPF
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        return new AccountPrincipal(
                accountRepository.findByCpf(cpf)
                        .orElseThrow(() -> new UsernameNotFoundException(
                                "Nenhuma conta encontrada para o CPF: " + cpf))
        );
    }
}
