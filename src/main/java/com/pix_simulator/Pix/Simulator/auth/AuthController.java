package com.pix_simulator.Pix.Simulator.auth;


import io.swagger.v3.oas.annotations.Operation;
import com.pix_simulator.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de autenticação - endpoint de login.
 * Rota pública: não requer token JWT.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e geração de token JWT")
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint de login.
     * Recebe CPF + senha, valida e retorna token JWT.
     *
     * O token deve ser incluído em todas as requisições autenticadas no header:
     * Authorization: Bearer <token>
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login",
            description = "Autentica a conta com CPF e senha. Retorna token JWT para uso nas próximas requisições."
    )
    public ResponseEntity<ApiResponse<AuthDTO.LoginResponse>> login(
            @Valid @RequestBody AuthDTO.LoginRequest request) {

        AuthDTO.LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login realizado com sucesso", response));
    }
}
