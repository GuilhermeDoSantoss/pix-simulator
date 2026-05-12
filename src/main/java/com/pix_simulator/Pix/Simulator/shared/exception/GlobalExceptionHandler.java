package com.pix_simulator.Pix.Simulator.shared.exception;

import com.pix_simulator.Pix.Simulator.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tratador global de exceções da API.
 *
 * Responsabilidade: converter exceções em respostas JSON padronizadas
 * (ApiResponse) com códigos HTTP apropriados.
 *
 * Princípio de segurança aplicado:
 * - Erros de negócio mostram a mensagem real (útil para o cliente)
 * - Erros de autenticação retornam mensagem genérica (não revela detalhes)
 * - Erros inesperados logam o stacktrace completo mas retornam mensagem genérica
 *   (não vaza informações internas da aplicação ao cliente)
 *
 * Hierarquia de handlers (Spring aplica o mais específico primeiro):
 *   BusinessException         → 400 Bad Request
 *   ResourceNotFoundException → 404 Not Found
 *   MethodArgumentNotValid    → 422 Unprocessable Entity (com mapa de campos)
 *   BadCredentialsException   → 401 Unauthorized (mensagem genérica)
 *   DisabledException         → 401 Unauthorized (conta desativada)
 *   Exception (genérico)      → 500 Internal Server Error
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Violação de regra de negócio — ex: saldo insuficiente, CPF duplicado.
     * Mensagem específica é segura para expor ao cliente.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Regra de negócio violada: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Recurso não encontrado — ex: conta ou transação inexistente.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Erro de validação de campos — @Valid no controller rejeitou o request.
     * Retorna mapa com {campo → mensagem de erro} para o frontend exibir.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Valor inválido",
                        // Em caso de campo duplicado, mantém o primeiro erro
                        (existing, duplicate) -> existing
                ));

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Erro de validação")
                        .data(fieldErrors)
                        .build());
    }

    /**
     * Credenciais inválidas no login.
     * Mensagem deliberadamente genérica — não revela se o CPF existe.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Tentativa de login com credenciais inválidas");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("CPF ou senha inválidos"));
    }

    /**
     * Tentativa de login com conta desativada.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Conta desativada. Entre em contato com o suporte."));
    }

    /**
     * Fallback para qualquer exceção não tratada acima.
     * Loga o stacktrace completo internamente mas não expõe ao cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Erro interno do servidor. Tente novamente."));
    }
}
