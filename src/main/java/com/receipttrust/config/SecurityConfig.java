package com.receipttrust.config;

import com.receipttrust.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties({
        SecurityProperties.class,
        StorageProperties.class,
        DebtProperties.class,
        TrustProperties.class,
        CorsProperties.class,
        OcrProperties.class,
        SocialAuthProperties.class,
        ImageExpiryProperties.class
})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Normalize configured origins: drop blanks and trailing slashes so a value
        // like "https://app.onrender.com/" still matches the browser Origin header.
        List<String> origins = corsProperties.getAllowedOrigins().stream()
                .filter(o -> o != null && !o.isBlank())
                .map(o -> o.strip().replaceAll("/+$", ""))
                .toList();

        org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                .info("CORS allowed origin patterns: {}", origins);

        CorsConfiguration config = new CorsConfiguration();
        // allowedOriginPatterns works with allowCredentials(true) and tolerates
        // exact origins as well as patterns.
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight must never be blocked by security.
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/google", "/api/auth/facebook")
                        .permitAll()
                        // Country list is public so the registration form can show it.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/tax-settings/countries")
                        .permitAll()
                        // SSE stream can't send an Authorization header (EventSource), so it
                        // is permitted here and authorized inside the controller by a
                        // short-lived single-use ticket from POST /api/stream-ticket.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/stream")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Admin-only areas, enforced at the URL level (the services
                        // also re-check the role as a second layer). The role is read
                        // from the database on each request, so changes apply at once.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/tax-settings/**")
                        .hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/tax-settings/**")
                        .hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/tax-settings/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Signed in but lacking the role (e.g. a non-admin on /api/admin):
                        // answer 403 directly. The default handler uses sendError(), which
                        // re-dispatches to /error without credentials and surfaces as 401.
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\","
                                    + "\"message\":\"Access denied\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
