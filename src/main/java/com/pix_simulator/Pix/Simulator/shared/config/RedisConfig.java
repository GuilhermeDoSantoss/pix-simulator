package com.pix_simulator.Pix.Simulator.shared.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuração do Redis para o sistema de idempotência do PIX.
 *
 * Responsabilidade: configurar a conexão com o Redis e definir a estratégia
 * de serialização das chaves e valores armazenados em cache.
 *
 * Por que Redis para idempotência?
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  BANCO RELACIONAL (antes)    │  REDIS (agora)                        │
 * ├──────────────────────────────┼───────────────────────────────────────┤
 * │  Query SQL por UNIQUE key    │  Lookup O(1) por hash                 │
 * │  Sem TTL nativo              │  TTL automático (chave expira sozinha) │
 * │  Acessa disco                │  100% in-memory                       │
 * │  ~5-20ms por lookup          │  ~0.1-0.5ms por lookup                │
 * │  Sem suporte a lock atômico  │  SET NX (atomic check-and-set)        │
 * └──────────────────────────────┴───────────────────────────────────────┘
 *
 * ESTRATÉGIA DE SERIALIZAÇÃO:
 * - Chave (key):   StringRedisSerializer → armazena como String legível no Redis
 * - Valor (value): Jackson2JsonRedisSerializer → armazena como JSON
 *
 * O Jackson é configurado com:
 * - JavaTimeModule: suporte a LocalDateTime, Instant, etc.
 * - WRITE_DATES_AS_TIMESTAMPS=false: datas como ISO-8601 (legível)
 * - activateDefaultTyping: inclui info de tipo no JSON para deserialização correta
 *
 * ATENÇÃO EM PRODUÇÃO:
 * - Configurar Redis com autenticação (requirepass)
 * - Usar Redis Sentinel ou Redis Cluster para alta disponibilidade
 * - Configurar maxmemory-policy = allkeys-lru
 * - Separar instâncias de Redis por domínio (idempotência vs cache genérico)
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Factory de conexão com o Redis usando Lettuce (cliente reativo padrão do Spring).
     *
     * Lettuce vs Jedis:
     * - Lettuce: thread-safe, conexões compartilhadas, suporte reativo (Webflux)
     * - Jedis: pool de conexões, mais simples, síncrono apenas
     * Para Spring Boot 3, Lettuce é o padrão recomendado.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        return new LettuceConnectionFactory(config);
    }

    /**
     * Template principal para operações no Redis.
     *
     * RedisTemplate<String, Object> permite:
     * - Chave String: formato "pix:idempotency:{uuid}" (legível no Redis CLI)
     * - Valor Object: qualquer objeto Java serializado como JSON
     *
     * Operações disponíveis via template:
     * - opsForValue(): GET/SET/SETEX simples
     * - opsForHash(): hash maps
     * - opsForSet(): conjuntos
     * A idempotência usa opsForValue() com TTL.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Serializer para chaves: String simples e legível
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Serializer para valores: JSON via Jackson
        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(buildObjectMapper(), Object.class);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * ObjectMapper configurado para serialização correta no Redis.
     *
     * activateDefaultTyping: inclui o nome da classe no JSON serializado.
     * Isso é necessário para que o Jackson saiba para qual classe deserializar
     * ao ler o valor de volta do Redis.
     *
     * Exemplo de JSON no Redis sem tipo:
     *   {"transactionId": 1, "status": "PENDING"}
     *
     * Exemplo com tipo (activateDefaultTyping):
     *   ["com.pix.pix.dto.PixDTO$PixResponse", {"transactionId": 1, "status": "PENDING"}]
     */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Suporte para tipos Java 8 de data/hora (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Serializa LocalDateTime como "2024-01-01T12:00:00" em vez de array de números
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Acesso a todos os campos (incluindo privados sem getters)
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // Inclui informação de tipo para deserialização correta
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        return mapper;
    }
}
