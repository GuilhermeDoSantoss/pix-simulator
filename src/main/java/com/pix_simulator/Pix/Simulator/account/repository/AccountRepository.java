package com.pix_simulator.Pix.Simulator.account.repository;

import com.pix_simulator.Pix.Simulator.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório de acesso a dados para a entidade Account.
 *
 * Responsabilidade: abstrair todas as operações de banco relacionadas
 * a contas. O Spring Data JPA gera as queries SQL automaticamente
 * a partir dos nomes dos métodos.
 *
 * Não contém lógica de negócio — apenas acesso a dados.
 * Toda regra de negócio fica no AccountService.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Busca uma conta pelo CPF.
     * Usado pelo AccountUserDetailsService durante o login
     * (Spring Security chama loadUserByUsername(cpf)).
     */
    Optional<Account> findByCpf(String cpf);

    /**
     * Busca uma conta pela chave PIX.
     * Usado pelo PixService para localizar o destinatário de uma transferência.
     */
    Optional<Account> findByPixKey(String pixKey);

    /**
     * Verifica se já existe uma conta com este CPF.
     * Usado na criação de conta para prevenir duplicatas (retorna 400 se true).
     */
    boolean existsByCpf(String cpf);

    /**
     * Verifica se já existe uma conta com esta chave PIX.
     * Usado na criação de conta para garantir unicidade global da chave PIX.
     */
    boolean existsByPixKey(String pixKey);
}
