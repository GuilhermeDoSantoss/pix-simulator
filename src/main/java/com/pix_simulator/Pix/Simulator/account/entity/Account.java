package com.pix_simulator.Pix.Simulator.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade JPA que representa uma conta bancária no sistema PIX.
 *
 * Responsabilidade: mapear a tabela "accounts" no banco de dados H2.
 * Cada conta pertence a um usuário e possui saldo, chave PIX e credenciais de acesso.
 *
 * Regras de negócio embutidas:
 * - cpf é o username de login (único por conta)
 * - pixKey é o endereço de recebimento PIX (único por conta)
 * - password é armazenado SEMPRE como hash BCrypt — jamais em texto plano
 * - active=false significa soft delete — a conta existe no banco mas está desativada
 * - balance usa BigDecimal (não double/float) para evitar erros de ponto flutuante
 *   em valores monetários (ex: 0.1 + 0.2 = 0.30000000000000004 em float)
 *
 * Anotações Lombok:
 * - @Data gera getters, setters, equals, hashCode e toString
 * - @Builder habilita o padrão Builder para construção do objeto
 * - @NoArgsConstructor e @AllArgsConstructor são necessários para JPA + Builder
 */
@Entity
@Table(
        name = "accounts",
        indexes = {
                @Index(name = "idx_account_cpf",     columnList = "cpf"),
                @Index(name = "idx_account_pix_key", columnList = "pixKey")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome completo do titular da conta */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * CPF do usuário — funciona como username de login.
     * Constraint UNIQUE garante que cada CPF só pode ter uma conta.
     * length=11 porque CPF tem exatamente 11 dígitos (sem pontuação).
     */
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    /**
     * Senha hashada com BCrypt.
     * NUNCA armazenar senha em texto plano.
     * O AccountService sempre chama passwordEncoder.encode() antes de persistir.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Chave PIX — identificador único para recebimento de transferências.
     * Pode ser email, telefone, CPF ou UUID aleatório.
     * Constraint UNIQUE garante que cada chave aponta para exatamente uma conta.
     */
    @Column(nullable = false, unique = true)
    private String pixKey;

    /**
     * Saldo disponível da conta.
     * precision=15, scale=2 suporta valores de até R$ 9.999.999.999.999,99.
     * Builder.Default garante que contas criadas sem saldo inicial partem de ZERO.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Flag de ativação da conta.
     * false = conta desativada via soft delete (AccountService.delete()).
     * Contas inativas não podem enviar nem receber PIX.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Momento de criação do registro — imutável após insert */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento da última atualização — gerenciado automaticamente pelo Hibernate */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
