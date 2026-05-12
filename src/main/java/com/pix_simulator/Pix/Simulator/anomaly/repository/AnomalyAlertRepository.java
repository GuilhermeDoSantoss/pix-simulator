package com.pix_simulator.Pix.Simulator.anomaly.repository;

import com.pix_simulator.Pix.Simulator.anomaly.entity.AlertStatus;
import com.pix_simulator.Pix.Simulator.anomaly.entity.AnomalyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório de acesso a dados para alertas de anomalia.
 *
 * Responsabilidade: abstrair operações de banco para AnomalyAlert.
 * Sem lógica de negócio — apenas acesso a dados.
 */
@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {

    /**
     * Lista alertas de uma conta específica, mais recente primeiro.
     * Usado pelo AnomalyController para exibir alertas ao usuário autenticado.
     */
    List<AnomalyAlert> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * Lista alertas por status de revisão, mais recente primeiro.
     * Útil para dashboards de operadores filtrando alertas OPEN.
     */
    List<AnomalyAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    /**
     * Conta alertas de uma conta por status.
     * Usado para exibir badge de contagem no frontend.
     */
    long countByAccountIdAndStatus(Long accountId, AlertStatus status);
}
