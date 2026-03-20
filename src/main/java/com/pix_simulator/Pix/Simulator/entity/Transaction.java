package com.pix_simulator.Pix.Simulator.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade que representa uma transação PIX.
 *
 * Ciclo de vida de uma transação:
 * PENDING -> PROCESSING -> COMPLETED (sucesso)
 *                       -> FAILED (falha)
 *                       -> CANCELLED (cancelado)
 *
 * A chave de idempotência garante que a mesma transação não seja processada duas vezes,
 * mesmo que o cliente envie a requisição múltiplas vezes (ex: duplo clique, retry de rede).
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                // Índice na chave de idempotência para busca rápida O(log n)
                @Index(name = "idx_transaction_idempotency_key", columnList = "idempotencyKey"),
                // Índice para buscar transações por conta origem (histórico, anomalia)
                @Index(name = "idx_transaction_sender_id", columnList = "senderId"),
                // Índice para buscar recebimentos
                @Index(name = "idx_transaction_receiver_id", columnList = "receiverId")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chave de idempotência gerada pelo cliente ANTES de enviar o PIX.
     * UUID v4: "550e8400-e29b-41d4-a716-446655440000"
     *
     * Se o cliente enviar a mesma chave duas vezes (duplo clique, retry),
     * a segunda requisição retorna o resultado da primeira sem processar novamente.
     *
     * unique = true garante unicidade no banco - segunda linha com mesma chave causa erro de constraint.
     * nullable = false: a chave é obrigatória - sem ela não há garantia de idempotência.
     */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    /**
     * ID da conta que está enviando o PIX.
     * Vem do JWT - não pode ser manipulado pelo cliente.
     */
    @Column(nullable = false)
    private Long senderId;

    /**
     * ID da conta destinatária - encontrada via pixKey.
     */
    @Column(nullable = false)
    private Long receiverId;

    /**
     * Chave PIX do destinatário informada pelo remetente.
     * Mantida para histórico e auditoria.
     */
    @Column(nullable = false)
    private String receiverPixKey;

    /**
     * Valor do PIX. BigDecimal para precisão monetária.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Status atual da transação.
     * @Enumerated(STRING) armazena "PENDING", "COMPLETED" etc. em vez de 0, 1, 2...
     * String é mais legível no banco e resistente a reordenamento do enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /**
     * Descrição opcional informada pelo remetente.
     */
    @Column(length = 500)
    private String description;

    /**
     * Mensagem interna de erro/resultado - para diagnóstico.
     */
    @Column(length = 500)
    private String statusMessage;

    /**
     * Quando a transação foi concluída (COMPLETED ou FAILED).
     * null se ainda estiver em processamento.
     */
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
