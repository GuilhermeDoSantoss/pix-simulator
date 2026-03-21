package com.pix_simulator.Pix.Simulator.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do Swagger / OpenAPI 3.
 *
 * Define metadados da API e o esquema de autenticação JWT para que
 * o Swagger UI exiba o botão "Authorize" e permita testar endpoints protegidos.
 *
 * Acesse em: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PIX Simulator API")
                        .version("1.0.0")
                        .description("""
                                API do simulador PIX com:
                                - Autenticação JWT por conta (isolamento completo)
                                - Idempotência via Redis (previne duplo débito)
                                - Transações atômicas com log completo
                                - Detecção de anomalias via Kafka + Z-Score
                                
                                **Como usar:**
                                1. Cadastre uma conta em POST /api/accounts/register
                                2. Faça login em POST /api/auth/login
                                3. Clique em "Authorize" e cole o token retornado
                                4. Use os endpoints de PIX com o token no header
                                """)
                        .contact(new Contact()
                                .name("PIX Simulator")
                                .email("dev@pixsimulator.com")))
                // Define o esquema de segurança "bearerAuth" usado pelos controllers
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Cole o token JWT obtido no login. Formato: Bearer <token>")))
                // Aplica autenticação por padrão em todos os endpoints
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
