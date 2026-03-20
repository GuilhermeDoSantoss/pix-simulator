package com.pix_simulator.Pix.Simulator.anomaly;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {

    /** Busca todos os alertas de uma conta, mais recentes primeiro */
    List<AnomalyAlert> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /** Busca alertas abertos para revisão */
    List<AnomalyAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    /** Conta alertas abertos de uma conta */
    long countByAccountIdAndStatus(Long accountId, AlertStatus status);
}
