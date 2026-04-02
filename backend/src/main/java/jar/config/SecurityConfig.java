package jar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()   // 🔥 LOGIN + REGISTER OPEN
                .requestMatchers("/api/leaves/**").hasAnyRole("ADMIN","EMPLOYEE")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());  // ✅ ONLY BASIC AUTH

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.withUsername("admin")
                .password("{noop}admin123")
                .roles("ADMIN")
                .build(),
            User.withUsername("employee")
                .password("{noop}employee123")
                .roles("EMPLOYEE")
                .build(),
            User.withUsername("testuser")
                .password("{noop}testpass")
                .roles("EMPLOYEE")
                .build()
        );
    }

    // ✅ FIXED CORS CONFIG
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "https://employee-leave-management-lake.vercel.app/"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}