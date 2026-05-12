package com.pix_simulator.Pix.Simulator.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticação JWT.
 *
 * Responsabilidade: interceptar toda requisição HTTP, extrair e validar
 * o JWT do header Authorization, e popular o SecurityContext do Spring.
 *
 * FLUXO (por requisição):
 *   1. Extrai token do header "Authorization: Bearer <token>"
 *   2. Valida assinatura e expiração via JwtService
 *   3. Carrega AccountPrincipal do banco pelo CPF extraído do token
 *   4. Popula SecurityContext — a requisição torna-se "autenticada"
 *   5. Continua a cadeia de filtros
 *
 * OncePerRequestFilter garante execução única por requisição, mesmo
 * em forwards internos do servlet container.
 *
 * Segurança:
 * - Token ausente ou inválido → requisição continua sem autenticação
 *   (Spring Security rejeita nos matchers de autorização)
 * - Exceções durante processamento → contexto limpo + log de aviso
 *   (sem vazar detalhes ao cliente)
 * - Verifica SecurityContext antes de autenticar (evita sobrescrita)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER   = "Authorization";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        // Processa apenas se: token presente, válido, e contexto ainda não autenticado
        if (token != null
                && jwtService.isValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                authenticateRequest(token, request);
            } catch (Exception e) {
                log.warn("Falha ao processar JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Autentica a requisição populando o SecurityContext.
     * Metodo separado para clareza e facilidade de teste.
     */
    private void authenticateRequest(String token, HttpServletRequest request) {
        String cpf = jwtService.extractCpf(token);
        AccountPrincipal principal = (AccountPrincipal) userDetailsService.loadUserByUsername(cpf);

        var authToken = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        log.debug("JWT autenticado com sucesso accountId={}", principal.getAccountId());
    }

    /**
     * Extrai o token JWT removendo o prefixo "Bearer ".
     *
     * @return token sem prefixo, ou null se header ausente/malformado
     */
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
