package com.pix_simulator.Pix.Simulator.pix;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Produtor Kafka responsável por publicar eventos de transação PIX.
 *
 * Quando um PIX é concluído, esse producer publica um PixEvent no tópico
 * "pix.events". O Anomaly Detector consome esse tópico de forma assíncrona
 * e decide se o valor é suspeito.
 *
 * KafkaTemplate<String, PixEvent>:
 * - String: tipo da chave (usamos o senderId como chave para garantir
 *   que todas as transações do mesmo usuário vão para a mesma partição,
 *   mantendo a ordem cronológica por usuário)
 * - PixEvent: tipo do valor (serializado em JSON pelo JsonSerializer)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PixEventProducer {

    private final KafkaTemplate<String, PixEvent> kafkaTemplate;

    /**
     * Nome do tópico lido do application.yml.
     */
    @Value("${kafka.topics.pix-events}")
    private String pixEventsTopic;

    /**
     * Publica um evento de PIX concluído no Kafka.
     *
     * O envio é assíncrono - a aplicação não espera a confirmação do Kafka
     * para responder ao usuário. O callback de sucesso/erro é tratado
     * separadamente via CompletableFuture.
     *
     * @param event dados do PIX concluído
     */
    public void publishPixEvent(PixEvent event) {
        log.info("Publicando evento PIX no Kafka - transação: {}, conta: {}, valor: R$ {}",
                event.getTransactionId(), event.getSenderId(), event.getAmount());

        // A chave é o senderId como String - garante ordem por remetente na mesma partição
        String key = event.getSenderId().toString();

        // send() retorna CompletableFuture - permite tratar sucesso e falha de forma não bloqueante
        CompletableFuture<SendResult<String, PixEvent>> future =
                kafkaTemplate.send(pixEventsTopic, key, event);

        // Callback de sucesso: loga confirmação do broker
        future.thenAccept(result -> {
            log.info("Evento PIX confirmado pelo Kafka - tópico: {}, partição: {}, offset: {}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        });

        // Callback de falha: loga o erro mas NÃO interrompe a transação
        // O PIX já foi processado com sucesso - a análise de anomalia é best-effort
        future.exceptionally(ex -> {
            log.error("Falha ao publicar evento PIX no Kafka - transação: {}. Erro: {}",
                    event.getTransactionId(), ex.getMessage());
            return null;
        });
    }
}
