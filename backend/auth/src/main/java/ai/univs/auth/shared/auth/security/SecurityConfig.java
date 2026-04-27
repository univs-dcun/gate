package ai.univs.auth.shared.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DelegatedAuthenticationEntryPoint delegatedAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        PathPatternRequestMatcher.Builder matcher = PathPatternRequestMatcher.withDefaults();

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        // Auth - public
                        .requestMatchers(matcher.matcher("/api/v1/auth/signup/**")).permitAll()
                        .requestMatchers(matcher.matcher("/api/v1/auth/login")).permitAll()
                        .requestMatchers(matcher.matcher("/api/v1/auth/logout")).permitAll()
                        .requestMatchers(matcher.matcher("/api/v1/auth/token/refresh")).permitAll()
                        .requestMatchers(matcher.matcher("/api/v1/auth/token/validate")).permitAll()
                        .requestMatchers(matcher.matcher("/api/v1/auth/password/reset/**")).permitAll()
                        // Infra
                        .requestMatchers(matcher.matcher("/actuator/**")).permitAll()
                        .requestMatchers(matcher.matcher("/swagger-ui/**")).permitAll()
                        .requestMatchers(matcher.matcher("/swagger-ui.html")).permitAll()
                        .requestMatchers(matcher.matcher("/api-docs/**")).permitAll()
                        .requestMatchers(matcher.matcher("/favicon.ico")).permitAll()
                        // Protected
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> {
                    ex.authenticationEntryPoint(delegatedAuthenticationEntryPoint);
                    ex.accessDeniedHandler(customAccessDeniedHandler);
                })
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
