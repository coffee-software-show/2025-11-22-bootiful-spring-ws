package com.example.client;

import com.example.ws.Country;
import com.example.ws.GetCountryRequest;
import com.example.ws.GetCountryResponse;
import jakarta.xml.soap.SOAPException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.xml.transform.StringResult;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@SpringBootApplication
public class ClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClientApplication.class, args);
	}

	@Bean
	RestClient restClient(RestClient.Builder builder) {
		return builder.build();
	}

}

/**
 * demonstrates using a username/password
 */
@Profile("two")
@Configuration
class Client2Configuration {

	// let's NOT lock down the ws endpoint since it's just a username/pw in this case, not
	// an OAuth client
	@Bean
	Customizer<HttpSecurity> httpSecurityCustomizer() {
		return httpSecurity -> httpSecurity.authorizeHttpRequests(a -> a.requestMatchers("/ws")
			.permitAll() //
			.requestMatchers("/username")
			.permitAll() //
		);
	}

	@Bean
	Jaxb2Marshaller jaxb2Marshaller() {
		var marshaller = new Jaxb2Marshaller();
		marshaller.setPackagesToScan(GetCountryRequest.class.getPackageName());
		return marshaller;
	}

	@Bean
	Wss4jSecurityInterceptor wss4jSecurityInterceptor() {
		var interceptor = new Wss4jSecurityInterceptor();
		interceptor.setSecurementActions(WSHandlerConstants.USERNAME_TOKEN);
		interceptor.setSecurementUsername("josh");
		interceptor.setSecurementPassword("pw");
		interceptor.setSecurementPasswordType(WSConstants.PW_TEXT);
		return interceptor;
	}

	@Bean
	WebServiceTemplate webServiceTemplate(Jaxb2Marshaller jaxb2Marshaller, WebServiceTemplateBuilder builder,
			Wss4jSecurityInterceptor wss4jSecurityInterceptor) {
		return builder //
			.interceptors(wss4jSecurityInterceptor) //
			.setDefaultUri("http://localhost:8080/ws") //
			.setMarshaller(jaxb2Marshaller)//
			.setUnmarshaller(jaxb2Marshaller)//
			.build();
	}

}

// @Profile("three")
@Configuration
class Client3Configuration {

	@Bean
	Jaxb2Marshaller jaxb2Marshaller() {
		var marshaller = new Jaxb2Marshaller();
		marshaller.setPackagesToScan(GetCountryRequest.class.getPackageName());
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
		public boolean handleResponse(MessageContext messageContext) {
			return true;
		}

		@Override
		public boolean handleFault(MessageContext messageContext) {
			return true;
		}

		@Override
		public void afterCompletion(MessageContext messageContext, Exception ex) {
		}

	}

}

@Controller
@ResponseBody
@ImportRuntimeHints(ClientController.Hints.class)
class ClientController {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			hints.resources().registerResource(REQUEST_RESOURCE);
		}

	}

	static final Resource REQUEST_RESOURCE = new ClassPathResource("/request.xml");

	private final RestClient rest;

	private final WebServiceTemplate ws;

	private final String xml;

	ClientController(WebServiceTemplate template, RestClient rest) {
		this.rest = rest;
		this.ws = template;
		try {
			this.xml = REQUEST_RESOURCE.getContentAsString(Charset.defaultCharset());
		} //
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@GetMapping("/oauth")
	String oauthSecuredWebServiceTemplate() throws Exception {
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		var getMeElement = doc.createElementNS("http://example.com/ws", "getMeRequest");
		var request = new DOMSource(getMeElement);
		var response = new StringResult();
		this.ws.sendSourceAndReceiveToResult(request, response);
		return response.toString();
	}

	@GetMapping("/username")
	String usernamePassworedSecuredWebServiceTemplate() throws Exception {
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		var getMeElement = doc.createElementNS("http://example.com/ws", "getMeRequest");
		var request = new DOMSource(getMeElement);
		var response = new StringResult();
		this.ws.sendSourceAndReceiveToResult(request, response);
		return response.toString();
	}

	@GetMapping("/ws")
	Country webServiceTemplate() {
		var getCountryRequest = new GetCountryRequest();
		getCountryRequest.setName("United Kingdom");
		var response = (GetCountryResponse) this.ws.marshalSendAndReceive(getCountryRequest);
		return Objects.requireNonNull(response).getCountry();
	}

	@GetMapping("/rest")
	String restClient(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
		var token = client.getAccessToken().getTokenValue();
		return this.rest //
			.post() //
			.uri("http://localhost:8080/ws") //
			.contentType(MediaType.TEXT_XML)//
			.headers(h -> h.setBearerAuth(token)) //
			.body(this.xml.replace("123", token))//
			.retrieve()//
			.body(String.class);
	}

}