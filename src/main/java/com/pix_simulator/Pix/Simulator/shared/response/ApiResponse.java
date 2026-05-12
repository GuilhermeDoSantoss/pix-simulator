package com.pix_simulator.Pix.Simulator.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Envelope padrão para todas as respostas da API.
 *
 * Responsabilidade: padronizar o formato JSON de todas as respostas,
 * garantindo consistência para o cliente (frontend ou outro serviço).
 *
 * ESTRUTURA DO JSON:
 * {
 *   "success": true/false,
 *   "message": "Mensagem descritiva",
 *   "data": { ... },          ← omitido quando null (@JsonInclude)
 *   "timestamp": "2024-01-01T12:00:00"
 * }
 *
 * JsonInclude(NON_NULL): campos nulos não aparecem no JSON de resposta.
 * Isso mantém as respostas de erro simples (sem "data: null") e as
 * respostas de sucesso limpas (sem campos irrelevantes).
 *
 * Métodos estáticos de fábrica eliminam boilerplate nos controllers.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** true = operação bem-sucedida | false = erro */
    private boolean success;

    /** Mensagem descritiva do resultado (sempre presente) */
    private String message;

    /** Dado retornado — null em respostas de erro */
    private T data;

    /** Momento em que a resposta foi gerada */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── Métodos de fábrica ────────────────────────────────────────────────

    /** Resposta de sucesso com dado */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /** Resposta de sucesso sem dado (ex: deleções) */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /** Resposta de erro com mensagem (sem dado) */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
