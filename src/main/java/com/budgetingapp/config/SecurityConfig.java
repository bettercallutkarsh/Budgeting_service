package com.budgetingapp.config;

import com.budgetingapp.dto.response.MessageResponse;
import com.budgetingapp.repository.UserRepository;
import com.budgetingapp.security.AccountStatusFilter;
import com.budgetingapp.security.ClientIpResolver;
import com.budgetingapp.security.DatabaseUserDetailsService;
import com.budgetingapp.security.LoginRateLimiter;
import com.budgetingapp.security.SecurityErrorWriter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder legacyBcrypt = new BCryptPasswordEncoder();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("argon2", Map.of(
                "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", legacyBcrypt));
        encoder.setDefaultPasswordEncoderForMatches(legacyBcrypt);
        return encoder;
    }

    @Bean
    public ClientIpResolver clientIpResolver(
            @Value("${app.security.login-rate-limit.trust-proxy:false}") boolean trustProxy) {
        return new ClientIpResolver(trustProxy);
    }

    @Bean
    public LoginRateLimiter loginRateLimiter(
            @Value("${app.security.login-rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${app.security.login-rate-limit.window-minutes:15}") long windowMinutes,
            Clock clock) {
        return new LoginRateLimiter(maxAttempts, Duration.ofMinutes(windowMinutes), clock);
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return new DatabaseUserDetailsService(userRepository);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityErrorWriter securityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        return new SecurityErrorWriter(objectMapper, clock);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   CsrfTokenRepository csrfTokenRepository,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   SecurityErrorWriter securityErrorWriter,
                                                   UserRepository userRepository) throws Exception {
        AccountStatusFilter accountStatusFilter = new AccountStatusFilter(userRepository, securityErrorWriter);
        http
            .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/csrf",
                            "/api/v1/auth/logout", "/error")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(exceptionHandling -> exceptionHandling
                    .authenticationEntryPoint((request, response, authException) ->
                            securityErrorWriter.writeError(response, HttpStatus.UNAUTHORIZED,
                                    "NOT_AUTHENTICATED", "Authentication is required to access this resource."))
                    .accessDeniedHandler(accessDeniedHandler(securityErrorWriter)))
            .logout(logout -> logout
                    .logoutUrl("/api/v1/auth/logout")
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(logoutSuccessHandler(securityErrorWriter)))
            .addFilterBefore(accountStatusFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private AccessDeniedHandler accessDeniedHandler(SecurityErrorWriter securityErrorWriter) {
        return (request, response, accessDeniedException) -> {
            if (accessDeniedException instanceof InvalidCsrfTokenException
                    || accessDeniedException instanceof MissingCsrfTokenException) {
                securityErrorWriter.writeError(response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID",
                        "CSRF token is missing or invalid.");
            } else {
                securityErrorWriter.writeError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                        "You do not have permission to perform this action.");
            }
        };
    }

    private LogoutSuccessHandler logoutSuccessHandler(SecurityErrorWriter securityErrorWriter) {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) ->
                securityErrorWriter.writeJson(response, HttpStatus.OK,
                        new MessageResponse("Signed out successfully."));
    }
}
