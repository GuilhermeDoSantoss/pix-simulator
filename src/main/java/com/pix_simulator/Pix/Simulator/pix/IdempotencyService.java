package com.pix_simulator.Pix.Simulator.pix;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Serviço de idempotência para transações PIX.
 *
 * PROBLEMA QUE RESOLVE:
 * Usuário clica "Enviar PIX" → internet cai → não sabe se o PIX foi → clica de novo.
 * Sem idempotência: 2 PIX enviados, dinheiro debitado duas vezes.
 * Com idempotência: 2ª requisição retorna o resultado do 1º PIX sem debitar novamente.
 *
 * COMO FUNCIONA:
 * 1. Frontend gera UUID antes de enviar (ex: "uuid-abc-123")
 * 2. Backend verifica no Redis: "pix:1:uuid-abc-123" existe?
 *    - NÃO: processa o PIX e salva no Redis com TTL de 24h
 *    - SIM: retorna o resultado salvo (idempotent response)
 *
 * ESTRUTURA DA CHAVE NO REDIS:
 * "pix:{accountId}:{idempotencyKey}"
 * Ex: "pix:42:550e8400-e29b-41d4-a716-446655440000"
 *
 * O namespace por accountId evita colisão entre contas diferentes
 * que por acaso gerem o mesmo UUID (improvável mas possível).
 *
 * VALOR ARMAZENADO:
 * O ID da transação como String. Quando detecta duplicata,
 * buscamos a transação pelo ID para retornar o resultado completo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    /**
     * RedisTemplate<String, String> para operações simples chave-valor.
     * Chave: String (namespace + accountId + UUID)
     * Valor: String (ID da transação)
     */
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * TTL em horas lido do application.yml - padrão 24h.
     */
    @Value("${app.idempotency.ttl-hours}")
    private long ttlHours;

    /**
     * Prefixo das chaves no Redis para namespace e fácil identificação.
     */
    private static final String KEY_PREFIX = "pix:";

    /**
     * Verifica se uma chave de idempotência já existe no Redis.
     * Se existir, significa que o PIX já foi processado antes.
     *
     * @param accountId    ID da conta remetente (do JWT)
     * @param idempotencyKey UUID gerado pelo cliente
     * @return true se já processado (é uma requisição duplicada)
     */
    public boolean isDuplicate(Long accountId, String idempotencyKey) {
        String redisKey = buildKey(accountId, idempotencyKey);
        boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));

        if (exists) {
            log.warn("Requisição duplicada detectada - conta: {}, chave: {}", accountId, idempotencyKey);
        }

        return exists;
    }

    /**
     * Salva a chave de idempotência no Redis após processar o PIX com sucesso.
     * O valor salvo é o ID da transação para que possamos recuperar os dados completos.
     *
     * TTL garante que a chave expira após 24h - depois disso o mesmo UUID
     * poderia ser reprocessado (mas em prática nunca acontece pois o frontend
     * gera um novo UUID para cada novo PIX).
     *
     * @param accountId      ID da conta remetente
     * @param idempotencyKey UUID da requisição
     * @param transactionId  ID da transação criada no banco
     */
    public void saveKey(Long accountId, String idempotencyKey, Long transactionId) {
        String redisKey = buildKey(accountId, idempotencyKey);

        // set com TTL: chave, valor, tempo, unidade
        redisTemplate.opsForValue().set(redisKey, transactionId.toString(), ttlHours, TimeUnit.HOURS);

        log.info("Chave de idempotência salva no Redis - chave: {}, transação: {}, TTL: {}h",
                redisKey, transactionId, ttlHours);
    }

    /**
     * Recupera o ID da transação associada a uma chave de idempotência.
     * Usado quando detectamos uma requisição duplicada para retornar
     * o resultado da transação original.
     *
     * @return ID da transação como Long, ou null se não encontrado
     */
    public Long getTransactionId(Long accountId, String idempotencyKey) {
        String redisKey = buildKey(accountId, idempotencyKey);
        String value = redisTemplate.opsForValue().get(redisKey);

        if (value == null) {
            return null;
        }

        return Long.parseLong(value);
    }

    /**
     * Monta a chave composta para o Redis.
     * Formato: "pix:{accountId}:{idempotencyKey}"
     * Exemplo: "pix:42:550e8400-e29b-41d4-a716-446655440000"
     */
    private String buildKey(Long accountId, String idempotencyKey) {
        return KEY_PREFIX + accountId + ":" + idempotencyKey;
    }
}
