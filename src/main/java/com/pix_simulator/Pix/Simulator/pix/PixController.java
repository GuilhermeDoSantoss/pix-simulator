package com.pix_simulator.Pix.Simulator.pix;


import com.pix_simulator.Pix.Simulator.auth.AccountPrincipal;
import com.pix_simulator.Pix.Simulator.shared.ApiResponse;
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
 * Controller REST para operações PIX.
 *
 * Todos os endpoints exigem autenticação JWT.
 * O accountId é sempre extraído do token - nunca da URL ou body.
 * Isso garante que um usuário não possa fazer PIX em nome de outro.
 */
@RestController
@RequestMapping("/api/pix")
@RequiredArgsConstructor
@Tag(name = "PIX", description = "Operações de transferência PIX com idempotência")
@SecurityRequirement(name = "bearerAuth")
public class PixController {

    private final PixService pixService;

    /**
     * Envia um PIX com garantia de idempotência.
     *
     * O cliente deve gerar um UUID antes de chamar este endpoint e incluí-lo
     * no campo "idempotencyKey". Se a requisição falhar e precisar de retry,
     * deve usar o MESMO UUID - o backend detecta e retorna o resultado original.
     *
     * @param principal   conta autenticada (extraída do JWT pelo Spring Security)
     * @param request     dados do PIX incluindo a chave de idempotência
     */
    @PostMapping("/send")
    @Operation(
            summary = "Enviar PIX",
            description = """
            Realiza uma transferência PIX com garantia de idempotência.
            
            **Como usar a idempotência:**
            1. Gere um UUID no frontend antes de enviar: `crypto.randomUUID()`
            2. Inclua no campo `idempotencyKey`
            3. Se precisar reenviar (timeout, erro de rede), use o MESMO UUID
            4. O backend retornará o resultado original sem debitar novamente
            
            O campo `idempotentResponse: true` na resposta indica que foi detectado como duplicata.
            """
    )
    public ResponseEntity<ApiResponse<PixDTO.PixResponse>> sendPix(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PixDTO.PixRequest request) {

        PixDTO.PixResponse response = pixService.sendPix(principal.getAccountId(), request);
        return ResponseEntity.ok(ApiResponse.success("PIX processado", response));
    }

    /**
     * Retorna o histórico de transações da conta autenticada.
     */
    @GetMapping("/history")
    @Operation(
            summary = "Histórico de transações",
            description = "Retorna todas as transações enviadas pela conta autenticada, ordenadas da mais recente."
    )
    public ResponseEntity<ApiResponse<List<PixDTO.TransactionHistoryItem>>> getHistory(
            @AuthenticationPrincipal AccountPrincipal principal) {

        List<PixDTO.TransactionHistoryItem> history = pixService.getHistory(principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success("Histórico carregado", history));
    }

    /**
     * Retorna detalhes de uma transação específica.
     * Valida que a transação pertence à conta autenticada.
     */
    @GetMapping("/{transactionId}")
    @Operation(
            summary = "Detalhes de uma transação",
            description = "Retorna os detalhes de uma transação específica. Só permite acessar transações da própria conta."
    )
    public ResponseEntity<ApiResponse<PixDTO.PixResponse>> getTransaction(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Parameter(description = "ID da transação", example = "1")
            @PathVariable Long transactionId) {

        PixDTO.PixResponse response = pixService.getTransaction(transactionId, principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success("Transação encontrada", response));
    }
}
