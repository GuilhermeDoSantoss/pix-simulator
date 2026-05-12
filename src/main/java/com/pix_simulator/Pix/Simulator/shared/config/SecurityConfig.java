package com.pix_simulator.Pix.Simulator.shared.config;

import com.pix_simulator.Pix.Simulator.auth.security.JwtAuthenticationFilter;
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
 * Configuração do Spring Security.
 *
 * Responsabilidade: definir regras de autenticação, autorização,
 * sessão, CORS e encadeamento de filtros para toda a aplicação.
 *
 * DECISÕES DE SEGURANÇA:
 *
 * 1. CSRF desabilitado: APIs REST com JWT não são vulneráveis a CSRF
 *    (CSRF explora cookies de sessão — aqui usamos JWT no header)
 *
 * 2. Sessão STATELESS: o estado de autenticação vive no JWT,
 *    não no servidor. Elimina necessidade de HttpSession.
 *
 * 3. BCrypt para senhas: padrão da indústria, auto-salted,
 *    resistente a ataques de força bruta e rainbow tables.
 *
 * 4. CORS permissivo (allowedOriginPatterns = *):
 *    Adequado para desenvolvimento local.
 *    EM PRODUÇÃO: restringir para origens específicas do frontend.
 *
 * 5. H2 console permitido com frameOptions.sameOrigin():
 *    O console H2 usa iframes — sem isso seria bloqueado pelo CSP.
 *    EM PRODUÇÃO: remover h2-console completamente.
 *
 * ENDPOINTS PÚBLICOS (sem autenticação):
 * - POST /api/auth/login       → login
 * - POST /api/accounts/register → cadastro
 * - /swagger-ui/**, /api-docs/** → documentação
 * - /h2-console/**              → console do banco (apenas dev)
 * - /, /index.html              → frontend estático
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService      userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST API com JWT não precisa de proteção CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // Configuração CORS — restringir origens em produção
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Regras de autorização por endpoint
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/accounts/register",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/h2-console/**",
                                "/",
                                "/index.html",
                                "/*.css",
                                "/*.js",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // Sem sessão — JWT é stateless por natureza
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Provider que valida CPF + senha via BCrypt
                .authenticationProvider(authenticationProvider())

                // Filtro JWT executado antes do filtro padrão do Spring
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // Permite frames do H2 Console (apenas para dev local)
                .headers(headers ->
                        headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // EM PRODUÇÃO: substituir por URL específica do frontend
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
