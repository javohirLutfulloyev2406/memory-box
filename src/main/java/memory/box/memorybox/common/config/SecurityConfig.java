package memory.box.memorybox.common.config;

import tools.jackson.databind.ObjectMapper;
import memory.box.memorybox.auth.IdentityResolutionService;
import memory.box.memorybox.common.exception.ApiErrorResponse;
import memory.box.memorybox.common.security.DevAuthProperties;
import memory.box.memorybox.common.security.DevAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Stateless, filter-based security: no cookies, no login page. Every {@code /api/v1}
 * call must be authenticated; the Telegram webhook path authenticates itself
 * separately (see {@code TelegramWebhookController}) and actuator health / API docs
 * are public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public DevAuthenticationFilter devAuthenticationFilter(DevAuthProperties properties,
                                                             IdentityResolutionService identityResolutionService) {
        return new DevAuthenticationFilter(properties, identityResolutionService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DevAuthenticationFilter devAuthenticationFilter,
                                                     ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/telegram/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper)))
                .addFilterBefore(devAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Writes the same {@link ApiErrorResponse} shape the rest of the API uses for
     * 401s, using Spring's own auto-configured {@link ObjectMapper} bean (not a bare
     * {@code new ObjectMapper()}) so date formatting matches every other error
     * response instead of falling back to Jackson's raw-epoch default.
     */
    private static final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper objectMapper;

        JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void commence(jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response,
                              org.springframework.security.core.AuthenticationException authException)
                throws IOException, jakarta.servlet.ServletException {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiErrorResponse body = new ApiErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "Authentication is required to access this resource.", request.getRequestURI());
            objectMapper.writeValue(response.getWriter(), body);
        }
    }
}