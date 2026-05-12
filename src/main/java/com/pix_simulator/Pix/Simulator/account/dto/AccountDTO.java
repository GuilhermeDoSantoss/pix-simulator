package com.pix_simulator.Pix.Simulator.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTOs (Data Transfer Objects) do módulo de contas.
 *
 * Responsabilidade: definir os contratos de entrada e saída da API
 * para operações de conta, separando a camada de transporte da entidade JPA.
 *
 * Por que usar DTOs em vez de expor a entidade Account diretamente?
 * - Segurança: a senha (hash BCrypt) nunca aparece nas respostas
 * - Flexibilidade: a entidade pode mudar sem quebrar o contrato da API
 * - Validação: regras de validação específicas por operação (criar vs atualizar)
 * - Documentação: @Schema do Swagger documenta cada campo individualmente
 *
 * Estrutura:
 * - CreateRequest  → POST /api/accounts/register
 * - UpdateRequest  → PUT /api/accounts/me
 * - Response       → retorno de qualquer operação de conta
 * - DepositRequest → POST /api/accounts/me/deposit (helper para testes)
 */
public class AccountDTO {

    /**
     * Payload para criação de nova conta.
     * Todos os campos obrigatórios são validados com Bean Validation (@NotBlank, @Pattern, etc.)
     * antes de chegar ao AccountService.
     */
    @Data
    @Schema(description = "Dados para cadastro de nova conta")
    public static class CreateRequest {

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        @Schema(example = "João Silva")
        private String name;

        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "CPF deve ter exatamente 11 dígitos numéricos")
        @Schema(example = "12345678901")
        private String cpf;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        @Schema(example = "senha123")
        private String password;

        @NotBlank(message = "Chave PIX é obrigatória")
        @Schema(
                description = "Chave PIX única: email, telefone, CPF ou UUID aleatório",
                example = "joao@email.com"
        )
        private String pixKey;

        @DecimalMin(value = "0.00", message = "Saldo inicial não pode ser negativo")
        @Schema(description = "Saldo inicial da conta", example = "1000.00")
        private BigDecimal initialBalance = BigDecimal.ZERO;
    }

    /**
     * Payload para atualização parcial de conta.
     * Todos os campos são opcionais — apenas os não-nulos são atualizados (PATCH semântico).
     */
    @Data
    @Schema(description = "Dados para atualização de conta (campos opcionais)")
    public static class UpdateRequest {

        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        @Schema(example = "João Silva Atualizado")
        private String name;

        @Size(min = 6, message = "Nova senha deve ter no mínimo 6 caracteres")
        @Schema(example = "novaSenha456")
        private String password;
    }

    /**
     * DTO de resposta para operações de conta.
     *
     * SEGURANÇA: campo password da entidade Account é intencionalmente
     * omitido aqui. A senha hasheada jamais deve sair do servidor.
     */
    @Data
    @Builder
    @Schema(description = "Dados da conta retornados pela API (sem senha)")
    public static class Response {
        private Long id;
        private String name;
        private String cpf;
        private String pixKey;
        private BigDecimal balance;
        private Boolean active;
        private LocalDateTime createdAt;
    }

    /**
     * Payload para depósito de fundos.
     * Endpoint auxiliar para facilitar testes e demonstrações.
     * Em produção real seria substituído por integração com sistema bancário.
     */
    @Data
    @Schema(description = "Valor a ser depositado na conta autenticada")
    public static class DepositRequest {

        @NotNull(message = "Valor do depósito é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo de depósito é R$ 0,01")
        @Schema(example = "500.00")
        private BigDecimal amount;
    }
}
