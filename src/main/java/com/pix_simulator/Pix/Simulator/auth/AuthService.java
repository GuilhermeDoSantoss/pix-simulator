package com.pix_simulator.Pix.Simulator.auth;


import com.pix_simulator.Pix.Simulator.account.Account;
import com.pix_simulator.Pix.Simulator.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Serviço de autenticação - processa o login da conta.
 *
 * Fluxo do login:
 * 1. AuthenticationManager autentica CPF + senha contra o banco
 * 2. Se autenticado, gera token JWT com o accountId embutido
 * 3. Retorna o token para o cliente armazenar e usar nas próximas requisições
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AccountRepository accountRepository;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Realiza o login da conta e retorna um token JWT.
     *
     * @param request CPF e senha
     * @return token JWT + dados básicos da conta
     */
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        log.info("Tentativa de login para CPF: {}***", request.getCpf().substring(0, 3));

        // AuthenticationManager delega para DaoAuthenticationProvider que:
        // 1. Chama AccountUserDetailsService.loadUserByUsername(cpf) para carregar a conta
        // 2. Usa BCrypt para verificar se a senha digitada bate com o hash no banco
        // 3. Lança BadCredentialsException se CPF ou senha estiverem errados
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getCpf(), request.getPassword())
        );

        // Se chegou aqui, a autenticação foi bem-sucedida
        // Extrai o AccountPrincipal do resultado da autenticação
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();

        // Carrega dados adicionais da conta para incluir na resposta
        Account account = accountRepository.findByCpf(principal.getCpf())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada após autenticação"));

        // Gera o token JWT com o accountId embutido
        String token = jwtService.generateToken(principal.getAccountId(), principal.getCpf());

        log.info("Login bem-sucedido para conta ID: {}", principal.getAccountId());

        return new AuthDTO.LoginResponse(token, principal.getAccountId(), account.getName(), jwtExpiration);
    }
}
