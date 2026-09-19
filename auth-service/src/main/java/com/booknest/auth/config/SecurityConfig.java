package com.booknest.auth.config;

import com.booknest.auth.security.CustomAuthorizationRequestResolver;
import com.booknest.auth.security.JwtAuthenticationFilter;
import com.booknest.auth.security.OAuth2LoginFailureHandler;
import com.booknest.auth.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 7 configuration (Spring Boot 4.1). Stateless — there is
 * no HttpSession; every request is authenticated fresh from the JWT cookie
 * by {@link JwtAuthenticationFilter}. CSRF is disabled because there is no
 * session-backed CSRF token to protect (cookie-based JWT auth on a
 * stateless API is not vulnerable to classic CSRF the way session-cookie
 * auth is, since the JWT cookie alone cannot be replayed cross-origin to
 * forge state-changing requests without the attacker also controlling a
 * page on an allowed CORS origin).
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            // Injected as an extra @Bean method parameter (Spring resolves
            // these independently of the class's own constructor) rather
            // than added to SecurityConfig's constructor, so this doesn't
            // disturb SecurityConfigTest's `new SecurityConfig(...)` calls.
            ClientRegistrationRepository clientRegistrationRepository
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                                                       .requestMatchers(
                                                               "/api/auth/health",
                                                               "/api/auth/register",
                                                               "/api/auth/login",
                                                               "/api/auth/refresh",
                                                               "/api/auth/forgot-password",
                                                               "/api/auth/reset-password",
                                                               // Logging out must work even with an expired/invalid/absent
                                                               // access token cookie (that's precisely when a client is
                                                               // most likely to call it) — AuthController#logout already
                                                               // handles a null Authentication gracefully, but that branch
                                                               // was unreachable while this endpoint required authentication.
                                                               "/api/auth/logout",
                                                               "/oauth2/**",
                                                               "/login/**")
                                                       .permitAll()
                                                       .anyRequest().authenticated())
                // Without this, an unauthenticated request to any authenticated
                // endpoint falls through to the entry point registered by
                // oauth2Login() below, which redirects (302) to
                // /oauth2/authorization/google. That's correct behavior for a
                // browser hitting a page, but wrong for a JSON API: /api/** must
                // reply 401 so JS/mobile clients can detect "not logged in"
                // instead of being sent through an HTML OAuth2 flow.
                //
                // AntPathRequestMatcher is gone in Spring Security 7 — use its
                // replacement, PathPatternRequestMatcher, instead.
                .exceptionHandling(exceptions -> exceptions
                                                         .defaultAuthenticationEntryPointFor(
                                                                 new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                 PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .oauth2Login(oauth2 -> oauth2
                                               // Captures ?redirect_uri=... off the initial
                                               // /oauth2/authorization/google hit (see
                                               // CustomAuthorizationRequestResolver) so
                                               // OAuth2LoginSuccessHandler can send the browser
                                               // back to where it started — e.g. /cart to resume
                                               // a checkout — instead of always landing on
                                               // /profile.
                                               .authorizationEndpoint(endpoint -> endpoint
                                                       .authorizationRequestResolver(
                                                               new CustomAuthorizationRequestResolver(clientRegistrationRepository)))
                                               .successHandler(oAuth2LoginSuccessHandler)
                                               .failureHandler(oAuth2LoginFailureHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Allow-list driven by {@code booknest.app.cors-allowed-origins} (defaults
     * to the two local dev frontends). {@code allowCredentials(true)} is
     * required so the browser both sends and accepts the {@code
     * booknest_jwt}/{@code booknest_refresh} cookies on cross-origin requests
     * from the storefront and the Ruby playground to this service.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
