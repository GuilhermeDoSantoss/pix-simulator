package com.pix_simulator.Pix.Simulator.pix.dto;

import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTOs do módulo PIX.
 *
 * Responsabilidade: definir os contratos de entrada e saída da API
 * para operações de transferência PIX.
 *
 * Estrutura:
 * - SendRequest  → POST /api/pix/send
 * - PixResponse  → resposta de envio e consulta individual
 * - HistoryItem  → item do histórico de transações (GET /api/pix/history)
 */
public class PixDTO {

    /**
     * Payload de envio de PIX.
     *
     * O campo mais importante é o idempotencyKey:
     * - Deve ser um UUID gerado pelo cliente ANTES do envio
     * - Se a mesma chave for enviada duas vezes, o servidor retorna
     *   o resultado original sem processar novamente
     * - Isso garante que duplo-clique ou retry de rede não gere duplicata
     */
    @Data
    @Schema(description = "Dados para envio de uma transferência PIX")
    public static class SendRequest {

        /**
         * CHAVE DE IDEMPOTÊNCIA — campo crítico para segurança financeira.
         *
         * O cliente deve gerar um UUID v4 único antes de clicar "Enviar PIX".
         * Em caso de falha de rede ou timeout, o mesmo UUID pode ser reenviado
         * com segurança — o servidor detecta a duplicata e retorna o resultado
         * original sem debitar o saldo novamente.
         *
         * Formato recomendado: UUID v4 — ex: "550e8400-e29b-41d4-a716-446655440000"
         */
        @NotBlank(message = "Chave de idempotência é obrigatória")
        @Schema(
                description = "UUID gerado pelo cliente. Reenviar o mesmo UUID é seguro — retorna resultado original.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        private String idempotencyKey;

        @NotBlank(message = "Chave PIX do destinatário é obrigatória")
        @Schema(description = "Chave PIX do destinatário", example = "maria@email.com")
        private String receiverPixKey;

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo de transferência é R$ 0,01")
        @Schema(example = "150.00")
        private BigDecimal amount;

        @Size(max = 140, message = "Descrição deve ter no máximo 140 caracteres")
        @Schema(example = "Almoço do grupo")
        private String description;
    }

    /**
     * Resposta de uma operação PIX (envio ou consulta individual).
     *
     * O campo idempotentHit=true indica que a requisição foi reconhecida
     * como duplicata e o resultado retornado é o original (sem novo processamento).
     */
    @Data
    @Builder
    @Schema(description = "Resultado de uma operação PIX")
    public static class PixResponse {
        private Long transactionId;
        private String idempotencyKey;
        private TransactionStatus status;
        private BigDecimal amount;
        private String receiverPixKey;
        private String receiverName;
        private String message;

        /** true quando a resposta veio do cache por chave de idempotência duplicada */
        private boolean idempotentHit;

        private LocalDateTime processedAt;
        private LocalDateTime createdAt;
    }

    /**
     * Item resumido do histórico de transações.
     * Retornado pelo endpoint GET /api/pix/history, mais recente primeiro.
     */
    @Data
    @Builder
    @Schema(description = "Item do histórico de transações enviadas")
    public static class HistoryItem {
        private Long id;
        private String idempotencyKey;
        private TransactionStatus status;
        private BigDecimal amount;
        private String receiverPixKey;
        private String receiverName;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime processedAt;
    }
}
