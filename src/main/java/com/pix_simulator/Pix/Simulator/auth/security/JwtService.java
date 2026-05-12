package com.pix_simulator.Pix.Simulator.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Serviço de geração e validação de tokens JWT.
 *
 * Responsabilidade: encapsular toda lógica relacionada ao JWT —
 * geração, validação de assinatura, validação de expiração e extração de claims.
 *
 * ESTRUTURA DO JWT:
 *   - Header:    algoritmo HS256
 *   - Payload:   sub (cpf), accountId, iat (emissão), exp (expiração)
 *   - Signature: HMAC-SHA256 do header+payload com a chave secreta
 *
 * O accountId no payload é a base do isolamento de contas:
 * todo endpoint protegido lê o accountId do token, nunca da URL,
 * tornando impossível acessar dados de outro usuário.
 *
 * Segurança:
 * - Segredo com no mínimo 256 bits (obrigatório para HS256)
 * - Em produção: usar variável de ambiente, nunca commitado em código
 * - isValid() trata todas as exceções JWT (expirado, assinatura inválida,
 *   malformado) retornando false sem vazar detalhes ao cliente
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expirationMs;

    /**
     * Gera um novo token JWT para a conta autenticada.
     *
     * @param accountId ID da conta (claim personalizado no payload)
     * @param cpf       CPF da conta (subject padrão JWT)
     * @return token JWT assinado e compactado
     */
    public String generate(Long accountId, String cpf) {
        return Jwts.builder()
                .subject(cpf)
                .claim("accountId", accountId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(buildKey())
                .compact();
    }

    /**
     * Extrai o accountId do payload do token.
     * Usado pelo JwtAuthenticationFilter para identificar a conta.
     */
    public Long extractAccountId(String token) {
        return parseClaims(token).get("accountId", Long.class);
    }

    /**
     * Extrai o CPF (subject) do payload do token.
     * Usado para carregar o UserDetails do banco após validação do token.
     */
    public String extractCpf(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Valida a assinatura e verifica se o token não está expirado.
     *
     * Retorna false para qualquer exceção JWT (expirado, inválido, malformado)
     * em vez de propagar — o filtro trata false como "não autenticado".
     *
     * @param token token JWT a validar
     * @return true se assinatura válida e não expirado
     */
    public boolean isValid(String token) {
        try {
            return !parseClaims(token).getExpiration().before(new Date());
        } catch (JwtException e) {
            log.warn("Token JWT inválido: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Erro ao validar token JWT: {}", e.getMessage());
            return false;
        }
    }

    /** Parseia e retorna os claims do token (lança exceção se inválido) */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(buildKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Converte o segredo (string) na chave criptográfica para HS256 */
    private SecretKey buildKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
