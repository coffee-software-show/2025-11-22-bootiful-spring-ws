package com.example.ws;

import jakarta.xml.soap.SOAPException;
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

	private String jwt(SaajSoapMessage saajMessage) throws SOAPException {
		var soapMessage = saajMessage.getSaajMessage();
		var header = soapMessage.getSOAPHeader();
		var soapHeaderElementIterator = header.examineAllHeaderElements();
		while (soapHeaderElementIterator.hasNext()) {
			var she = soapHeaderElementIterator.next();
			if (she.getLocalName().equals("Security")) {
				var bstNodes = she.getChildElements();
				while (bstNodes.hasNext()) {
					var node = bstNodes.next();
					if (node.getLocalName().equals("BinarySecurityToken")) {
						var txt = node.getTextContent();
						var decoded = Base64.getDecoder().decode(txt);
						return new String(decoded, StandardCharsets.UTF_8);
					}
				}
			}
		}
		throw new IllegalStateException("could not load the JWT token");
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
		return http -> http.authorizeHttpRequests(a -> a.requestMatchers(path).permitAll()) //
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
