package com.pix_simulator.Pix.Simulator.idempotency.dto;

import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Objeto armazenado no Redis como valor de uma chave de idempotência.
 *
 * Responsabilidade: representar o snapshot da resposta de um PIX no momento
 * em que foi processado pela primeira vez. Este objeto é serializado como JSON
 * e salvo no Redis com TTL de 24 horas.
 *
 * Por que armazenar este objeto e não apenas o ID da transação?
 *
 * Opção A — armazenar apenas o transactionId:
 *   Pro: entrada menor no Redis
 *   Contra: requer query adicional ao banco H2 a cada hit de idempotência.
 *   Se o banco estiver indisponível, o Redis não consegue montar a resposta.
 *
 * Opção B — armazenar a resposta completa (esta implementação):
 *   Pro: resposta instantânea a partir apenas do Redis (zero queries ao banco)
 *   Pro: funciona mesmo se o banco estiver temporariamente indisponível
 *   Contra: entrada ligeiramente maior no Redis (~200-300 bytes por chave)
 *   Para sistemas financeiros, a Opção B é a escolha correta.
 *
 * Implementa Serializable como requisito de algumas configurações de
 * serialização do Spring Data Redis (boa prática defensiva).
 *
 * NoArgsConstructor e @AllArgsConstructor são obrigatórios para
 * que o Jackson consiga deserializar ao ler do Redis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyEntry implements Serializable {

    /** ID da transação no banco H2 — para referência cruzada */
    private Long transactionId;

    /** A mesma chave UUID v4 usada para armazenar este entry */
    private String idempotencyKey;

    /** Status da transação no momento do armazenamento */
    private TransactionStatus status;

    /** Valor da transferência PIX */
    private BigDecimal amount;

    /** Chave PIX do destinatário */
    private String receiverPixKey;

    /** Nome do destinatário — já resolvido para evitar query futura */
    private String receiverName;

    /** Mensagem de status da transação */
    private String statusMessage;

    /** Momento em que foi processado (null se ainda PENDING) */
    private LocalDateTime processedAt;

    /** Momento em que a transação foi criada */
    private LocalDateTime createdAt;
}
