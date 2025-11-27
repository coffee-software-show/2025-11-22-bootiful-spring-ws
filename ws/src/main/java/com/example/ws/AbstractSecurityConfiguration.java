package com.example.ws;

import org.apache.wss4j.dom.engine.WSSConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

import java.util.List;

/**
 * defines most of the required machinery to do security in Spring WS with Spring Security.
 */
abstract class AbstractSecurityConfiguration implements WsConfigurer {

    private final ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors;

    AbstractSecurityConfiguration(ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
        this.wss4jSecurityInterceptors = wss4jSecurityInterceptors;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(wss4jSecurityInterceptors.getObject());
    }

    @Bean
    Customizer<HttpSecurity> defaultSpringWsHttpSecurityCustomizer(
            @Value("${spring.webservices.path}") String wsPath) {
        return http -> http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a //
                        .requestMatchers(wsPath.endsWith("/**") ? wsPath : wsPath + "/**").permitAll()
                );

    }

    abstract WSSConfig wssConfig();

    abstract Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig wssConfig);

}
