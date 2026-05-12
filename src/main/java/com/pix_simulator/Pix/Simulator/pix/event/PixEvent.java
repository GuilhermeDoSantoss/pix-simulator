package com.pix_simulator.Pix.Simulator.pix.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento publicado no tópico Kafka "pix.created".
 *
 * Responsabilidade: transportar os dados de uma transação PIX do produtor
 * (PixService) para o consumidor (PixEventConsumer) de forma assíncrona.
 *
 * Por que Kafka em vez de chamada direta entre serviços?
 * - Desacoplamento: PixService não conhece AnomalyDetectorService
 * - Resiliência: eventos ficam enfileirados se o consumer estiver fora do ar
 * - Escalabilidade: múltiplos consumers podem processar o mesmo stream
 *
 * CORREÇÃO DE BUG: @Builder(toBuilder = true) é OBRIGATÓRIO.
 * O PixEventConsumer usa event.toBuilder() para criar o evento PIX_COMPLETED.
 * Sem toBuilder=true o Lombok não gera o método e o código falha em runtime
 * com UnsupportedOperationException.
 *
 * NoArgsConstructor é obrigatório para deserialização Jackson no consumer.
 * AllArgsConstructor é obrigatório para o @Builder funcionar.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PixEvent {

    /** ID da transação no banco — chave para busca no consumer */
    private Long transactionId;

    /** ID do remetente — usado como partition key para preservar ordem */
    private Long senderId;

    /** ID do destinatário */
    private Long receiverId;

    /** Valor transferido — BigDecimal obrigatório para precisão monetária */
    private BigDecimal amount;

    /** Chave PIX do destinatário — para rastreabilidade */
    private String receiverPixKey;

    /** Momento de criação do evento */
    private LocalDateTime createdAt;

    /**
     * Tipo do evento para distinguir mensagens no mesmo tópico.
     * Valores: "PIX_CREATED" | "PIX_COMPLETED" | "PIX_FAILED"
     */
    private String eventType;
}
