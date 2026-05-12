package com.pix_simulator.Pix.Simulator.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuração dos tópicos Kafka.
 *
 * Responsabilidade: criar os tópicos Kafka na inicialização da aplicação
 * caso ainda não existam. O Spring Kafka usa o KafkaAdmin para isso.
 *
 * TÓPICOS:
 * - pix.created   → publicado pelo PixService após aceitar um PIX (3 partições)
 * - pix.completed → publicado pelo consumer após processar com sucesso (3 partições)
 * - pix.alert     → publicado quando anomalia é detectada (1 partição)
 *
 * POR QUE 3 PARTIÇÕES para pix.created e pix.completed?
 * Permite que até 3 instâncias do consumer processem em paralelo.
 * O senderId é usado como partition key, garantindo que transações do
 * mesmo remetente sempre vão para a mesma partição (preserva ordem).
 *
 * POR QUE replicas=1?
 * Configuração para ambiente de desenvolvimento com 1 broker.
 * EM PRODUÇÃO: usar replicas=3 para tolerância a falhas.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.pix-created}")
    private String topicPixCreated;

    @Value("${app.kafka.topics.pix-completed}")
    private String topicPixCompleted;

    @Value("${app.kafka.topics.pix-alert}")
    private String topicPixAlert;

    @Bean
    public NewTopic pixCreatedTopic() {
        return TopicBuilder.name(topicPixCreated)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic pixCompletedTopic() {
        return TopicBuilder.name(topicPixCompleted)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic pixAlertTopic() {
        return TopicBuilder.name(topicPixAlert)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
