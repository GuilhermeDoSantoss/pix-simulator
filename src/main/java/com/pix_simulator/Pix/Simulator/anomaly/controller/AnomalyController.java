package com.pix_simulator.Pix.Simulator.anomaly.controller;

import com.pix_simulator.Pix.Simulator.anomaly.dto.AnomalyAlertDTO;
import com.pix_simulator.Pix.Simulator.anomaly.service.AnomalyDetectorService;
import com.pix_simulator.Pix.Simulator.shared.response.ApiResponse;
import com.pix_simulator.Pix.Simulator.auth.security.AccountPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para consulta de alertas de anomalia.
 *
 * Responsabilidade: expor o histórico de alertas gerados pelo detector
 * de anomalias para a conta autenticada.
 *
 * Segurança: o accountId é sempre lido do JWT (AccountPrincipal),
 * impedindo que um usuário consulte alertas de outra conta (IDOR).
 *
 * Localizado no pacote anomaly.controller (e não anomaly.consumer)
 * para respeitar a separação de responsabilidades — consumers Kafka
 * não devem conter endpoints REST.
 */
@RestController
@RequestMapping("/api/anomaly")
@RequiredArgsConstructor
@Tag(name = "Anomaly Detection", description = "Consulta de transações suspeitas da conta autenticada")
@SecurityRequirement(name = "bearerAuth")
public class AnomalyController {

    private final AnomalyDetectorService anomalyService;

    /**
     * Retorna todos os alertas de anomalia da conta autenticada.
     *
     * Um alerta é criado quando uma transação tem valor muito acima
     * da média histórica do remetente (razão > threshold configurável).
     * A transação correspondente fica com status FLAGGED.
     */
    @GetMapping("/alerts")
    @Operation(
            summary = "Meus alertas de anomalia",
            description = """
            Retorna todos os alertas gerados para transações enviadas pela conta autenticada.
            
            Um alerta indica que o valor da transação foi detectado como anômalo
            em relação ao histórico de 30 dias do usuário.
            
            A transação correspondente terá status **FLAGGED** e não terá os saldos atualizados
            até que seja revisada manualmente.
            """
    )
    public ResponseEntity<ApiResponse<List<AnomalyAlertDTO>>> myAlerts(
            @AuthenticationPrincipal AccountPrincipal principal) {

        List<AnomalyAlertDTO> alerts = anomalyService.getAlertsForAccount(principal.getAccountId());
        String message = alerts.isEmpty()
                ? "Nenhum alerta encontrado"
                : alerts.size() + " alerta(s) encontrado(s)";

        return ResponseEntity.ok(ApiResponse.success(message, alerts));
    }
}
