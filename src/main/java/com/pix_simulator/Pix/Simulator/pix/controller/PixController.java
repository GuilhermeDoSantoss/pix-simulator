package com.pix_simulator.Pix.Simulator.pix.controller;

import com.pix_simulator.Pix.Simulator.auth.security.AccountPrincipal;
import com.pix_simulator.Pix.Simulator.pix.dto.PixDTO;
import com.pix_simulator.Pix.Simulator.pix.service.PixService;
import com.pix_simulator.Pix.Simulator.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para transferências PIX.
 *
 * Responsabilidade: receber requests HTTP, delegar para PixService
 * e retornar respostas padronizadas via ApiResponse.
 *
 * Segurança: todos os endpoints exigem JWT válido.
 * O accountId SEMPRE vem do @AuthenticationPrincipal (JWT) —
 * jamais de parâmetros da URL ou do corpo da requisição.
 * Isso torna impossível para um usuário agir em nome de outro (IDOR).
 *
 * Os métodos do controller são intencionalmente simples (thin controller):
 * toda lógica de negócio fica no PixService.
 */
@RestController
@RequestMapping("/api/pix")
@RequiredArgsConstructor
@Tag(name = "PIX", description = "Transferências PIX com garantia de idempotência")
@SecurityRequirement(name = "bearerAuth")
public class PixController {

    private final PixService pixService;

    @PostMapping("/send")
    @Operation(
            summary = "Enviar um PIX",
            description = """
            Inicia uma transferência PIX.
            
            Inclua um **idempotencyKey** (UUID v4) no corpo da requisição.
            Reenviar a mesma chave é seguro — o servidor retorna o resultado
            original sem processar a transferência novamente.
            
            O campo `idempotentHit: true` na resposta indica que uma duplicata
            foi detectada e o resultado veio do cache.
            
            **Status possíveis na resposta:**
            - `PENDING` → aceito, aguardando processamento Kafka
            - `COMPLETED` → processado com sucesso (em idempotency hit)
            - `FLAGGED` → bloqueado pela detecção de anomalia (em idempotency hit)
            """
    )
    public ResponseEntity<ApiResponse<PixDTO.PixResponse>> send(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PixDTO.SendRequest request) {

        PixDTO.PixResponse response = pixService.send(principal.getAccountId(), request);
        return ResponseEntity.ok(ApiResponse.success("PIX processado", response));
    }

    @GetMapping("/history")
    @Operation(summary = "Histórico de transações enviadas (mais recente primeiro)")
    public ResponseEntity<ApiResponse<List<PixDTO.HistoryItem>>> history(
            @AuthenticationPrincipal AccountPrincipal principal) {

        List<PixDTO.HistoryItem> history = pixService.getHistory(principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success("OK", history));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Detalhes de uma transação",
            description = "Retorna os detalhes de uma transação. Deve pertencer à conta autenticada."
    )
    public ResponseEntity<ApiResponse<PixDTO.PixResponse>> getById(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Parameter(description = "ID da transação") @PathVariable Long id) {

        PixDTO.PixResponse response = pixService.getById(id, principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }
}
