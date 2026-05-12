package com.pix_simulator.Pix.Simulator.idempotency.service;

import com.pix_simulator.Pix.Simulator.idempotency.dto.IdempotencyEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Serviço central de idempotência usando Redis.
 *
 * Responsabilidade: garantir que uma requisição PIX com o mesmo UUID
 * seja processada exatamente uma vez, independente de quantas vezes
 * o cliente reenvie a mesma requisição.
 *
 *
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  IMPLEMENTAÇÃO (Redis como camada principal)                    ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║                                                                      ║
 * ║  FLUXO COMPLETO:                                                     ║
 * ║                                                                      ║
 * ║  Cliente → POST /api/pix/send                                        ║
 * ║       │                                                              ║
 * ║       ├─ idempotencyKey presente no body (UUID v4)                  ║
 * ║       │                                                              ║
 * ║       ▼                                                              ║
 * ║  IdempotencyService.checkAndLock(key)                               ║
 * ║       │                                                              ║
 * ║       ├─ Redis GET "pix:idempotency:{key}"                          ║
 * ║       │       │                                                      ║
 * ║       │       ├─ EXISTS → retorna IdempotencyEntry do cache         ║
 * ║       │       │           sem tocar banco, sem Kafka               ║
 * ║       │       │                                                      ║
 * ║       │       └─ NOT EXISTS → SETNX "pix:lock:{key}" TTL=30s       ║
 * ║       │               │                                              ║
 * ║       │               ├─ Lock obtido → processa o PIX              ║
 * ║       │               │   → salva no banco H2                       ║
 * ║       │               │   → armazena no Redis com TTL=24h          ║
 * ║       │               │   → libera lock                             ║
 * ║       │               │                                              ║
 * ║       │               └─ Lock negado (outro thread processando)     ║
 * ║       │                   → retorna 409 Conflict                    ║
 * ║       │                                                              ║
 * ║  Banco relacional mantém UNIQUE constraint como barreira final.     ║
 * ║  Redis é a camada rápida; banco é o fallback de segurança.         ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * POR QUE UUID v4 É O FORMATO CORRETO?
 * - UUID v4: gerado aleatoriamente com 122 bits de entropia
 * - Probabilidade de colisão: 1 em 2^61 ≈ impossível na prática
 * - Não contém informação de máquina/tempo (ao contrário do v1)
 * - Formato universalmente reconhecido: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
 * - O "4" na posição 13 e bits específicos na posição 17 identificam v4
 *
 * TTL DE 24 HORAS:
 * - Janela de retentativa razoável para falhas de rede no cliente
 * - Após 24h, assume-se que o cliente não vai mais retentar
 * - Em produção: considerar TTL configurável por tipo de operação
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Prefixo das chaves de idempotência no Redis
    // Formato final: "pix:idempotency:550e8400-e29b-41d4-a716-446655440000"
    private static final String IDEMPOTENCY_PREFIX = "pix:idempotency:";

    // Prefixo do lock distribuído (impede processamento concorrente da mesma chave)
    private static final String LOCK_PREFIX = "pix:lock:";

    // Regex para validação rigorosa de UUID v4
    // Posição 13 deve ser "4" (versão) e posição 17 deve ser [89ab] (variante)
    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${app.idempotency.ttl-hours:24}")
    private long ttlHours;

    @Value("${app.idempotency.lock-seconds:30}")
    private long lockSeconds;

    /**
     * Verifica se uma chave de idempotência já foi processada.
     *
     * Operação O(1) — leitura direta do Redis sem query SQL.
     *
     * @param idempotencyKey UUID v4 fornecido pelo cliente
     * @return Optional com o entry cacheado, ou Optional.empty() se não existe
     */
    public Optional<IdempotencyEntry> findCachedResponse(String idempotencyKey) {
        String redisKey = buildIdempotencyKey(idempotencyKey);

        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached instanceof IdempotencyEntry entry) {
                log.info("Cache hit idempotência — chave={} transactionId={}",
                        idempotencyKey, entry.getTransactionId());
                return Optional.of(entry);
            }
            return Optional.empty();
        } catch (Exception e) {
            // Falha no Redis não deve bloquear o processamento do PIX
            // Princípio: Redis é camada de otimização, banco é fonte da verdade
            log.warn("Falha ao consultar Redis para chave={}: {}. Continuando sem cache.",
                    idempotencyKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Armazena o resultado de um PIX no Redis com TTL configurado.
     *
     * Chamado pelo PixService após salvar a transação no banco H2.
     * Após este ponto, qualquer retentativa com a mesma chave receberá
     * este entry diretamente do Redis, sem tocar o banco.
     *
     * @param idempotencyKey UUID v4 — deve ser o mesmo da requisição original
     * @param entry          dados da transação a serem cacheados
     */
    public void storeResponse(String idempotencyKey, IdempotencyEntry entry) {
        String redisKey = buildIdempotencyKey(idempotencyKey);
        Duration ttl = Duration.ofHours(ttlHours);

        try {
            redisTemplate.opsForValue().set(redisKey, entry, ttl);
            log.info("Idempotência armazenada no Redis — chave={} transactionId={} TTL={}h",
                    idempotencyKey, entry.getTransactionId(), ttlHours);
        } catch (Exception e) {
            // Falha ao armazenar não cancela a transação — já foi salva no banco
            // Próxima requisição com mesma chave irá ao banco (fallback seguro)
            log.error("Falha ao armazenar idempotência no Redis para chave={}: {}",
                    idempotencyKey, e.getMessage());
        }
    }

    /**
     * Tenta adquirir um lock distribuído para a chave de idempotência.
     *
     * Usa SETNX (SET if Not eXists) — operação atômica do Redis.
     * Garante que apenas um thread/instância processe a mesma chave simultaneamente.
     *
     * Por que isso é necessário?
     * Sem lock: dois requests simultâneos com a mesma chave poderiam ambos passar
     * pela verificação "chave não existe" antes que um deles armazenasse o resultado.
     * Com lock: o segundo request espera ou retorna 409, garantindo unicidade.
     *
     * TTL do lock (padrão 30s): proteção contra lock eterno se a aplicação crashar
     * no meio do processamento.
     *
     * @param idempotencyKey UUID v4 a ser bloqueado
     * @return true se o lock foi adquirido com sucesso, false se já existe
     */
    public boolean acquireLock(String idempotencyKey) {
        String lockKey = buildLockKey(idempotencyKey);
        Duration lockTtl = Duration.ofSeconds(lockSeconds);

        try {
            // setIfAbsent = SETNX atômico — retorna true apenas se a chave não existia
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "LOCKED", lockTtl);
            boolean success = Boolean.TRUE.equals(acquired);
            if (success) {
                log.debug("Lock adquirido para chave={}", idempotencyKey);
            } else {
                log.warn("Lock negado para chave={} — processamento em andamento", idempotencyKey);
            }
            return success;
        } catch (Exception e) {
            // Se Redis falhar ao tentar o lock, deixa processar (degradação graciosa)
            log.warn("Falha ao adquirir lock Redis para chave={}: {}. Processando sem lock.",
                    idempotencyKey, e.getMessage());
            return true;
        }
    }

    /**
     * Libera o lock distribuído após o processamento ser concluído.
     *
     * Deve ser chamado no bloco finally para garantir liberação mesmo em erros.
     *
     * @param idempotencyKey UUID v4 cujo lock deve ser liberado
     */
    public void releaseLock(String idempotencyKey) {
        String lockKey = buildLockKey(idempotencyKey);
        try {
            redisTemplate.delete(lockKey);
            log.debug("Lock liberado para chave={}", idempotencyKey);
        } catch (Exception e) {
            // Lock vai expirar automaticamente pelo TTL — não é crítico
            log.warn("Falha ao liberar lock Redis para chave={}: {}", idempotencyKey, e.getMessage());
        }
    }

    /**
     * Valida se a chave fornecida é um UUID v4 válido.
     *
     * Por que validar no servidor e não apenas no cliente?
     * - O cliente pode enviar qualquer string como chave
     * - Uma chave com baixa entropia (ex: "1", "teste") aumenta risco de colisão
     * - UUID v4 garante 122 bits de aleatoriedade — praticamente impossível de colidir
     *
     * Regex: ^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$
     * - Posição 13: deve ser "4" (versão 4)
     * - Posição 17: deve ser [89ab] (variante RFC 4122)
     *
     * param key string a ser validada
     * return true se é um UUID v4 válido
     */
    public boolean isValidUuidV4(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return UUID_V4_PATTERN.matcher(key).matches();
    }

    /**
     * Gera um novo UUID v4 — usado quando o backend precisa criar a chave.
     * Exposto como utilitário para controllers e testes.
     *
     * @return UUID v4 no formato padrão com hífens
     */
    public String generateUuidV4() {
        return UUID.randomUUID().toString();
    }

    /**
     * Monta a chave Redis para o entry de idempotência.
     * Formato: "pix:idempotency:{uuid}"
     *
     * O namespace "pix:idempotency:" isola estas chaves de outros dados no Redis.
     * Útil quando o mesmo Redis é compartilhado entre múltiplos serviços.
     */
    private String buildIdempotencyKey(String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + idempotencyKey;
    }

    /**
     * Monta a chave Redis para o lock distribuído.
     * Formato: "pix:lock:{uuid}"
     */
    private String buildLockKey(String idempotencyKey) {
        return LOCK_PREFIX + idempotencyKey;
    }
}
