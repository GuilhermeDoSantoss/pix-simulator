package account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTOs (Data Transfer Objects) da conta bancária.
 *
 * DTOs são usados para separar a camada de transporte (API) da entidade de domínio.
 * Vantagens:
 * - Não expõe campos internos como a senha no response
 * - Permite validações específicas para entrada de dados
 * - Versiona a API sem alterar a entidade do banco
 *
 * Todas as classes estão agrupadas aqui como classes internas estáticas
 * para facilitar a organização em projetos menores.
 */
public class AccountDTO {

    /**
     * DTO de entrada para criar uma nova conta.
     * As anotações @NotBlank, @Size etc. são validadas automaticamente
     * pelo Spring quando @Valid é usado no controller.
     */
    @Data
    @Schema(description = "Dados para criar uma nova conta")
    public static class CreateRequest {

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        @Schema(description = "Nome completo do titular", example = "João Silva")
        private String name;

        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "CPF deve conter exatamente 11 dígitos numéricos")
        @Schema(description = "CPF sem pontos ou traços", example = "12345678901")
        private String cpf;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
        @Schema(description = "Senha de acesso", example = "senha123")
        private String password;

        @NotBlank(message = "Chave PIX é obrigatória")
        @Schema(description = "Chave PIX (CPF, email, telefone ou aleatória)", example = "joao@email.com")
        private String pixKey;

        @DecimalMin(value = "0.00", message = "Saldo inicial não pode ser negativo")
        @Schema(description = "Saldo inicial da conta", example = "1000.00")
        private BigDecimal initialBalance = BigDecimal.ZERO;
    }

    /**
     * DTO de saída com os dados públicos da conta.
     * Note que a senha NÃO está incluída aqui - nunca retorne senha.
     */
    @Data
    @Builder
    @Schema(description = "Dados da conta retornados pela API")
    public static class Response {

        @Schema(description = "ID único da conta", example = "1")
        private Long id;

        @Schema(description = "Nome do titular", example = "João Silva")
        private String name;

        @Schema(description = "CPF do titular (parcialmente mascarado)", example = "123.***.***-01")
        private String cpf;

        @Schema(description = "Chave PIX", example = "joao@email.com")
        private String pixKey;

        @Schema(description = "Saldo disponível", example = "850.00")
        private BigDecimal balance;

        @Schema(description = "Conta ativa?", example = "true")
        private Boolean active;

        @Schema(description = "Data de criação")
        private LocalDateTime createdAt;
    }

    /**
     * DTO para depósito de saldo - usado internamente para testes.
     */
    @Data
    @Schema(description = "Dados para depositar saldo")
    public static class DepositRequest {

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo de depósito é R$ 0,01")
        @Schema(description = "Valor a depositar", example = "500.00")
        private BigDecimal amount;
    }
}
