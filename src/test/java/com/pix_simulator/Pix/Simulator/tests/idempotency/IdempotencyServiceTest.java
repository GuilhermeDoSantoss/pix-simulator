package com.pix_simulator.Pix.Simulator.tests.idempotency;

import com.pix_simulator.Pix.Simulator.idempotency.dto.IdempotencyEntry;
import com.pix_simulator.Pix.Simulator.idempotency.service.IdempotencyService;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do IdempotencyService.
 *
 * Estratégia: RedisTemplate mockado — testa a lógica do serviço
 * sem necessitar de um servidor Redis real.
 *
 * Cobertura:
 * - Validação de UUID v4 (formatos válidos e inválidos)
 * - Cache hit: retorna entry quando chave existe no Redis
 * - Cache miss: retorna Optional.empty() quando chave não existe
 * - storeResponse: armazena com TTL correto
 * - acquireLock: lock obtido com sucesso (SETNX true)
 * - acquireLock: lock negado (SETNX false — já existe)
 * - releaseLock: deleta a chave de lock
 * - Falhas no Redis: degradação graciosa (não lança exceção)
 * - generateUuidV4: gera UUID no formato correto
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService — Testes Unitários")
class IdempotencyServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks private IdempotencyService idempotencyService;

    private static final String VALID_UUID_V4 = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(idempotencyService, "ttlHours", 24L);
        ReflectionTestUtils.setField(idempotencyService, "lockSeconds", 30L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─────────────────────────────────────────────────────────────────────
    // VALIDAÇÃO UUID v4
    // ─────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @DisplayName("Deve aceitar UUIDs v4 válidos")
    @ValueSource(strings = {
            "550e8400-e29b-41d4-a716-446655440000",  // v4 clássico
            "6ba7b810-9dad-41d1-80b4-00c04fd430c8",  // v4 com variante 8
            "6ba7b812-9dad-41d1-80b4-00c04fd430c8",  // v4 com variante 8
            "6ba7b813-9dad-41d1-b0b4-00c04fd430c8",  // v4 com variante b
            "6ba7b814-9dad-41d1-a0b4-00c04fd430c8",  // v4 com variante a
            "FFFFFFFF-FFFF-4FFF-9FFF-FFFFFFFFFFFF",  // letras maiúsculas
    })
    void isValidUuidV4_deveAceitarFormatosValidos(String uuid) {
        assertThat(idempotencyService.isValidUuidV4(uuid)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("Deve rejeitar strings que não são UUID v4")
    @ValueSource(strings = {
            "",                                             // vazio
            "nao-e-uuid",                                   // texto livre
            "550e8400-e29b-31d4-a716-446655440000",         // versão 3, não 4
            "550e8400-e29b-41d4-c716-446655440000",         // variante inválida (c)
            "550e8400e29b41d4a716446655440000",             // sem hífens
            "550e8400-e29b-41d4-a716",                      // truncado
            "550e8400-e29b-41d4-a716-4466554400001",        // longo demais
            "gggggggg-gggg-4ggg-aggg-gggggggggggg",         // hex inválido (g)
    })
    void isValidUuidV4_deveRejeitarFormatosInvalidos(String invalid) {
        assertThat(idempotencyService.isValidUuidV4(invalid)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar null")
    void isValidUuidV4_deveRejeitarNull() {
        assertThat(idempotencyService.isValidUuidV4(null)).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CACHE HIT / MISS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findCachedResponse deve retornar entry quando chave existe no Redis")
    void findCachedResponse_devRetornarEntryQuandoExiste() {
        IdempotencyEntry entry = buildEntry();
        when(valueOps.get("pix:idempotency:" + VALID_UUID_V4)).thenReturn(entry);

        Optional<IdempotencyEntry> result = idempotencyService.findCachedResponse(VALID_UUID_V4);

        assertThat(result).isPresent();
        assertThat(result.get().getTransactionId()).isEqualTo(1L);
        assertThat(result.get().getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("findCachedResponse deve retornar Optional.empty() quando chave não existe")
    void findCachedResponse_deveRetornarVazioQuandoNaoExiste() {
        when(valueOps.get(anyString())).thenReturn(null);

        Optional<IdempotencyEntry> result = idempotencyService.findCachedResponse(VALID_UUID_V4);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findCachedResponse deve retornar Optional.empty() quando Redis falha (degradação graciosa)")
    void findCachedResponse_deveRetornarVazioQuandoRedisFalha() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis indisponível"));

        // Não deve lançar exceção — degradação graciosa
        Optional<IdempotencyEntry> result = idempotencyService.findCachedResponse(VALID_UUID_V4);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────
    // ARMAZENAMENTO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("storeResponse deve armazenar entry com TTL de 24h")
    void storeResponse_deveArmazenarComTtlCorreto() {
        IdempotencyEntry entry = buildEntry();

        idempotencyService.storeResponse(VALID_UUID_V4, entry);

        verify(valueOps).set(
                eq("pix:idempotency:" + VALID_UUID_V4),
                eq(entry),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("storeResponse não deve lançar exceção quando Redis falha")
    void storeResponse_naoDeveLancarExcecaoQuandoRedisFalha() {
        doThrow(new RuntimeException("Redis indisponível"))
                .when(valueOps).set(anyString(), any(), any(Duration.class));

        // Não deve lançar — a transação já foi salva no banco
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> idempotencyService.storeResponse(VALID_UUID_V4, buildEntry()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOCK DISTRIBUÍDO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("acquireLock deve retornar true quando lock é obtido (SETNX sucesso)")
    void acquireLock_deveRetornarTrueQuandoLockObtido() {
        when(valueOps.setIfAbsent(
                eq("pix:lock:" + VALID_UUID_V4),
                eq("LOCKED"),
                eq(Duration.ofSeconds(30))
        )).thenReturn(true);

        assertThat(idempotencyService.acquireLock(VALID_UUID_V4)).isTrue();
    }

    @Test
    @DisplayName("acquireLock deve retornar false quando lock já existe (outro thread processando)")
    void acquireLock_deveRetornarFalseQuandoLockJaExiste() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        assertThat(idempotencyService.acquireLock(VALID_UUID_V4)).isFalse();
    }

    @Test
    @DisplayName("acquireLock deve retornar true quando Redis falha (degradação graciosa)")
    void acquireLock_deveRetornarTrueQuandoRedisFalha() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis indisponível"));

        // Deixa processar em caso de falha no Redis (melhor do que bloquear tudo)
        assertThat(idempotencyService.acquireLock(VALID_UUID_V4)).isTrue();
    }

    @Test
    @DisplayName("releaseLock deve deletar a chave de lock no Redis")
    void releaseLock_deveDeletarChaveDeLock() {
        idempotencyService.releaseLock(VALID_UUID_V4);

        verify(redisTemplate).delete("pix:lock:" + VALID_UUID_V4);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GERAÇÃO DE UUID
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateUuidV4 deve gerar UUID no formato v4 correto")
    void generateUuidV4_deveGerarFormatoCorreto() {
        String uuid = idempotencyService.generateUuidV4();

        assertThat(uuid).isNotNull();
        assertThat(idempotencyService.isValidUuidV4(uuid)).isTrue();
        // Posição 15 deve ser "4" (versão 4)
        assertThat(uuid.charAt(14)).isEqualTo('4');
    }

    @Test
    @DisplayName("generateUuidV4 deve gerar UUIDs únicos a cada chamada")
    void generateUuidV4_deveGerarValoresUnicos() {
        String uuid1 = idempotencyService.generateUuidV4();
        String uuid2 = idempotencyService.generateUuidV4();

        assertThat(uuid1).isNotEqualTo(uuid2);
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────

    private IdempotencyEntry buildEntry() {
        return IdempotencyEntry.builder()
                .transactionId(1L)
                .idempotencyKey(VALID_UUID_V4)
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("150.00"))
                .receiverPixKey("maria@test.com")
                .receiverName("Maria")
                .build();
    }
}
