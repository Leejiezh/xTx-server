package com.leejie.xtx.api.config;

import com.leejie.xtx.api.config.security.JwtAuthFilter;
import com.leejie.xtx.api.config.security.RestAuthExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 无状态 JWT 认证配置。
 *
 * <p>本类存在的前提：只要 classpath 上有 spring-boot-starter-security 而没有任何
 * SecurityFilterChain bean，Boot 就会启用默认配置（anyRequest 全部要认证 + formLogin
 * + httpBasic），所有接口一律返回空响应体的 401。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 免认证路径。
     *
     * <p>这里**不能带 {@code /api} 前缀**：{@code server.servlet.context-path=/api} 由容器
     * 剥离后，Security 的 matcher 看到的是不含 /api 的路径，写成 "/api/auth/**" 永远匹配不到。
     * 同理 Swagger UI 实际访问地址是 /api/swagger-ui.html，但这里写 /swagger-ui.html。
     */
    private static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**"
    };

    private final RestAuthExceptionHandler authExceptionHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 无状态 JWT 不存在可被跨站利用的会话；开着 csrf 会让所有 POST/PUT/DELETE 变 403
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authExceptionHandler)
                        .accessDeniedHandler(authExceptionHandler)
                )
                .addFilterBefore(new JwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
