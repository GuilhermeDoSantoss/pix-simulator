package com.pix_simulator.Pix.Simulator.auth.service;

import com.pix_simulator.Pix.Simulator.auth.security.AccountPrincipal;
import com.pix_simulator.Pix.Simulator.auth.dto.AuthDTO;
import com.pix_simulator.Pix.Simulator.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Serviço de autenticação: valida credenciais e emite JWT.
 *
 * Responsabilidade: único ponto de entrada para login no sistema.
 * Delega a verificação de senha para o Spring Security (DaoAuthenticationProvider
 * + BCryptPasswordEncoder) e usa JwtService para gerar o token.
 *
 * FLUXO:
 *   1. AuthenticationManager autentica CPF + senha
 *   2. DaoAuthenticationProvider chama AccountUserDetailsService.loadUserByUsername(cpf)
 *   3. BCrypt compara a senha fornecida com o hash armazenado
 *   4. Se válido, retorna Authentication com AccountPrincipal
 *   5. JwtService gera token com accountId e cpf no payload
 *
 * CORREÇÃO DE BUG: versão anterior fazia uma segunda query ao banco
 * (repo.findByCpf) para buscar o nome da conta, mesmo tendo as informações
 * disponíveis no AccountPrincipal. Corrigido para usar apenas o principal.
 *
 * Segurança: BadCredentialsException lançada pelo AuthenticationManager
 * é tratada pelo GlobalExceptionHandler com mensagem genérica ("credenciais inválidas"),
 * sem revelar se o CPF existe ou não no sistema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;

    @Value("${app.jwt.expiration}")
    private long expiration;

    /**
     * Autentica o usuário e retorna um JWT.
     *
     * @param request credenciais (CPF + senha em texto plano)
     * @return resposta com token JWT, accountId, nome e expiração
     * @throws org.springframework.security.authentication.BadCredentialsException se inválido
     */
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        // Delega validação para o Spring Security — lança BadCredentialsException se inválido
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getCpf(), request.getPassword()));

        // Principal já contém accountId e nome — sem necessidade de nova query ao banco
        AccountPrincipal principal = (AccountPrincipal) auth.getPrincipal();
        String token = jwtService.generate(principal.getAccountId(), principal.getCpf());

        log.info("Login realizado com sucesso accountId={}", principal.getAccountId());

        return new AuthDTO.LoginResponse(
                token,
                principal.getAccountId(),
                principal.getName(),
                expiration
        );
    }
}
