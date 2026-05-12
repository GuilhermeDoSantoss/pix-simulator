package com.pix_simulator.Pix.Simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação PIX Simulador.
 *
 * SpringBootApplication ativa:
 *   - @ComponentScan: detecta todos os @Service, @Repository, @Controller
 *     e @Component no pacote com.pix e sub-pacotes
 *   - @EnableAutoConfiguration: configura automaticamente JPA, Security,
 *     Kafka, Web, H2, Swagger, etc. com base nas dependências do classpath
 *   - @Configuration: habilita classes de configuração @Bean
 *
 * ORDEM DE INICIALIZAÇÃO:
 *   1. Spring Boot inicializa o contexto
 *   2. H2 cria o schema (ddl-auto: create-drop)
 *   3. KafkaConfig cria os tópicos se não existirem
 *   4. Spring Security configura os filtros
 *   5. Tomcat inicia e aceita requisições na porta 8080
 *
 * URLs DISPONÍVEIS APÓS START:
 *   http://localhost:8080              → Frontend HTML/CSS/JS
 *   http://localhost:8080/swagger-ui.html → Documentação interativa da API
 *   http://localhost:8080/h2-console   → Console do banco H2 (dev only)
 *
 * PRÉ-REQUISITOS:
 *   docker-compose up -d  → sobe o Kafka antes de iniciar a aplicação
 */
@SpringBootApplication
public class PixSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixSimulatorApplication.class, args);
	}
}
