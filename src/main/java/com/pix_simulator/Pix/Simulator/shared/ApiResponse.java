package com.pix_simulator.Pix.Simulator.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wrapper padrão para todas as respostas da API.
 *
 * Garante um formato consistente em todos os endpoints:
 * {
 *   "success": true,
 *   "message": "PIX realizado com sucesso",
 *   "data": { ... },
 *   "timestamp": "2024-01-15T10:30:00"
 * }
 *
 * - @JsonInclude(NON_NULL) omite campos null do JSON de resposta,
 * mantendo o payload limpo (ex: campo "data" não aparece em erros sem dados).
 *
 * - @param <T> tipo do dado retornado no campo "data"
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** true para respostas de sucesso, false para erros */
    private boolean success;

    /** Mensagem legível sobre o resultado da operação */
    private String message;

    /** Dados retornados pela operação (null em caso de erro) */
    private T data;

    /** Momento em que a resposta foi gerada */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Metodo factory para respostas de sucesso com dados.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Metodo factory para respostas de sucesso sem dados.
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * Metodo factory para respostas de erro.
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
