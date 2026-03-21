package com.pix_simulator.Pix.Simulator.exception;

import com.pix_simulator.Pix.Simulator.shared.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler global de exceções da aplicação.
 *
 * @RestControllerAdvice intercepta exceções lançadas em qualquer controller
 * e as converte em respostas HTTP com o formato ApiResponse padronizado.
 *
 * Sem esse handler, o Spring retornaria o stack trace completo em HTML/texto,
 * o que é inseguro (expõe detalhes internos) e inconsistente com o formato da API.
 *
 * Hierarquia de tratamento:
 * - BusinessException (400): regras de negócio violadas
 * - ResourceNotFoundException (404): recurso não encontrado
 * - MethodArgumentNotValidException (422): validação de campos falhou
 * - BadCredentialsException (401): CPF ou senha incorretos
 * - Exception (500): qualquer erro não previsto
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Trata erros de regra de negócio.
     * Ex: saldo insuficiente, conta inativa, PIX duplicado.
     * HTTP 400: a requisição está errada do ponto de vista de negócio.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Erro de negócio: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Trata recursos não encontrados.
     * Ex: conta com ID inexistente, chave PIX não cadastrada.
     * HTTP 404: o recurso solicitado não existe.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Trata falhas de validação nos DTOs (@Valid).
     * Retorna um mapa com todos os campos inválidos e seus erros.
     *
     * Ex: {"cpf": "CPF deve conter 11 dígitos", "amount": "Valor mínimo é R$ 0,01"}
     *
     * HTTP 422: a sintaxe está certa mas o conteúdo falhou na validação semântica.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Agrupa todos os erros de validação por campo
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
        });

        log.warn("Erro de validação: {}", errors);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Erro de validação nos campos")
                        .data(errors)
                        .build());
    }

    /**
     * Trata falhas de autenticação (login com credenciais erradas).
     * HTTP 401: não autenticado.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Tentativa de login com credenciais inválidas");
        // Mensagem genérica - não revela se é CPF ou senha que está errado
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("CPF ou senha incorretos"));
    }

    /**
     * Trata tentativas de login com conta desabilitada.
     * HTTP 401: conta inativa.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabledException(DisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Conta desativada. Entre em contato com o suporte."));
    }

    /**
     * Trata qualquer erro não previsto (safety net).
     * HTTP 500: erro interno do servidor.
     *
     * Loga o stack trace completo para diagnóstico mas retorna mensagem genérica
     * para o cliente - não expõe detalhes internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Erro interno não esperado: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Erro interno do servidor. Tente novamente mais tarde."));
    }
}
