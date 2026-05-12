package com.pix_simulator.Pix.Simulator.auth.controller;

import com.pix_simulator.Pix.Simulator.auth.dto.AuthDTO;
import com.pix_simulator.Pix.Simulator.auth.service.AuthService;
import com.pix_simulator.Pix.Simulator.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para autenticação.
 *
 * Responsabilidade: expor o endpoint de login e delegar
 * a lógica de autenticação para o AuthService.
 *
 * Endpoint público — não requer JWT.
 * Após login bem-sucedido, o cliente recebe um JWT que deve ser
 * enviado em todas as requisições subsequentes no header:
 *   Authorization: Bearer <token>
 *
 * Erros de autenticação retornam HTTP 401 com mensagem genérica
 * ("CPF ou senha inválidos"), sem revelar se o CPF existe no sistema.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login e geração de token JWT")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
            summary = "Login com CPF e senha",
            description = """
            Autentica o usuário com CPF e senha.
            
            Retorna um token JWT que deve ser incluído em todas as
            requisições protegidas no header:
            `Authorization: Bearer <token>`
            
            O token expira em 24 horas (configurável em application.yml).
            """
    )
    public ResponseEntity<ApiResponse<AuthDTO.LoginResponse>> login(
            @Valid @RequestBody AuthDTO.LoginRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success("Login realizado com sucesso", authService.login(request)));
    }
}
