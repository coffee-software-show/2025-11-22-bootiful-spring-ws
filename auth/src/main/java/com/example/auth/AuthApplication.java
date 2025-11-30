package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.stream.Stream;

@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return h -> h
            .oauth2AuthorizationServer(a -> a.oidc(Customizer.withDefaults()));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder pw) {
        var users = Stream
                .of("josh", "greg", "stephane", "arjen")
                .map(us -> User.withUsername(us)
                        .roles("USER")
                        .password(pw.encode("pw"))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    // 1. usernames/passwords (form, http basic)
    // 2. x509 mutual tls
    // 3. one time tokens
    // 4. passkeys (webauthn)
    // 5. MFA
}
