package com.example.ws;

import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.SpringSecurityPasswordValidationCallbackHandler;

import java.util.Set;


/**
 * this demonstrates how to do username and password based authentication with Spring
 * Security.
 */
//@Profile("two")
@Configuration
class Security2Configuration extends AbstractSecurityConfiguration {


    Security2Configuration(ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
        super(wss4jSecurityInterceptors);
    }

    @Bean
    @Override
    WSSConfig wssConfig() {
        var wssconfig = WSSConfig.getNewInstance();
        wssconfig.setValidator(WSConstants.USERNAME_TOKEN,
                this.userDetailsServiceUsernameTokenValidator(null));
        return wssconfig;
    }

    @Bean
    SpringSecurityPasswordValidationCallbackHandler springSecurityPasswordValidationCallbackHandler(UserDetailsService detailsService) {
        var h = new SpringSecurityPasswordValidationCallbackHandler();
        h.setUserDetailsService(detailsService);
        return h;
    }

    @Bean
    @Override
    Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig wssConfig) {

        var callbackHandler = this.springSecurityPasswordValidationCallbackHandler(null);

        var ws4jsi = new Wss4jSecurityInterceptor();
        ws4jsi.setValidationActions("UsernameToken");
        ws4jsi.setWssConfig(wssConfig);
        ws4jsi.setValidationCallbackHandler(callbackHandler);
        return ws4jsi;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder passwordEncoder) {
        var users = Set.of("stephane", "rob", "josh")
                .stream()
                .map(username -> User //
                        .withUsername(username)//
                        .password(passwordEncoder.encode("pw"))
                        .roles("USER") //
                        .build() //
                )
                .toList();
        return new InMemoryUserDetailsManager(users);
    }


    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService) {
        return new DaoAuthenticationProvider(userDetailsService);
    }

    @Bean
    UserDetailsServiceUsernameTokenValidator userDetailsServiceUsernameTokenValidator(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new UserDetailsServiceUsernameTokenValidator(daoAuthenticationProvider);
    }

    static class UserDetailsServiceUsernameTokenValidator extends AbstractAuthenticationProviderValidator {

        UserDetailsServiceUsernameTokenValidator(DaoAuthenticationProvider jwtAuthenticationProvider) {
            super(jwtAuthenticationProvider, (credential, _) -> {
                var credentialUsernametoken = credential.getUsernametoken();
                var pw = credentialUsernametoken.getPassword();
                var name = credentialUsernametoken.getName();
                return new UsernamePasswordAuthenticationToken(name, pw);
            });

        }


    }
}

