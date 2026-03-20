package com.pix_simulator.Pix.Simulator.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Serviço responsável por gerar e validar tokens JWT.
 *
 * JWT (JSON Web Token) é composto por 3 partes separadas por ponto:
 * [Header].[Payload].[Signature]
 *
 * - Header: algoritmo usado (HS256)
 * - Payload: claims (dados): accountId, cpf, expiração
 * - Signature: hash do header+payload usando o segredo - garante integridade
 *
 * O accountId fica no payload e é extraído em cada requisição para garantir
 * que cada usuário só acesse seus próprios dados.
 */
@Service
@Slf4j
public class JwtService {

    /**
     * Segredo lido do application.yml - usado para assinar e verificar o token.
     * Em produção, use variável de ambiente e um segredo com alta entropia.
     */
    @Value("${app.jwt.secret}")
    private String secret;

    /**
     * Tempo de expiração em milissegundos lido do application.yml.
     */
    @Value("${app.jwt.expiration}")
    private long expiration;

    /**
     * Gera um token JWT para a conta autenticada.
     *
     * O token contém no payload:
     * - subject: CPF da conta (identificador principal)
     * - claim "accountId": ID da conta no banco
     * - issuedAt: quando foi emitido
     * - expiration: quando expira
     *
     * @param accountId ID da conta no banco
     * @param cpf CPF da conta (usado como subject)
     * @return token JWT assinado com HS256
     */
    public String generateToken(Long accountId, String cpf) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                // Subject identifica o "dono" do token - usamos o CPF
                .subject(cpf)
                // Claim customizado com o ID da conta - extraído em cada requisição
                .claim("accountId", accountId)
                .issuedAt(now)
                .expiration(expirationDate)
                // Assina com HS256 usando o segredo - garante que não foi adulterado
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrai o ID da conta do token JWT.
     * Lança exceção automaticamente se o token for inválido ou expirado.
     */
    public Long extractAccountId(String token) {
        Claims claims = extractAllClaims(token);
        // O claim "accountId" foi definido como Long no generateToken
        return claims.get("accountId", Long.class);
    }

    /**
     * Extrai o CPF (subject) do token JWT.
     */
    public String extractCpf(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Verifica se o token é válido e não está expirado.
     * extractAllClaims já valida a assinatura e lança exceção se inválido.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            // Verifica se a data de expiração é posterior ao momento atual
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.warn("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extrai todos os claims (dados) do token.
     * Verifica automaticamente a assinatura - lança exceção se adulterado.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Converte o segredo (String) em uma chave criptográfica adequada para HS256.
     * Keys.hmacShaKeyFor garante que a chave tem o tamanho mínimo exigido pelo algoritmo.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
