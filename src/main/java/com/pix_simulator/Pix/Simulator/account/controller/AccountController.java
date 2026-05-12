package com.pix_simulator.Pix.Simulator.account.controller;

import com.pix_simulator.Pix.Simulator.account.dto.AccountDTO;
import com.pix_simulator.Pix.Simulator.account.service.AccountService;
import com.pix_simulator.Pix.Simulator.shared.response.ApiResponse;
import com.pix_simulator.Pix.Simulator.auth.security.AccountPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para gerenciamento de contas.
 *
 * Responsabilidade: receber requisições HTTP, delegar para AccountService
 * e retornar respostas padronizadas (ApiResponse).
 *
 * Segurança — IDOR Prevention:
 * Todas as operações que modificam ou consultam dados de uma conta específica
 * usam o ID extraído do JWT (@AuthenticationPrincipal), NUNCA de parâmetros
 * da URL ou do corpo da requisição. Isso impede que um usuário acesse ou
 * modifique dados de outra conta (Insecure Direct Object Reference).
 *
 * Endpoints públicos (sem autenticação):
 * - POST /register → qualquer pessoa pode criar uma conta
 *
 * Endpoints protegidos (JWT obrigatório):
 * - GET /me, PUT /me, DELETE /me, POST /me/deposit → conta do usuário autenticado
 * - GET / → lista todas as contas (mantido simples para demo)
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Gerenciamento de contas (CRUD + depósito)")
public class AccountController {

    private final AccountService accountService;

    /**
     * Cadastra uma nova conta no sistema.
     * Endpoint público — não requer autenticação.
     * Retorna HTTP 201 Created com os dados da conta criada.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar nova conta")
    public ResponseEntity<ApiResponse<AccountDTO.Response>> register(
            @Valid @RequestBody AccountDTO.CreateRequest request) {

        AccountDTO.Response response = accountService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Conta criada com sucesso", response));
    }

    /**
     * Retorna os dados da conta do usuário autenticado.
     * O accountId vem do JWT — não da URL.
     */
    @GetMapping("/me")
    @Operation(summary = "Minha conta", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AccountDTO.Response>> getMyAccount(
            @AuthenticationPrincipal AccountPrincipal principal) {

        return ResponseEntity.ok(
                ApiResponse.success("OK", accountService.getById(principal.getAccountId())));
    }

    /**
     * Lista todas as contas do sistema.
     * Mantido simples para fins de demonstração.
     * Em produção: adicionar paginação e controle de acesso por papel (ROLE_ADMIN).
     */
    @GetMapping
    @Operation(summary = "Listar todas as contas", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<AccountDTO.Response>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success("OK", accountService.listAll()));
    }

    /**
     * Atualiza nome e/ou senha da conta autenticada.
     * Apenas os campos fornecidos são alterados (semântica de PATCH).
     */
    @PutMapping("/me")
    @Operation(summary = "Atualizar minha conta", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AccountDTO.Response>> update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AccountDTO.UpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Conta atualizada", accountService.update(principal.getAccountId(), request)));
    }

    /**
     * Desativa a conta do usuário autenticado (soft delete).
     * A conta não é removida do banco — apenas marcada como inativa.
     * Isso preserva o histórico de transações vinculadas ao accountId.
     */
    @DeleteMapping("/me")
    @Operation(summary = "Desativar minha conta (soft delete)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AccountPrincipal principal) {

        accountService.delete(principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success("Conta desativada com sucesso"));
    }

    /**
     * Adiciona saldo à conta autenticada.
     * Endpoint auxiliar para testes e demonstrações.
     * Em produção seria substituído por integração com gateway bancário.
     */
    @PostMapping("/me/deposit")
    @Operation(summary = "Depositar fundos (auxiliar para testes)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AccountDTO.Response>> deposit(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AccountDTO.DepositRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Depósito realizado",
                accountService.deposit(principal.getAccountId(), request.getAmount())));
    }
}
