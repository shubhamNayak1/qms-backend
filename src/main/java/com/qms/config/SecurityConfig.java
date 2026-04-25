package com.qms.config;

import com.qms.security.JwtAuthEntryPoint;
import com.qms.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // enables @PreAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final UserDetailsService userDetailsService;

    private static final String[] PUBLIC_URLS = {
            // Auth endpoints that do NOT require a token
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            // Swagger / OpenAPI docs
            "/api-docs/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // Health probe used by Render / load-balancers
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF - stateless JWT doesn't need it
            .csrf(AbstractHttpConfigurer::disable)

            // CORS handled by CorsConfig bean
            .cors(cors -> cors.configure(http))

            // Return 401 JSON instead of redirect for unauthenticated requests
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jwtAuthEntryPoint))

            // Stateless sessions - JWT handles auth state
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Endpoint authorization rules
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_URLS).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // Admin settings — individual endpoints are guarded by @PreAuthorize
                    .requestMatchers("/api/v1/admin/**").authenticated()

                    // User management - admin only
                    .requestMatchers("/api/v1/users/**").hasAnyRole("SUPER_ADMIN", "QA_MANAGER")

                    // Audit logs - auditors and above
                    .requestMatchers("/api/v1/audit/**").hasAnyRole("SUPER_ADMIN", "QA_MANAGER", "AUDITOR")

                    // QMS modules - QA roles + Employee (create/edit own records)
                    .requestMatchers("/api/v1/qms/**").hasAnyRole("SUPER_ADMIN", "QA_MANAGER", "QA_OFFICER", "EMPLOYEE")

                    // DMS - doc controllers and QA
                    .requestMatchers("/api/v1/dms/**").hasAnyRole("SUPER_ADMIN", "QA_MANAGER", "DOC_CONTROLLER", "EMPLOYEE")

                    // LMS - all authenticated users
                    .requestMatchers("/api/v1/lms/**").authenticated()

                    // Reports - managers and above
                    .requestMatchers("/api/v1/reports/**").hasAnyRole("SUPER_ADMIN", "QA_MANAGER", "AUDITOR")

                    // All other requests must be authenticated
                    .anyRequest().authenticated()
            )

            // Register JWT filter before Spring's username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
