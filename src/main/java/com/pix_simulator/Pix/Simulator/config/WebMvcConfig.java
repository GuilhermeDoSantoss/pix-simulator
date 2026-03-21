package com.pix_simulator.Pix.Simulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração do Spring MVC para servir o frontend estático.
 *
 * O Spring Boot já serve automaticamente arquivos de src/main/resources/static/,
 * mas precisamos garantir que:
 * 1. A rota raiz "/" retorne o index.html
 * 2. Os recursos estáticos (css, js) sejam encontrados
 *
 * Como funciona a integração frontend ↔ backend:
 * - O HTML/CSS/JS ficam em src/main/resources/static/
 * - O Spring Boot os serve em http://localhost:8080/
 * - O JavaScript faz fetch('/api/...') na mesma origem
 * - Como é mesma origem (sem CORS), não há restrição de browser
 * - O token JWT é incluído no header Authorization de cada chamada
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Redireciona a rota raiz para o index.html do frontend.
     * Sem isso, acessar http://localhost:8080/ retornaria 404.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // "/" redireciona para o index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /**
     * Garante que os recursos estáticos em /static/ sejam servidos corretamente.
     * O Spring Boot faz isso automaticamente, mas declaramos explicitamente
     * para ficar documentado e evitar conflitos com o Spring Security.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
