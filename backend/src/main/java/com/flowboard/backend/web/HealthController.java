package com.flowboard.backend.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * "Estou de pé." Chamado a cada 10 minutos por um monitor externo (UptimeRobot): o plano gratuito
 * do Render desliga o servidor após 15 minutos sem requisições, e religá-lo leva quase 1 minuto.
 *
 * De propósito NÃO consulta o banco: o Neon também dorme quando ocioso (e acorda em menos de 1 s);
 * se esta rota usasse o banco, o despertador o manteria ligado e gastaria as horas gratuitas dele.
 */
@RestController
@Tag(name = "Saúde")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Confirma que a API está no ar (não consulta o banco)")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
