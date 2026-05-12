package com.pix_simulator.Pix.Simulator.pix.repository;

import com.pix_simulator.Pix.Simulator.pix.entity.Transaction;
import com.pix_simulator.Pix.Simulator.pix.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados para transações PIX.
 *
 * Responsabilidade: todos os acessos ao banco relacionados à entidade
 * Transaction passam por aqui — nenhuma lógica de negócio.
 *
 * CORREÇÃO DE BUG nas queries JPQL:
 * As queries originais usavam string literal 'COMPLETED' em vez do enum.
 * Isso causa falha silenciosa se o enum mudar de nome.
 * Corrigido para referenciar o enum diretamente via parâmetro tipado,
 * garantindo type-safety em tempo de compilação.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Busca transação pela chave de idempotência.
     * Usada como primeira verificação no fluxo PIX para evitar duplicatas.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Histórico de transações enviadas por uma conta, mais recente primeiro.
     * Usado para exibir o extrato do usuário autenticado.
     */
    List<Transaction> findBySenderIdOrderByCreatedAtDesc(Long senderId);

    /**
     * Calcula a média dos valores de transações COMPLETED nos últimos N dias.
     *
     * CORREÇÃO: parâmetro :status (enum tipado) em vez de string literal 'COMPLETED'.
     * Isso garante que a query continue funcionando se o enum for renomeado.
     *
     * Usado pelo detector de anomalias para estabelecer a linha de base histórica.
     */
    @Query("SELECT AVG(t.amount) FROM Transaction t " +
            "WHERE t.senderId = :senderId " +
            "AND t.status = :status " +
            "AND t.createdAt >= :since")
    Optional<BigDecimal> avgAmountBySenderSince(
            @Param("senderId") Long senderId,
            @Param("since") LocalDateTime since,
            @Param("status") TransactionStatus status);

    /**
     * Conta transações COMPLETED em um período.
     * Usado para verificar se há histórico suficiente antes da detecção de anomalia.
     *
     * CORREÇÃO: parâmetro :status tipado em vez de string literal.
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.senderId = :senderId " +
            "AND t.status = :status " +
            "AND t.createdAt >= :since")
    long countByStatusSince(
            @Param("senderId") Long senderId,
            @Param("since") LocalDateTime since,
            @Param("status") TransactionStatus status);

    /**
     * Filtra o histórico de um remetente por status.
     * Útil para listar apenas transações pendentes ou com flag de anomalia.
     */
    List<Transaction> findBySenderIdAndStatusOrderByCreatedAtDesc(
            Long senderId, TransactionStatus status);
}
