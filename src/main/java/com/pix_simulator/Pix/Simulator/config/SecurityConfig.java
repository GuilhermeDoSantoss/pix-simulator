package com.pix_simulator.Pix.Simulator.config;

import com.pix_simulator.Pix.Simulator.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuração central de segurança da aplicação.
 *
 * Define quais endpoints são públicos, quais exigem autenticação,
 * como o JWT é processado e como as senhas são criptografadas.
 *
 * - @Configuration registra como classe de configuração Spring.
 * - @EnableWebSecurity ativa o módulo de segurança web do Spring.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Define as regras de autorização e o comportamento da segurança HTTP.
     *
     * Principais decisões:
     * - CSRF desabilitado: APIs REST stateless não precisam de CSRF (sem cookies de sessão)
     * - Sessão STATELESS: o estado do usuário está no JWT, não no servidor
     * - JWT Filter adicionado ANTES do filtro padrão do Spring
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Desabilita CSRF - desnecessário para APIs REST com JWT
                .csrf(AbstractHttpConfigurer::disable)

                // Configura CORS para permitir requisições do frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Define quais rotas precisam de autenticação
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos - não precisam de token
                        .requestMatchers(
                                "/api/auth/**",           // Login e registro
                                "/api/accounts/register", // Cadastro de conta
                                "/swagger-ui/**",         // Documentação Swagger
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/",                      // Frontend: index.html na raiz
                                "/index.html",
                                "/*.css",                 // Arquivos estáticos do frontend
                                "/*.js",
                                "/favicon.ico"
                        ).permitAll()
                        // Todos os outros endpoints exigem autenticação (token JWT válido)
                        .anyRequest().authenticated()
                )

                // Política STATELESS: sem sessões no servidor - cada requisição é independente
                // O estado do usuário é transportado no JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Registra o provedor de autenticação customizado
                .authenticationProvider(authenticationProvider())

                // Adiciona o filtro JWT ANTES do filtro de autenticação padrão do Spring
                // Isso garante que o token seja processado antes de qualquer verificação de segurança
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Provedor de autenticação que usa o banco de dados.
     *
     * DaoAuthenticationProvider conecta:
     * - UserDetailsService: carrega o usuário do banco pelo CPF
     * - PasswordEncoder: verifica se a senha digitada bate com o hash no banco
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager é usado pelo AuthService para autenticar o login.
     * Spring Boot gerencia a instância, aqui apenas a expõe como Bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt é o algoritmo padrão para hash de senhas.
     * Gera um salt aleatório em cada chamada - duas chamadas com a mesma senha
     * geram hashes diferentes, mas ambos verificam corretamente com matches().
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configuração CORS para o frontend HTML que roda em outra origem.
     *
     * Em desenvolvimento: permite qualquer origem (*)
     * Em produção: especificar apenas o domínio do frontend
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Permite requisições de qualquer origem (frontend local, etc.)
        config.setAllowedOriginPatterns(List.of("*"));

        // Métodos HTTP permitidos
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Headers permitidos na requisição (Authorization é necessário para o JWT)
        config.setAllowedHeaders(List.of("*"));

        // Permite envio de cookies e headers de autorização
        config.setAllowCredentials(true);

        // Aplica essa configuração para todas as rotas
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}