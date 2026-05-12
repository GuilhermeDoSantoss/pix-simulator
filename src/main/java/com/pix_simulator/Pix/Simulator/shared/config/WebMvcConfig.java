package com.pix_simulator.Pix.Simulator.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Configuração MVC do Spring.
 *
 * Responsabilidade: mapear rotas de navegação e servir arquivos estáticos.
 *
 * - A rota "/" é redirecionada para o frontend (index.html) que fica em
 *   src/main/resources/static/ e é servido pelo Spring Boot embutido.
 * - O handler de recursos expõe todos os arquivos da pasta static/
 *   para o caminho raiz da aplicação.
 *
 * Isso permite que o frontend HTML/CSS/JS seja servido diretamente
 * pelo mesmo servidor Spring Boot em http://localhost:8080, sem precisar
 * de um servidor web separado (Nginx, Apache, etc.).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redireciona a raiz "/" para o arquivo index.html do frontend
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve todos os arquivos estáticos (HTML, CSS, JS) da pasta /static/
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
