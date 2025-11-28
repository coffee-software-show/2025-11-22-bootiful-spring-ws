package com.example.client;

import com.example.ws.Country;
import com.example.ws.GetCountryRequest;
import com.example.ws.GetCountryResponse;
import com.example.ws.GetMeResponse;
import jakarta.xml.soap.SOAPException;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;


@Configuration
class OAuthClientConfiguration {

	@Bean
	Jaxb2Marshaller jaxb2Marshaller() {
		var marshaller = new Jaxb2Marshaller();
		marshaller.setClassesToBeBound(
            GetCountryRequest.class ,
            GetCountryResponse.class ,
            Country.class ,
            Currency.class ,
            GetMeResponse.class
        );
		return marshaller;
	}

	@Bean
	WebServiceTemplate webServiceTemplate(OauthTokenBinaryTokenClientInterceptor oAuthBearerSecurityInterceptor,
			Jaxb2Marshaller jaxb2Marshaller, WebServiceTemplateBuilder builder) {
		return builder //
			.interceptors(oAuthBearerSecurityInterceptor) //
			.setDefaultUri("http://localhost:8080/ws") //
			.setMarshaller(jaxb2Marshaller) //
			.setUnmarshaller(jaxb2Marshaller)
			.build();
	}

	@Bean
	OauthTokenBinaryTokenClientInterceptor oAuthBearerSecurityInterceptor(
			OAuth2AuthorizedClientManager authorizedClientManager) {
		return new OauthTokenBinaryTokenClientInterceptor(authorizedClientManager);
	}

	static class OauthTokenBinaryTokenClientInterceptor implements ClientInterceptor {

		private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";

		private static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";

		private static final String ENCODING_TYPE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";

		private static final String VALUE_TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt";

		private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
			.getContextHolderStrategy();

		private final OAuth2AuthorizedClientManager authorizedClientManager;

		OauthTokenBinaryTokenClientInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
			this.authorizedClientManager = authorizedClientManager;
		}

		private String token() {
			var principal = this.securityContextHolderStrategy.getContext().getAuthentication();
			if (principal instanceof OAuth2AuthenticationToken auth2AuthenticationToken) {
				var clientRegistrationId = auth2AuthenticationToken.getAuthorizedClientRegistrationId();
				var authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
					.principal(auth2AuthenticationToken)
					.build();
				var oAuth2AuthorizedClient = this.authorizedClientManager.authorize(authorizeRequest);
				return Objects.requireNonNull(oAuth2AuthorizedClient).getAccessToken().getTokenValue();
			}
			throw new IllegalStateException("couldn't resolve the registered client id and an associated token!");
		}

		@Override
		public boolean handleRequest(MessageContext messageContext) {
			var message = messageContext.getRequest();
			if (message instanceof SaajSoapMessage soapMessage) {
				try {
					this.addBinarySecurityToken(soapMessage, this.token());
				}
				catch (SOAPException e) {
					throw new RuntimeException("Failed to add BinarySecurityToken to SOAP header", e);
				}
			}
			return true;
		}

		private void addBinarySecurityToken(SaajSoapMessage saajMessage, String jwt) throws SOAPException {
			var soapMessage = saajMessage.getSaajMessage();
			var envelope = soapMessage.getSOAPPart().getEnvelope();
			var header = envelope.getHeader();
			if (header == null) {
				header = envelope.addHeader();
			}

			// wsse:Security
			var securityName = envelope.createName("Security", "wsse", WSSE_NS);
			var securityHeader = header.addHeaderElement(securityName);

			// wsse:BinarySecurityToken
			var bstName = envelope.createName("BinarySecurityToken", "wsse", WSSE_NS);
			var bst = securityHeader.addChildElement(bstName);

			// Attributes
			bst.addAttribute(envelope.createName("ValueType"), VALUE_TYPE_JWT);
			bst.addAttribute(envelope.createName("EncodingType"), ENCODING_TYPE);

			// wsu:Id attribute
			var wsuIdName = envelope.createName("Id", "wsu", WSU_NS);
			bst.addAttribute(wsuIdName, "jwt-" + UUID.randomUUID());

			// Value: base64(jwt bytes) so that on the server side
			// binarySecurityToken.getToken() returns the original jwt bytes
			var encoded = Base64.getEncoder().encodeToString(jwt.getBytes(StandardCharsets.UTF_8));
			bst.addTextNode(encoded);
		}

		@Override
		public boolean handleResponse(@NonNull MessageContext messageContext) {
			return true;
		}

		@Override
		public boolean handleFault(@NonNull MessageContext messageContext) {
			return true;
		}

		@Override
		public void afterCompletion(@NonNull MessageContext messageContext, Exception ex) {
		}

	}

}
