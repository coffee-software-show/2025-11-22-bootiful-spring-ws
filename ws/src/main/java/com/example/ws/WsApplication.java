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
class OAuthEndpointInterceptor implements EndpointInterceptor {

    private final SecurityContextHolderStrategy strategy =
            SecurityContextHolder.getContextHolderStrategy();

    private final AuthenticationProvider authenticationProvider;

    OAuthEndpointInterceptor(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public boolean handleRequest(@NonNull MessageContext messageContext, @NonNull Object endpoint) throws Exception {
        if (messageContext.getRequest() instanceof SaajSoapMessage saajSoapMessage) {
            var sm = saajSoapMessage.getSaajMessage();
            var header = sm.getSOAPHeader();
            var elements = header.examineAllHeaderElements();
            while (elements.hasNext()) {
                var next = elements.next();
                if (next.getLocalName().equals("Security")) {
                    var children = next.getChildElements();
                    while (children.hasNext()) {
                        var child = children.next();
                        if (child.getLocalName().equals("BinarySecurityToken")) {
                            var text = child.getTextContent();
                            var jwt = new String(
                                    Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
                            var bearerTokenAuthentication = new BearerTokenAuthenticationToken(jwt);
                            var authenticated =
                                    this.authenticationProvider.authenticate(bearerTokenAuthentication);
                            if (Objects.requireNonNull(authenticated).isAuthenticated()) {
                                this.strategy.getContext().setAuthentication(authenticated);
                                return true;
                            }
                        }
                    }
                }
            }
            // find Security
            // find BinarySecurityToken
            // extract the text value
            // Base64 decode
            // turn into JWT
            // validate it using AP
            // install in SCH
        }

        return false;
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
    public void afterCompletion(@NonNull MessageContext messageContext, @NonNull Object endpoint, @Nullable Exception ex) throws Exception {
        strategy.clearContext();
    }
}

@Configuration
class EndpointSecurityConfiguration implements WsConfigurer {

    // resource server
    // - configure an EndpointInterceptor to extract token and validate it
    // - install a authentication into SCH


    private final ObjectProvider<@NonNull OAuthEndpointInterceptor> oAuthEndpointInterceptor;

    EndpointSecurityConfiguration(ObjectProvider<@NonNull OAuthEndpointInterceptor> oAuthEndpointInterceptor) {
        this.oAuthEndpointInterceptor = oAuthEndpointInterceptor;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String uriOfIssuer) {
        return NimbusJwtDecoder.withIssuerLocation(uriOfIssuer)
                .build();
    }

    @Bean
    JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder decoder) {
        return new JwtAuthenticationProvider(decoder);
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return h -> h
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(ar -> ar.requestMatchers("/ws").permitAll());
    }

    @Override
    public void addInterceptors(@NonNull List<EndpointInterceptor> interceptors) {
        interceptors.add(oAuthEndpointInterceptor.getIfAvailable());
    }
}


@Endpoint
class MessageEndpoint {

    @PayloadRoot(namespace = WsApplication.NS, localPart = "messageRequest")
    @ResponsePayload
    MessageResponse message(@RequestPayload MessageRequest request) {
        var authenticatedName = SecurityContextHolder
                .getContextHolderStrategy()
                .getContext()
                .getAuthentication()
                .getName();
        var response = new MessageResponse();
        response.setMessage("hello, " + authenticatedName );
        return response;
    }
}
