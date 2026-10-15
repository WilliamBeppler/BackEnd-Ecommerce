package org.project.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> origensPermitidas;
    private final List<String> origensDoPainel;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${app.cors.allowed-origins}") String allowedOrigins,
                          @Value("${app.admin.allowed-origins}") String adminOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.origensPermitidas = separarOrigens(allowedOrigins, "CORS_ORIGINS");
        this.origensDoPainel = separarOrigens(adminOrigins, "ADMIN_ORIGINS");

        // O painel também precisa passar pelo CORS; sem isto, toda chamada dele falharia
        // com um erro de CORS genérico no navegador, longe da causa real.
        if (!origensPermitidas.containsAll(origensDoPainel)) {
            throw new IllegalStateException(
                    "Toda origem de ADMIN_ORIGINS precisa estar também em CORS_ORIGINS.");
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---- ROTAS PÚBLICAS ----
                        .requestMatchers("/api/usuarios/registrar", "/api/usuarios/login").permitAll()
                        // Logout público: tem que apagar o cookie mesmo com o token já vencido
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/logout").permitAll()

                        // ---- SÓ ADMIN ----
                        // Precisa vir ANTES do GET público abaixo: o Spring usa a primeira regra que bate
                        .requestMatchers(HttpMethod.GET, "/api/produtos/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/produtos/**").hasRole("ADMIN")

                        // Vitrine pública (só produtos ativos)
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**").permitAll()

                        // ---- QUALQUER USUÁRIO LOGADO ----
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").authenticated()

                        // ---- QUALQUER OUTRA COISA: precisa estar logado ----
                        .anyRequest().authenticated()
                )
                // Sem login (ou token inválido/vencido) = 401, e o front manda para o login.
                // Logado mas sem permissão continua sendo 403 (padrão do Spring).
                // Antes, os dois casos viravam 403 e o front não tinha como diferenciar.
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Trava por Origin logo depois do CORS: escritas em produtos só vindas do painel
                .addFilterAfter(new AdminOriginFilter(origensDoPainel), CorsFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origensPermitidas);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Deixa o navegador mandar e receber o cookie do token entre o front e a API.
        // Exige origens escritas uma a uma (por isso nunca "*").
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Chega como "http://a.com, http://b.com": separa pela vírgula,
    // tira os espaços e descarta itens vazios (ex.: vírgula sobrando no fim).
    private static List<String> separarOrigens(String valor, String nomeVariavel) {
        List<String> origens = Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .toList();

        // Falha ao subir em vez de rodar com um CORS que bloqueia tudo sem explicar por quê
        if (origens.isEmpty()) {
            throw new IllegalStateException(nomeVariavel + " está vazia: defina ao menos uma origem do front-end.");
        }
        return origens;
    }
}