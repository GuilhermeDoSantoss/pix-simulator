package com.pix_simulator.Pix.Simulator.account;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository da conta bancária.
 *
 * JpaRepository<Account, Long> já fornece gratuitamente:
 * save(), findById(), findAll(), deleteById(), count(), etc.
 *
 * Métodos adicionais abaixo usam Spring Data Query Derivation:
 * o Spring gera o SQL automaticamente baseado no nome do método.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Busca conta pelo CPF - usado no login para autenticação.
     * SQL gerado: SELECT * FROM accounts WHERE cpf = ?
     */
    Optional<Account> findByCpf(String cpf);

    /**
     * Busca conta pela chave PIX - usado para encontrar o destinatário.
     * SQL gerado: SELECT * FROM accounts WHERE pix_key = ?
     */
    Optional<Account> findByPixKey(String pixKey);

    /**
     * Verifica se já existe conta com esse CPF - usado no cadastro para evitar duplicata.
     * SQL gerado: SELECT COUNT(*) > 0 FROM accounts WHERE cpf = ?
     */
    boolean existsByCpf(String cpf);

    /**
     * Verifica se já existe conta com essa chave PIX - garante unicidade.
     */
    boolean existsByPixKey(String pixKey);
}
