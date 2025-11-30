package com.example.ws;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@SpringBootApplication
public class WsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WsApplication.class, args);
    }

    static final String NS = "http://example.com/ws";

}


@Component
class OAuthTokenInterceptor implements EndpointInterceptor {

    private final SecurityContextHolderStrategy securityContextHolder = SecurityContextHolder
            .getContextHolderStrategy();

    private final AuthenticationProvider authenticationProvider;

    OAuthTokenInterceptor(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public boolean handleRequest(@NonNull MessageContext messageContext, @NonNull Object endpoint) throws Exception {
        if (messageContext.getRequest() instanceof SaajSoapMessage soapMessage) {
            var token = this.jwt(soapMessage);
            var bearerAuthentication = new BearerTokenAuthenticationToken(token);
            var authenticated = Objects.requireNonNull(this.authenticationProvider.authenticate(bearerAuthentication));
            if (authenticated.isAuthenticated()) {
                this.securityContextHolder.getContext().setAuthentication(authenticated);
                return true;
            }
        }
        return false;
    }

    private String jwt(SaajSoapMessage soapMessage) throws Exception {
        var header = soapMessage.getSaajMessage().getSOAPHeader();
        var all = header.examineAllHeaderElements();
        while (all.hasNext()) {
            var next = all.next();
            if (next.getLocalName().equals("Security")) {
                var children = next.getChildElements();
                while (children.hasNext()) {
                    var child = children.next();
                    if (child.getLocalName().equals("BinarySecurityToken")) {
                        return new String(Base64.getDecoder().decode(child.getTextContent()),
                                StandardCharsets.UTF_8);
                    }
                }
            }
        }
        throw new IllegalStateException("no JWT!");
    }

    @Override
    public boolean handleResponse(@NonNull MessageContext messageContext, @NonNull Object endpoint) throws Exception {
        return true;
    }

    @Override
    public boolean handleFault(@NonNull MessageContext messageContext, @NonNull Object endpoint) throws Exception {
        return true;
    }

    @Override
    public void afterCompletion(@NonNull MessageContext messageContext, @NonNull Object endpoint,
                                @Nullable Exception ex) throws Exception {
        this.securityContextHolder.clearContext();
    }

}

@Configuration
class SecurityConfiguration implements WsConfigurer {

    private final ObjectProvider<@NonNull EndpointInterceptor> oAuthTokenInterceptors;

    SecurityConfiguration(ObjectProvider<@NonNull EndpointInterceptor> oAuthTokenInterceptors) {
        this.oAuthTokenInterceptors = oAuthTokenInterceptors;
    }

    @Override
    public void addInterceptors(@NonNull List<EndpointInterceptor> interceptors) {
        interceptors.add(this.oAuthTokenInterceptors.getIfAvailable());
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }

    @Bean
    JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder decoder) {
        return new JwtAuthenticationProvider(decoder);
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer(@Value("${spring.webservices.path}") String path) {
        return http -> http
                .authorizeHttpRequests(a -> a.requestMatchers(path).permitAll()) //
                .csrf(AbstractHttpConfigurer::disable);
    }

}

@Endpoint
class MessageEndpoint {

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    @ResponsePayload
    @PayloadRoot(namespace = WsApplication.NS, localPart = "messageRequest")
    MessageResponse message(@RequestPayload MessageRequest request) {
        var authentication = this.securityContextHolderStrategy.getContext().getAuthentication();
        var mr = new MessageResponse();
        mr.setMessage("hello, " + Objects.requireNonNull(authentication).getName());
        return mr;
    }

}
