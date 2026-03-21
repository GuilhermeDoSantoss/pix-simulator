package com.pix_simulator.Pix.Simulator.exception;

/**
 * Exceção para recursos não encontrados no banco.
 * Ex: conta com ID inexistente, chave PIX não cadastrada.
 *
 * Mapeada para HTTP 404 (Not Found) no GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
