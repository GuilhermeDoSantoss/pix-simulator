package com.pix_simulator.Pix.Simulator.pix.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade JPA que representa uma transação PIX no sistema.
 *
 * Responsabilidade: persistir o registro completo de cada tentativa de
 * transferência PIX, desde o aceite inicial (PENDING) até o resultado final.
 *
 * CICLO DE VIDA (status):
 *   PENDING    → PIX aceito pelo HTTP, chave de idempotência salva, evento Kafka publicado
 *   PROCESSING → Consumer Kafka recebeu e está processando (verificação de anomalia)
 *   COMPLETED  → Saldos atualizados com sucesso — estado final de sucesso
 *   FAILED     → Falha no processamento (saldo insuficiente, conta inativa, etc.)
 *   FLAGGED    → Anomalia detectada — aguarda revisão manual, saldo NÃO movido
 *
 * IDEMPOTÊNCIA:
 * O campo idempotencyKey tem constraint UNIQUE no banco de dados.
 * Isso garante que mesmo em condições de corrida (dois requests simultâneos
 * com a mesma chave), apenas um INSERT será aceito pelo banco.
 * O outro receberá DataIntegrityViolationException — tratado como idempotency hit.
 *
 * Índices: criados em senderId e receiverId para acelerar queries de histórico
 * e médias usadas pela detecção de anomalias.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_tx_idempotency_key", columnList = "idempotencyKey"),
                @Index(name = "idx_tx_sender_id",       columnList = "senderId"),
                @Index(name = "idx_tx_receiver_id",     columnList = "receiverId"),
                @Index(name = "idx_tx_status",          columnList = "status")
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
     * Chave de idempotência fornecida pelo cliente (formato UUID recomendado).
     * UNIQUE constraint previne duplicatas mesmo sob carga concorrente.
     */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    /** ID da conta remetente — referência ao Account.id */
    @Column(nullable = false)
    private Long senderId;

    /** ID da conta destinatária — referência ao Account.id */
    @Column(nullable = false)
    private Long receiverId;

    /** Chave PIX do destinatário — armazenada para rastreabilidade no histórico */
    @Column(nullable = false)
    private String receiverPixKey;

    /**
     * Valor da transferência.
     * BigDecimal com precision=15, scale=2 para precisão monetária.
     * Nunca usar double/float para dinheiro.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Status atual da transação.
     * EnumType.STRING persiste o nome do enum ("PENDING", "COMPLETED", etc.)
     * em vez do índice ordinal — mais seguro e legível no banco.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /** Descrição opcional fornecida pelo remetente (ex: "Almoço") */
    @Column(length = 140)
    private String description;

    /** Mensagem descritiva do status atual — atualizada pelo consumer Kafka */
    @Column(length = 500)
    private String statusMessage;

    /** Momento em que o processamento foi concluído (COMPLETED/FAILED/FLAGGED) */
    private LocalDateTime processedAt;

    /** Momento de criação do registro — imutável */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Momento da última atualização do status */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
