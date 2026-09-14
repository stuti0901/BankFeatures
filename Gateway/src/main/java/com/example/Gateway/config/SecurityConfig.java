package com.example.Gateway.config;

import com.example.Gateway.security.BearerTokenServerAuthenticationConverter;
import com.example.Gateway.security.JwtAuthenticationManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean
    MapReactiveUserDetailsService users(AuthProperties properties) {
        return new MapReactiveUserDetailsService(User.withUsername(properties.username())
                .password(properties.passwordHash()).roles("USER").build());
    }

    @Bean
    @Primary
    UserDetailsRepositoryReactiveAuthenticationManager passwordAuthenticationManager(
            MapReactiveUserDetailsService users) {
        var manager = new UserDetailsRepositoryReactiveAuthenticationManager(users);
        manager.setPasswordEncoder(new BCryptPasswordEncoder());
        manager.setScheduler(Schedulers.boundedElastic());
        return manager;
    }

    @Bean
    ServerAuthenticationEntryPoint unauthorized() {
        return (exchange, exception) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().set("WWW-Authenticate", "Bearer");
            byte[] body = "{\"status\":401,\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(reactor.core.publisher.Mono.just(response.bufferFactory().wrap(body)));
        };
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            JwtAuthenticationManager jwtManager, ServerAuthenticationEntryPoint unauthorized) {
        var repository = NoOpServerSecurityContextRepository.getInstance();
        var jwtFilter = new AuthenticationWebFilter(jwtManager);
        jwtFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers(
                "/accounts/api/**", "/cards/api/**", "/loans/api/**"));
        jwtFilter.setServerAuthenticationConverter(new BearerTokenServerAuthenticationConverter());
        jwtFilter.setSecurityContextRepository(repository);
        jwtFilter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(unauthorized));

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(repository)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/", "/login", "/auth/login", "/actuator/health", "/actuator/health/**",
                                "/dashboard").permitAll()
                        .pathMatchers("/accounts/api/**", "/cards/api/**", "/loans/api/**").authenticated()
                        .pathMatchers("/actuator/**").denyAll()
                        .anyExchange().permitAll())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
