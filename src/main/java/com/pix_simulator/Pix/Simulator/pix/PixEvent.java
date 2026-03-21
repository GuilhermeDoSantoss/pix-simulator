package com.pix_simulator.Pix.Simulator.pix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento publicado no tópico Kafka "pix.events" após cada PIX concluído.
 *
 * Esse evento serve como contrato entre o PIX Module e o Anomaly Detector.
 * O Anomaly Detector consome esse evento e decide se o valor é anômalo.
 *
 * Por que Kafka e não uma chamada direta?
 * - Desacoplamento: o PIX Module não sabe que existe um detector de anomalia
 * - Resiliência: se o detector estiver offline, os eventos ficam no Kafka
 * - Escalabilidade: múltiplos consumers podem processar o mesmo evento
 *
 * - @NoArgsConstructor é necessário para o Jackson deserializar o JSON do Kafka
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixEvent {

    /**
     * ID da transação no banco - permite correlacionar o evento com a transação.
     */
    private Long transactionId;

    /**
     * ID da conta remetente - usado para buscar o histórico de transações.
     */
    private Long senderId;

    /**
     * ID da conta destinatária.
     */
    private Long receiverId;

    /**
     * Valor transferido - o principal dado para análise de anomalia.
     */
    private BigDecimal amount;

    /**
     * Chave PIX do destinatário.
     */
    private String receiverPixKey;

    /**
     * Quando o PIX foi processado.
     */
    private LocalDateTime processedAt;

    /**
     * Tipo do evento para possível extensão futura.
     * Ex: "PIX_COMPLETED", "PIX_FAILED"
     */
    private String eventType;
}