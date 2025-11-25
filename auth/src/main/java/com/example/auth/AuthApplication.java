package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

//    @Bean
//    JdbcUserDetailsManager jdbcUserDetailsManager(DataSource dataSource) {
//        var u = new JdbcUserDetailsManager(dataSource);
//        u.setEnableUpdatePassword(true);
//        return u;
//    }

    @Bean
    InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder pw) {
        return new InMemoryUserDetailsManager(
                User.withUsername("josh").password(pw.encode("pw")).roles("ADMIN", "USER").build(),
                User.withUsername("james").password(pw.encode("pw")).roles("ADMIN", "USER").build()
        );
    }

    //
    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return http -> http
                .oauth2AuthorizationServer(a -> a.oidc(Customizer.withDefaults()))
                .webAuthn(w -> w
                        .allowedOrigins("http://localhost:8080")
                        .rpName("bootiful")
                        .rpId("localhost"))
                .oneTimeTokenLogin(o -> o.tokenGenerationSuccessHandler((request, response, oneTimeToken) -> {
                    response.getWriter().println("you've got console mail!");
                    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                    IO.println("please go to http://localhost:9090/login/ott?token=" +
                            oneTimeToken.getTokenValue());
                }));
    }
}
