package com.pix_simulator.Pix.Simulator.shared.exception;

/**
 * Exceção para recursos não encontrados no banco de dados.
 *
 * Responsabilidade: sinalizar que um recurso buscado por ID ou
 * identificador único não existe no sistema.
 *
 * Exemplos de uso:
 * - Conta não encontrada pelo ID
 * - Transação não encontrada pelo ID
 * - Chave PIX não encontrada para roteamento
 *
 * Mapeada pelo GlobalExceptionHandler para HTTP 404 Not Found.
 * A mensagem é sempre exposta ao cliente — deve ser clara e em PT-BR.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
