package com.pix_simulator.Pix.Simulator.auth.security;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Implementação de UserDetails que encapsula uma conta do sistema PIX.
 *
 * Responsabilidade: adaptar a entidade Account para o contrato do
 * Spring Security, permitindo que o framework gerencie autenticação
 * sem conhecer detalhes da nossa modelagem de domínio.
 *
 * Após validação do JWT pelo JwtAuthenticationFilter, um AccountPrincipal
 * é armazenado no SecurityContext. Controllers acessam via
 * AuthenticationPrincipal para obter accountId e nome sem queries adicionais.
 *
 * CORREÇÃO: campo "name" adicionado para que AuthService possa retornar
 * o nome do usuário no login response sem fazer uma segunda query ao banco.
 *
 * Segurança:
 * - isAccountNonExpired, isAccountNonLocked e isEnabled retornam false
 *   para contas inativas, impedindo login de contas desativadas (soft-delete)
 */
@Getter
public class AccountPrincipal implements UserDetails {

    private final Long accountId;
    private final String cpf;
    private final String name;
    private final String password;
    private final boolean active;

    public AccountPrincipal(Account account) {
        this.accountId = account.getId();
        this.cpf       = account.getCpf();
        this.name      = account.getName();
        this.password  = account.getPassword();
        this.active    = account.getActive();
    }

    // ── UserDetails contract ─────────────────────────────────────────────

    /** O username no nosso sistema é o CPF */
    @Override
    public String getUsername() { return cpf; }

    /** Sem papéis de autorização por enquanto — todos os usuários têm acesso igual */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }

    /** Contas inativas (soft-deleted) não podem fazer login */
    @Override public boolean isAccountNonExpired()     { return active; }
    @Override public boolean isAccountNonLocked()      { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return active; }
}
