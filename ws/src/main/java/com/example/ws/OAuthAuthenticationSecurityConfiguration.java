package com.example.ws;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeaderElement;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.util.Assert;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import javax.xml.namespace.QName;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Configuration
class OAuthAuthenticationSecurityConfiguration implements WsConfigurer {

	private final ObjectProvider<@NonNull OAuthBinarySecurityTokenEndpointInterceptor> endpointInterceptors;

	OAuthAuthenticationSecurityConfiguration(
			ObjectProvider<@NonNull OAuthBinarySecurityTokenEndpointInterceptor> endpointInterceptors) {
		this.endpointInterceptors = endpointInterceptors;
	}

	@Override
	public void addInterceptors(@NonNull List<EndpointInterceptor> interceptors) {
		interceptors.add(this.endpointInterceptors.getObject());
	}

	@Bean
	Customizer<HttpSecurity> httpSecurityCustomizer(@Value("${spring.webservices.path}") String wsPath) {
		return httpSecurity -> httpSecurity //
			.authorizeHttpRequests(a -> a.requestMatchers(wsPath).permitAll()) //
			.csrf(AbstractHttpConfigurer::disable);
	}

	@Bean
	JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder decoder) {
		return new JwtAuthenticationProvider(decoder);
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
		return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		return new JwtAuthenticationConverter();
	}

	@Bean
	OAuthBinarySecurityTokenEndpointInterceptor oAuthBinarySecurityTokenEndpointInterceptor(
			JwtAuthenticationProvider provider) {
		return new OAuthBinarySecurityTokenEndpointInterceptor(provider);
	}

	static class OAuthBinarySecurityTokenEndpointInterceptor implements EndpointInterceptor {

		private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";

		private static final String VALUE_TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt";

		private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();

		private final JwtAuthenticationProvider jwtAuthenticationProvider;

		OAuthBinarySecurityTokenEndpointInterceptor(JwtAuthenticationProvider jwtAuthenticationProvider) {
			this.jwtAuthenticationProvider = jwtAuthenticationProvider;
		}

		@Override
		public boolean handleRequest(MessageContext messageContext, @NonNull Object endpoint) throws Exception {
			var request = messageContext.getRequest();
			try {
				if (request instanceof SaajSoapMessage soapMessage) {
					var jwt = this.extractJwtFromBinarySecurityToken(soapMessage);
					var authenticated = this.jwtAuthenticationProvider
						.authenticate(new BearerTokenAuthenticationToken(jwt));
					if (Objects.requireNonNull(authenticated).isAuthenticated()) {
						this.securityContextHolderStrategy.getContext().setAuthentication(authenticated);
						return true;
					}
				} //
				throw new IllegalStateException("Authentication is not a valid token");
			} //
			catch (Exception e) {
				return addSoapFault(messageContext, "Invalid or expired JWT: " + e.getMessage());
			}
		}

		private String extractJwtFromBinarySecurityToken(SaajSoapMessage saajMessage) throws SOAPException {
			var soapMessage = saajMessage.getSaajMessage();
			var header = soapMessage.getSOAPHeader();
			Assert.notNull(header, "SOAP Header not found");
			var securityHeaders = header.getChildElements(new QName(WSSE_NS, "Security", "wsse"));
			Assert.state(securityHeaders.hasNext(), "there are no security headers");
			var securityHeader = (SOAPHeaderElement) securityHeaders.next();
			var bstElements = securityHeader.getChildElements(new QName(WSSE_NS, "BinarySecurityToken", "wsse"));
			Assert.state(bstElements.hasNext(), "there are no binary security tokens");
			var bst = (SOAPElement) bstElements.next();
			var valueType = bst.getAttribute("ValueType");
			Assert.state(VALUE_TYPE_JWT.equals(valueType), "it's not a valid JWT");
			var base64Encoded = bst.getTextContent();
			var decoded = Base64.getDecoder().decode(base64Encoded);
			return new String(decoded, StandardCharsets.UTF_8);
		}

		private boolean addSoapFault(MessageContext messageContext, String reason) {
			var response = (SoapMessage) messageContext.getResponse();
			response.getSoapBody().addClientOrSenderFault(reason, Locale.ENGLISH);
			return false;
		}

		@Override
		public boolean handleResponse(@NonNull MessageContext messageContext, @NonNull Object endpoint)
				throws Exception {
			return true;
		}

		@Override
		public boolean handleFault(@NonNull MessageContext messageContext, @NonNull Object endpoint) throws Exception {
			return true;
		}

		@Override
		public void afterCompletion(@NonNull MessageContext messageContext, @NonNull Object endpoint, Exception ex)
				throws Exception {
			this.securityContextHolderStrategy.clearContext();
		}

	}

}
