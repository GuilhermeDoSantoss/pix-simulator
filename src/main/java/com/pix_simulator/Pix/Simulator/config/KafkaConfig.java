package com.pix_simulator.Pix.Simulator.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuração dos tópicos Kafka.
 *
 * Os beans NewTopic fazem o Spring criar automaticamente os tópicos no Kafka
 * quando a aplicação sobe, se ainda não existirem.
 *
 * Parâmetros dos tópicos:
 * - partitions: número de partições - permite processamento paralelo
 *   (mais partições = mais consumers em paralelo)
 * - replicas: número de réplicas para tolerância a falhas
 *   (em desenvolvimento: 1 replica pois é um único broker)
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.pix-events}")
    private String pixEventsTopic;

    @Value("${kafka.topics.pix-alerts}")
    private String pixAlertsTopic;

    /**
     * Tópico principal de eventos PIX.
     * Publicado pelo PixService após cada transação concluída.
     * Consumido pelo PixEventConsumer (Anomaly Detector).
     *
     * 3 partições: permite 3 consumers em paralelo no grupo "pix-anomaly-detector".
     * Em produção: usar mais partições de acordo com o volume.
     */
    @Bean
    public NewTopic pixEventsTopic() {
        return TopicBuilder.name(pixEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Tópico de alertas de anomalia.
     * Publicado pelo AnomalyDetectorService quando detecta transação suspeita.
     * Pode ser consumido por sistemas de notificação, dashboards, etc.
     */
    @Bean
    public NewTopic pixAlertsTopic() {
        return TopicBuilder.name(pixAlertsTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
