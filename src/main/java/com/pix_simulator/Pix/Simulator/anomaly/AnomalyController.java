package com.pix_simulator.Pix.Simulator.anomaly;


import com.pix_simulator.Pix.Simulator.auth.AccountPrincipal;
import com.pix_simulator.Pix.Simulator.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller para consulta de alertas de anomalia da conta autenticada.
 */
@RestController
@RequestMapping("/api/anomaly")
@RequiredArgsConstructor
@Tag(name = "Anomalias", description = "Consulta de alertas de transações suspeitas")
@SecurityRequirement(name = "bearerAuth")
public class AnomalyController {

    private final AnomalyDetectorService anomalyDetectorService;

    /**
     * Retorna todos os alertas de anomalia da conta autenticada.
     * Cada alerta representa um PIX que foi identificado como fora do padrão histórico.
     */
    @GetMapping("/alerts")
    @Operation(
            summary = "Meus alertas de anomalia",
            description = """
            Retorna alertas de transações consideradas fora do padrão histórico.
            
            **Como é calculado:**
            - Busca as últimas 30 transações da conta como linha de base
            - Calcula média e desvio padrão dos valores
            - Se o novo PIX for > média + 3x desvio padrão → alerta gerado
            
            Requer mínimo de 5 transações históricas para ativar a análise.
            """
    )
    public ResponseEntity<ApiResponse<List<AnomalyAlert>>> getMyAlerts(
            @AuthenticationPrincipal AccountPrincipal principal) {

        List<AnomalyAlert> alerts = anomalyDetectorService.getAlertsByAccount(principal.getAccountId());
        return ResponseEntity.ok(ApiResponse.success(
                alerts.isEmpty() ? "Nenhum alerta encontrado" : alerts.size() + " alerta(s) encontrado(s)",
                alerts
        ));
    }
}
