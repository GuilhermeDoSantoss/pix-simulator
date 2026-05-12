package com.pix_simulator.Pix.Simulator.shared.exception;

/**
 * Exceção para violações de regras de negócio.
 *
 * Responsabilidade: sinalizar que uma operação foi rejeitada por
 * uma regra de negócio explícita do sistema.
 *
 * Exemplos de uso:
 * - Saldo insuficiente para PIX
 * - CPF já cadastrado
 * - Conta desativada
 * - Auto-transferência (PIX para si mesmo)
 * - Acesso negado a recurso de outra conta (IDOR)
 *
 * Mapeada pelo GlobalExceptionHandler para HTTP 400 Bad Request.
 * A mensagem é sempre exposta ao cliente — deve ser clara e em PT-BR.
 */
public class BusinessException extends RuntimeException {

  public BusinessException(String message) {
    super(message);
  }
}
