package com.pix_simulator.Pix.Simulator.entity;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository das transações PIX.
 *
 * Além do CRUD padrão do JpaRepository, contém queries específicas
 * para o detector de anomalia - que precisa do histórico de transações
 * de uma conta para calcular média e desvio padrão.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Busca transação pela chave de idempotência.
     * Usado para verificar se uma transação já foi processada antes.
     * O índice no campo garante que essa busca seja O(log n).
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Retorna todas as transações de uma conta como remetente,
     * ordenadas da mais recente para a mais antiga.
     */
    List<Transaction> findBySenderIdOrderByCreatedAtDesc(Long senderId);

    /**
     * Retorna transações concluídas de uma conta nos últimos N dias.
     * Usado pelo detector de anomalia para calcular o perfil histórico.
     *
     * @Query com JPQL (não SQL nativo) - portável entre bancos.
     */
    @Query("SELECT t FROM Transaction t " +
            "WHERE t.senderId = :senderId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.createdAt >= :since " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findCompletedTransactionsSince(
            @Param("senderId") Long senderId,
            @Param("since") LocalDateTime since);

    /**
     * Calcula a média dos valores de PIX de uma conta.
     * AVG retorna Double (pode ser null se não houver registros).
     *
     * Usado pelo detector de anomalia como linha de base.
     */
    @Query("SELECT AVG(t.amount) FROM Transaction t " +
            "WHERE t.senderId = :senderId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.createdAt >= :since")
    Optional<BigDecimal> findAverageAmountBySenderSince(
            @Param("senderId") Long senderId,
            @Param("since") LocalDateTime since);

    /**
     * Conta o número de transações completadas de uma conta no período.
     * Usado para verificar se há histórico suficiente para análise de anomalia.
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.senderId = :senderId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.createdAt >= :since")
    long countCompletedTransactionsSince(
            @Param("senderId") Long senderId,
            @Param("since") LocalDateTime since);

    /**
     * Busca transações de uma conta em um intervalo de status.
     * Útil para exibir histórico com filtro por status.
     */
    List<Transaction> findBySenderIdAndStatusOrderByCreatedAtDesc(
            Long senderId, TransactionStatus status);
}
