package com.pix_simulator.Pix.Simulator.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTOs de autenticação (login).
 */
public class AuthDTO {

    /**
     * Dados de entrada do login: CPF + senha.
     */
    @Data
    @Schema(description = "Credenciais de login")
    public static class LoginRequest {

        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 dígitos")
        @Schema(description = "CPF sem pontos ou traços", example = "12345678901")
        private String cpf;

        @NotBlank(message = "Senha é obrigatória")
        @Schema(description = "Senha da conta", example = "senha123")
        private String password;
    }

    /**
     * Resposta do login: token JWT + informações básicas da conta.
     */
    @Data
    @Schema(description = "Resposta do login com token JWT")
    public static class LoginResponse {

        @Schema(description = "Token JWT para uso nas requisições autenticadas")
        private String token;

        @Schema(description = "Tipo do token - sempre 'Bearer'", example = "Bearer")
        private String tokenType = "Bearer";

        @Schema(description = "ID da conta autenticada", example = "1")
        private Long accountId;

        @Schema(description = "Nome do titular", example = "João Silva")
        private String name;

        @Schema(description = "Expiração em milissegundos", example = "86400000")
        private long expiresIn;

        public LoginResponse(String token, Long accountId, String name, long expiresIn) {
            this.token = token;
            this.accountId = accountId;
            this.name = name;
            this.expiresIn = expiresIn;
        }
    }
}
