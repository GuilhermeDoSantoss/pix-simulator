package com.pix_simulator.Pix.Simulator.account;


import com.pix_simulator.Pix.Simulator.auth.AccountPrincipal;
import com.pix_simulator.Pix.Simulator.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para operações de conta bancária.
 *
 * - @RestController combina @Controller + @ResponseBody - converte retornos para JSON automaticamente.
 * - @RequestMapping define o prefixo base de todas as rotas deste controller.
 * - @Tag é do Swagger - agrupa esses endpoints sob o nome "Contas" na documentação.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Contas", description = "Gerenciamento de contas bancárias")
public class AccountController {

    private final AccountService accountService;

    /**
     * Endpoint público para cadastro de nova conta.
     * Não requer autenticação - é o primeiro passo do usuário.
     *
     * - @Valid aciona as validações declaradas no DTO (CPF, nome, etc.)
     * ResponseEntity<ApiResponse<...>> permite controlar o status HTTP retornado.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar nova conta", description = "Cria uma nova conta bancária. Endpoint público.")
    public ResponseEntity<ApiResponse<AccountDTO.Response>> register(
            @Valid @RequestBody AccountDTO.CreateRequest request) {

        AccountDTO.Response response = accountService.createAccount(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Conta criada com sucesso", response));
    }

    /**
     * Retorna os dados da conta autenticada.
     *
     * @AuthenticationPrincipal injeta o AccountPrincipal que foi colocado no contexto
     * pelo JwtAuthenticationFilter após validar o token JWT.
     * O accountId vem do token - o usuário NÃO pode consultar dados de outra conta.
     *
     * @SecurityRequirement(name = "bearerAuth") informa ao Swagger que precisa de token.
     */
    @GetMapping("/me")
    @Operation(summary = "Minha conta", description = "Retorna os dados da conta autenticada")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<AccountDTO.Response>> getMyAccount(
            @AuthenticationPrincipal AccountPrincipal principal) {

        // principal.getAccountId() é extraído do JWT - não pode ser manipulado pelo cliente
        AccountDTO.Response response = accountService.getAccount(principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success("Conta encontrada", response));
    }

    /**
     * Deposita saldo na conta autenticada.
     * Em um banco real isso nunca seria um endpoint simples assim,
     * mas aqui serve para facilitar os testes do simulador.
     */
    @PostMapping("/deposit")
    @Operation(summary = "Depositar saldo", description = "Deposita um valor na conta autenticada (apenas para testes)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<AccountDTO.Response>> deposit(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AccountDTO.DepositRequest request) {

        AccountDTO.Response response = accountService.deposit(principal.getAccountId(), request.getAmount());
        return ResponseEntity.ok(ApiResponse.success("Depósito realizado com sucesso", response));
    }
}
