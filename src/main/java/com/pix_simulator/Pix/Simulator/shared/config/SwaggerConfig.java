package com.pix_simulator.Pix.Simulator.shared.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

/**
 * Configuração do Swagger / OpenAPI.
 *
 * Responsabilidade: expor documentação interativa da API em /swagger-ui.html.
 * Define o esquema de autenticação JWT (bearerAuth) para que o Swagger
 * permita testar endpoints protegidos diretamente pelo navegador.
 *
 * Regra de negócio: todos os endpoints protegidos exigem o header
 * "Authorization: Bearer <token>" — o Swagger herda essa configuração
 * globalmente via addSecurityItem.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PIX Simulator API")
                        .version("1.0.0")
                        .description("""
                    Backend de nível pleno demonstrando:
                    - Autenticação JWT com isolamento de conta
                    - Transferências PIX com idempotência (retentativas seguras)
                    - Processamento assíncrono via Kafka
                    - Detecção de anomalias por análise de razão
                    
                    **Como usar:**
                    1. `POST /api/accounts/register` — crie uma conta
                    2. `POST /api/auth/login` — faça login e obtenha o JWT
                    3. Clique em **Authorize** e cole o token
                    4. `POST /api/pix/send` — envie um PIX
                    """))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Cole o token JWT obtido no login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
