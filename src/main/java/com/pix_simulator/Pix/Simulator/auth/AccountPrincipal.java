package com.pix_simulator.Pix.Simulator.auth;

package com.pixsimulator.auth.security;

import com.pixsimulator.account.entity.Account;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Representa o usuário autenticado no contexto do Spring Security.
 *
 * Implementa UserDetails - interface exigida pelo Spring Security para
 * representar qualquer usuário autenticado no sistema.
 *
 * O AccountPrincipal é criado pelo JwtAuthenticationFilter após validar o token
 * e é injetado automaticamente nos controllers via @AuthenticationPrincipal.
 *
 * Isso garante que em qualquer controller você tenha acesso ao accountId
 * sem precisar buscar no banco ou confiar em parâmetros da requisição.
 */
@Getter
public class AccountPrincipal implements UserDetails {

    /**
     * ID da conta no banco - extraído do JWT.
     * Este é o dado mais importante: isola completamente os dados por conta.
     */
    private final Long accountId;

    /**
     * CPF usado como username no Spring Security.
     */
    private final String cpf;

    /**
     * Senha criptografada - necessária para o mecanismo de autenticação do Spring.
     */
    private final String password;

    /**
     * Se a conta está ativa - Spring Security usa isso para bloquear logins.
     */
    private final boolean active;

    /**
     * Constrói o Principal a partir da entidade Account.
     * Chamado pelo UserDetailsService após carregar a conta do banco.
     */
    public AccountPrincipal(Account account) {
        this.accountId = account.getId();
        this.cpf = account.getCpf();
        this.password = account.getPassword();
        this.active = account.getActive();
    }

    /**
     * Retorna as permissões/papéis do usuário.
     * Neste simulador não implementamos roles, então retorna lista vazia.
     * Em produção: retornaria [ROLE_USER, ROLE_ADMIN, etc.]
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /**
     * O "username" para o Spring Security é o CPF da conta.
     */
    @Override
    public String getUsername() {
        return cpf;
    }

    /**
     * Indica se a conta não está expirada.
     * Aqui vinculamos ao campo "active" da conta.
     */
    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    /**
     * Indica se a conta não está bloqueada.
     */
    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    /**
     * Indica se as credenciais (senha) não estão expiradas.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indica se a conta está habilitada para uso.
     */
    @Override
    public boolean isEnabled() {
        return active;
    }
}
