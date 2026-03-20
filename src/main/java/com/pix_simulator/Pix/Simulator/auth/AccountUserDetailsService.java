package com.pix_simulator.Pix.Simulator.auth;


import com.pix_simulator.Pix.Simulator.account.Account;
import com.pix_simulator.Pix.Simulator.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementação do UserDetailsService do Spring Security.
 *
 * O Spring Security usa essa interface para carregar os dados do usuário
 * durante o processo de autenticação (login com CPF + senha).
 *
 * O metodo loadUserByUsername é chamado:
 * 1. No login: Spring verifica a senha usando os dados retornados aqui
 * 2. No JwtAuthenticationFilter: para reconstruir o Principal a partir do token
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    /**
     * Carrega os dados da conta pelo CPF (que usamos como "username").
     *
     * - @param cpf CPF da conta (identificador único usado como login)
     * - @return AccountPrincipal com os dados necessários para autenticação
     * - @throws UsernameNotFoundException se nenhuma conta for encontrada com esse CPF
     */
    @Override
    public UserDetails loadUserByUsername(String cpf) throws UsernameNotFoundException {
        // Busca a conta pelo CPF no banco
        Account account = accountRepository.findByCpf(cpf)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Conta não encontrada com CPF: " + cpf));

        // Converte a entidade Account para AccountPrincipal (UserDetails)
        return new AccountPrincipal(account);
    }
}
