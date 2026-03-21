package com.pix_simulator.Pix.Simulator.tests;


import com.pix_simulator.Pix.Simulator.pix.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do IdempotencyService.
 *
 * Testa a lógica de verificação e armazenamento de chaves de idempotência no Redis.
 * O RedisTemplate é mockado - não precisamos de um Redis real rodando.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService - Testes unitários")
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Mock do ValueOperations - retornado por redisTemplate.opsForValue()
     * Precisamos mockar este objeto pois IdempotencyService chama
     * redisTemplate.opsForValue().set(...) e .get(...)
     */
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        // Injeta o valor do TTL que normalmente viria do application.yml via @Value
        ReflectionTestUtils.setField(idempotencyService, "ttlHours", 24L);

        // Configura o mock para retornar o valueOperations quando chamado
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("isDuplicate: chave existente deve retornar true")
    void isDuplicate_existingKey_shouldReturnTrue() {
        // ARRANGE - Redis tem a chave
        when(redisTemplate.hasKey("pix:1:test-key-123")).thenReturn(true);

        // ACT
        boolean result = idempotencyService.isDuplicate(1L, "test-key-123");

        // ASSERT
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey("pix:1:test-key-123");
    }

    @Test
    @DisplayName("isDuplicate: chave inexistente deve retornar false")
    void isDuplicate_nonExistingKey_shouldReturnFalse() {
        // ARRANGE - Redis NÃO tem a chave
        when(redisTemplate.hasKey("pix:1:novo-uuid")).thenReturn(false);

        // ACT
        boolean result = idempotencyService.isDuplicate(1L, "novo-uuid");

        // ASSERT
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("saveKey: deve salvar chave no Redis com TTL correto")
    void saveKey_shouldSaveWithCorrectTTL() {
        // ACT
        idempotencyService.saveKey(1L, "uuid-abc", 42L);

        // ASSERT - verifica que o Redis foi chamado com os parâmetros corretos
        verify(valueOperations).set(
                eq("pix:1:uuid-abc"),   // chave no formato correto
                eq("42"),               // ID da transação como String
                eq(24L),                // TTL
                eq(TimeUnit.HOURS)      // unidade do TTL
        );
    }

    @Test
    @DisplayName("getTransactionId: deve retornar o ID da transação armazenado")
    void getTransactionId_existingKey_shouldReturnTransactionId() {
        // ARRANGE
        when(valueOperations.get("pix:5:uuid-xyz")).thenReturn("137");

        // ACT
        Long transactionId = idempotencyService.getTransactionId(5L, "uuid-xyz");

        // ASSERT
        assertThat(transactionId).isEqualTo(137L);
    }

    @Test
    @DisplayName("getTransactionId: chave inexistente deve retornar null")
    void getTransactionId_missingKey_shouldReturnNull() {
        // ARRANGE
        when(valueOperations.get(anyString())).thenReturn(null);

        // ACT
        Long result = idempotencyService.getTransactionId(1L, "nao-existe");

        // ASSERT
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Namespace: contas diferentes com mesmo UUID não devem colidir")
    void saveKey_differentAccounts_shouldUseDifferentKeys() {
        // ACT - duas contas salvam com o mesmo UUID
        idempotencyService.saveKey(1L, "mesmo-uuid", 10L);
        idempotencyService.saveKey(2L, "mesmo-uuid", 20L);

        // ASSERT - chaves devem ser diferentes por conta do accountId no namespace
        verify(valueOperations).set(eq("pix:1:mesmo-uuid"), eq("10"), anyLong(), any());
        verify(valueOperations).set(eq("pix:2:mesmo-uuid"), eq("20"), anyLong(), any());
    }
}
