package com.pix_simulator.Pix.Simulator.exception;

/**
 * Exceção para erros de regra de negócio.
 * Ex: saldo insuficiente, conta inativa, PIX para si mesmo.
 *
 * Mapeada para HTTP 400 (Bad Request) no GlobalExceptionHandler.
 * RuntimeException = unchecked, não precisa declarar no throws.
 */
public class BusinessException extends RuntimeException {
  public BusinessException(String message) {
    super(message);
  }
}