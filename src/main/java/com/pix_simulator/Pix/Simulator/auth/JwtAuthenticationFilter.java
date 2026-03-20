package com.pix_simulator.Pix.Simulator.auth;

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
 * Filtro JWT que intercepta TODAS as requisições HTTP.
 *
 * Funcionamento:
 * 1. Extrai o token JWT do header "Authorization: Bearer <token>"
 * 2. Valida a assinatura e expiração do token
 * 3. Extrai o accountId e CPF do token
 * 4. Cria um AccountPrincipal e coloca no SecurityContext
 * 5. Libera a requisição para o controller
 *
 * OncePerRequestFilter garante que o filtro é executado apenas uma vez por requisição,
 * evitando o problema de múltiplas execuções em redirecionamentos internos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Extrai o token do header Authorization
        String token = extractTokenFromRequest(request);

        // Se não tem token (ex: endpoint público), passa para o próximo filtro
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Verifica se o token é válido (assinatura + expiração)
        if (!jwtService.isTokenValid(token)) {
            log.warn("Token JWT inválido na requisição para: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Verifica se ainda não existe autenticação no contexto (evita processar duas vezes)
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Extrai o CPF do token para carregar o UserDetails
                String cpf = jwtService.extractCpf(token);

                // Carrega os detalhes do usuário do banco usando o CPF
                // AccountUserDetailsService.loadUserByUsername retorna AccountPrincipal
                AccountPrincipal principal = (AccountPrincipal) userDetailsService.loadUserByUsername(cpf);

                // Cria o objeto de autenticação que o Spring Security reconhece
                // principal: quem é o usuário | null: credenciais (não precisa aqui) | authorities: permissões
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

                // Adiciona detalhes da requisição (IP, session) ao objeto de autenticação
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Registra a autenticação no SecurityContext - a partir daqui o usuário está autenticado
                // Todos os controllers podem usar @AuthenticationPrincipal para acessar o AccountPrincipal
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Autenticação JWT estabelecida para conta ID: {}", principal.getAccountId());

            } catch (Exception e) {
                // Se ocorrer qualquer erro ao processar o token, limpa o contexto de segurança
                log.error("Erro ao processar autenticação JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        // Passa para o próximo filtro na cadeia (ou para o controller)
        filterChain.doFilter(request, response);
    }

    /**
     * Extrai o token JWT do header "Authorization".
     *
     * O padrão esperado é: "Bearer eyJhbGciOiJIUzI1NiJ9...."
     * - "Bearer" é o tipo do token (RFC 6750)
     * - Após o espaço vem o token JWT
     *
     * @return token JWT sem o prefixo "Bearer ", ou null se não encontrado
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // Verifica se o header existe e começa com "Bearer "
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // Retorna apenas o token, removendo o prefixo "Bearer " (7 caracteres)
            return bearerToken.substring(7);
        }

        return null;
    }
}
