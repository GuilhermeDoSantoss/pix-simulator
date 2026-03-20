package account;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade que representa uma conta bancária no sistema PIX.
 *
 * - @Entity mapeia essa classe para a tabela "accounts" no PostgreSQL.
 * Cada conta tem seu próprio saldo, chave PIX e credenciais de acesso.
 *
 * O isolamento entre contas é garantido pelo JWT:
 * o accountId é embutido no token e nenhum endpoint aceita accountId externo.
 */
@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    /**
     * ID gerado automaticamente pelo banco (IDENTITY = auto_increment no PostgreSQL).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nome completo do titular da conta.
     */
    @Column(nullable = false)
    private String name;

    /**
     * CPF único do titular - usado como identificador de login.
     * unique = true garante que não existam dois cadastros com o mesmo CPF.
     */
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    /**
     * Senha do usuário armazenada como hash BCrypt.
     * NUNCA armazene senhas em texto puro.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Chave PIX da conta - pode ser CPF, email, telefone ou chave aleatória.
     * Deve ser única no sistema para que o PIX seja roteado corretamente.
     */
    @Column(nullable = false, unique = true)
    private String pixKey;

    /**
     * Saldo disponível na conta.
     * BigDecimal é obrigatório para valores monetários - evita erros de ponto flutuante.
     * precision=15: até 15 dígitos no total | scale=2: 2 casas decimais (centavos)
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Se a conta está ativa. Contas inativas não podem fazer ou receber PIX.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Timestamps automáticos gerenciados pelo Hibernate.
     * updatable = false impede que createdAt seja alterado após a criação.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}