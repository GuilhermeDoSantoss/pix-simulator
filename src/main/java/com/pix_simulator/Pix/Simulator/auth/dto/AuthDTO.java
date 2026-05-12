package com.pix_simulator.Pix.Simulator.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTOs de autenticação.
 *
 * Responsabilidade: transportar dados entre o cliente e o AuthController
 * sem expor detalhes internos da entidade Account.
 */
public class AuthDTO {

    /**
     * Payload de entrada para o endpoint de login.
     * Validações garantem que dados malformados sejam rejeitados
     * antes de chegar ao AuthService.
     */
    @Data
    @Schema(description = "Credenciais de login")
    public static class LoginRequest {

        @NotBlank(message = "CPF é obrigatório")
        @Pattern(regexp = "\\d{11}", message = "CPF deve ter exatamente 11 dígitos")
        @Schema(example = "12345678901")
        private String cpf;

        @NotBlank(message = "Senha é obrigatória")
        @Schema(example = "senha123")
        private String password;
    }

    /**
     * Resposta do login com o JWT e dados básicos da conta.
     *
     * - token: JWT a ser enviado em todas as requisições subsequentes
     * - type: sempre "Bearer" — indica o esquema de autenticação
     * - accountId: ID da conta para referência no frontend
     * - name: nome do usuário para exibição na UI
     * - expiresIn: TTL do token em milissegundos (para refresh logic no cliente)
     */
    @Data
    @Schema(description = "Resposta com JWT e dados da conta")
    public static class LoginResponse {
        private final String token;
        private final String type = "Bearer";
        private final Long accountId;
        private final String name;
        private final long expiresIn;
    }
}
