package com.pix_simulator.Pix.Simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuração do RedisTemplate para operações chave-valor com String.
 *
 * Por padrão o Spring configura o RedisTemplate com serialização Java binária,
 * o que torna as chaves ilegíveis no Redis CLI.
 *
 * Aqui configuramos StringRedisSerializer para que as chaves e valores
 * sejam armazenados como texto legível:
 * - Chave: "pix:42:550e8400-e29b-41d4-a716-446655440000"
 * - Valor: "137" (ID da transação)
 *
 * Isso facilita muito o diagnóstico e debug.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Serializa chaves e valores como String simples (UTF-8)
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
