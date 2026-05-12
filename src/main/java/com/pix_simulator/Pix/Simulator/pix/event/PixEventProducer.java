package com.pix_simulator.Pix.Simulator.pix.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produtor de eventos Kafka para transações PIX.
 *
 * Responsabilidade: publicar eventos nos tópicos Kafka corretos,
 * encapsulando os detalhes de serialização e chaveamento de partição.
 *
 * CHAVE DE PARTIÇÃO:
 * Usamos o senderId como chave de partição ao publicar.
 * Isso garante que todas as transações do mesmo remetente
 * vão para a mesma partição, preservando a ordem cronológica
 * necessária para o detector de anomalias calcular a média corretamente.
 *
 * FIRE-AND-FORGET:
 * A publicação é assíncrona. O HTTP response é retornado imediatamente
 * após persistir a transação como PENDING, sem aguardar confirmação do Kafka.
 * O callback thenAccept/exceptionally apenas registra logs — não impacta o cliente.
 *
 * Em caso de falha na publicação:
 * - A transação fica como PENDING indefinidamente no banco
 * - Um job de monitoramento pode identificar PENDINGs antigos para reprocessamento
 * - Em produção: usar Kafka Outbox Pattern para garantia de entrega
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PixEventProducer {

    private final KafkaTemplate<String, PixEvent> kafkaTemplate;

    @Value("${app.kafka.topics.pix-created}")
    private String pixCreatedTopic;

    @Value("${app.kafka.topics.pix-completed}")
    private String pixCompletedTopic;

    /**
     * Publica evento quando um PIX é aceito (PENDING criado no banco).
     * Consumido pelo PixEventConsumer para processar assincronamente.
     *
     * @param event dados do PIX recém-criado
     */
    public void publishPixCreated(PixEvent event) {
        publishToTopic(pixCreatedTopic, event);
    }

    /**
     * Publica evento quando um PIX é concluído com sucesso (COMPLETED).
     * Útil para notificações, auditoria e relatórios downstream.
     *
     * @param event dados do PIX concluído
     */
    public void publishPixCompleted(PixEvent event) {
        publishToTopic(pixCompletedTopic, event);
    }

    /**
     * Publica um evento em um tópico Kafka de forma assíncrona.
     *
     * Usa senderId como chave de partição para garantir ordenação
     * por remetente (todas as transações do mesmo sender na mesma partição).
     *
     * @param topic nome do tópico destino
     * @param event payload do evento a ser publicado
     */
    private void publishToTopic(String topic, PixEvent event) {
        String partitionKey = event.getSenderId().toString();

        kafkaTemplate.send(topic, partitionKey, event)
                .thenAccept(result -> log.info(
                        "Evento publicado tópico={} partição={} offset={} transactionId={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getTransactionId()))
                .exceptionally(ex -> {
                    log.error("Falha ao publicar evento tópico={} transactionId={}: {}",
                            topic, event.getTransactionId(), ex.getMessage());
                    return null;
                });
    }
}
