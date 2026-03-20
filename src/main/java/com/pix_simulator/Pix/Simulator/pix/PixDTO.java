package com.pix_simulator.Pix.Simulator.pix;


import com.pix_simulator.Pix.Simulator.entity.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTOs das operações PIX.
 *
 * O campo mais importante é o idempotencyKey no PixRequest:
 * ele é gerado pelo CLIENTE (frontend) antes de enviar a requisição.
 * O backend usa essa chave para garantir que o mesmo PIX não seja
 * processado duas vezes, mesmo em caso de retry por falha de rede.
 */
public class PixDTO {

    /**
     * DTO de entrada para enviar um PIX.
     */
    @Data
    @Schema(description = "Dados para realizar um PIX")
    public static class PixRequest {

        /**
         * Chave de idempotência gerada pelo cliente (UUID v4).
         *
         * COMO FUNCIONA:
         * 1. O frontend gera um UUID antes de clicar em "Enviar PIX"
         * 2. Envia esse UUID junto com os dados do PIX
         * 3. Se a requisição falhar e o usuário tentar de novo,
         *    o frontend usa o MESMO UUID
         * 4. O backend detecta que já processou essa chave e retorna
         *    o resultado anterior sem debitar duas vezes
         *
         * Formato esperado: "550e8400-e29b-41d4-a716-446655440000"
         */
        @NotBlank(message = "Chave de idempotência é obrigatória")
        @Schema(
                description = "UUID gerado pelo cliente para garantir idempotência. Mesmo UUID = mesmo PIX.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        private String idempotencyKey;

        /**
         * Chave PIX do destinatário (CPF, email, telefone, chave aleatória).
         */
        @NotBlank(message = "Chave PIX do destinatário é obrigatória")
        @Schema(description = "Chave PIX do destinatário", example = "destinatario@email.com")
        private String receiverPixKey;

        /**
         * Valor a ser transferido.
         * Mínimo de R$ 0,01 - não é possível fazer PIX com valor zero.
         */
        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor mínimo do PIX é R$ 0,01")
        @Schema(description = "Valor a transferir", example = "150.00")
        private BigDecimal amount;

        /**
         * Descrição opcional da transferência.
         */
        @Schema(description = "Descrição opcional", example = "Pagamento do almoço")
        private String description;
    }

    /**
     * DTO de resposta com os dados da transação processada.
     */
    @Data
    @Builder
    @Schema(description = "Resultado do processamento do PIX")
    public static class PixResponse {

        @Schema(description = "ID da transação", example = "42")
        private Long transactionId;

        @Schema(description = "Chave de idempotência recebida", example = "550e8400-e29b-41d4-a716-446655440000")
        private String idempotencyKey;

        @Schema(description = "Status atual", example = "COMPLETED")
        private TransactionStatus status;

        @Schema(description = "Valor transferido", example = "150.00")
        private BigDecimal amount;

        @Schema(description = "Chave PIX do destinatário", example = "destinatario@email.com")
        private String receiverPixKey;

        @Schema(description = "Nome do destinatário", example = "Maria Souza")
        private String receiverName;

        @Schema(description = "Mensagem de status", example = "PIX realizado com sucesso")
        private String message;

        @Schema(description = "Se essa resposta veio de cache de idempotência (retry detectado)")
        private boolean idempotentResponse;

        @Schema(description = "Quando foi processado")
        private LocalDateTime processedAt;

        @Schema(description = "Quando foi criado")
        private LocalDateTime createdAt;
    }

    /**
     * DTO resumido para listagem do histórico de transações.
     */
    @Data
    @Builder
    @Schema(description = "Item do histórico de transações")
    public static class TransactionHistoryItem {

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
