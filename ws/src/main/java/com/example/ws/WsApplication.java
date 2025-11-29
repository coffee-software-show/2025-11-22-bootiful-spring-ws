package com.example.ws;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeaderElement;
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

import javax.xml.namespace.QName;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class WsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WsApplication.class, args);
    }

    static final String NS = "http://example.com/ws";
}


@Component
class OAuthTokenInterceptor implements EndpointInterceptor {

    private final SecurityContextHolderStrategy securityContextHolder = SecurityContextHolder.getContextHolderStrategy();

    private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";

    private final AuthenticationProvider authenticationProvider;

    OAuthTokenInterceptor(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    private String extractJwtToken(SaajSoapMessage saajMessage) throws SOAPException {
        var soapMessage = saajMessage.getSaajMessage();
        var header = soapMessage.getSOAPHeader();
        var securityHeaders = header.getChildElements(new QName(WSSE_NS, "Security", "wsse"));
        var securityHeader = (SOAPHeaderElement) securityHeaders.next();
        var bstElements = securityHeader.getChildElements(new QName(WSSE_NS, "BinarySecurityToken", "wsse"));
        var bst = (SOAPElement) bstElements.next();
        var base64Encoded = bst.getTextContent();
        var decoded = Base64.getDecoder().decode(base64Encoded);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    @Override
    public boolean handleRequest(@NonNull MessageContext messageContext, @NonNull Object endpoint) throws Exception {
        if (messageContext.getRequest() instanceof SaajSoapMessage soapMessage) {
            var token = this.extractJwtToken(soapMessage);
            var bearerAuthentication = new BearerTokenAuthenticationToken(token);
            var authenticated = Objects.requireNonNull(this.authenticationProvider.authenticate(bearerAuthentication));
            if (authenticated.isAuthenticated()) {
                this.securityContextHolder.getContext().setAuthentication(authenticated);
                return true;
            }
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
        this.securityContextHolder.clearContext();
    }
}


@Configuration
class SecurityConfiguration
        implements WsConfigurer {

    private final ObjectProvider<@NonNull OAuthTokenInterceptor> oAuthTokenInterceptors;

    SecurityConfiguration(ObjectProvider<@NonNull OAuthTokenInterceptor> oAuthTokenInterceptors) {
        this.oAuthTokenInterceptors = oAuthTokenInterceptors;
    }

    @Override
    public void addInterceptors(@NonNull List<EndpointInterceptor> interceptors) {
        interceptors.add(oAuthTokenInterceptors.getIfAvailable());
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
class MeEndpoint {

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @ResponsePayload
    @PayloadRoot(namespace = WsApplication.NS, localPart = "getMeRequest")
    GetMeResponse getMeResponse() {
        var authentication = this.securityContextHolderStrategy.getContext().getAuthentication();
        var gmr = new GetMeResponse();
        gmr.setName(authentication.getName());
        return gmr;
    }
}

@Endpoint
class CountryEndpoint {

    private final Map<String, Country> countries = new ConcurrentHashMap<>();

    CountryEndpoint() {
        add("Spain", "Madrid", Currency.EUR);
        add("France", "Paris", Currency.EUR);
        add("Poland", "Warsaw", Currency.PLN);
        add("United Kingdom", "London", Currency.GBP);
    }

    @ResponsePayload
    @PayloadRoot(namespace = WsApplication.NS, localPart = "getCountryRequest")
    GetCountryResponse country(@RequestPayload GetCountryRequest request) {
        var response = this.countries.get(request.getName());
        var result = new GetCountryResponse();
        result.setCountry(response);
        return result;
    }

    private void add(String name, String capital, Currency currency) {
        this.countries.computeIfAbsent(name, _ -> {
            var c = new Country();
            c.setName(name);
            c.setCapital(capital);
            c.setCurrency(currency);
            return c;
        });
    }
}
