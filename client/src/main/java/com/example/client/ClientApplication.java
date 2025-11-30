package com.example.client;

import com.example.ws.MessageRequest;
import com.example.ws.MessageResponse;
import jakarta.xml.soap.SOAPException;
import org.apache.wss4j.dom.WSConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

@SpringBootApplication
@ImportRuntimeHints(ClientApplication.Hints.class)
public class ClientApplication {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
			for (var t : JAXB_CLASSES)
				hints.reflection().registerType(t, MemberCategory.values());
		}

	}

	public static void main(String[] args) {
		SpringApplication.run(ClientApplication.class, args);
	}

	static final Class<?>[] JAXB_CLASSES = new Class<?>[] { MessageResponse.class, MessageRequest.class, };

	@Bean
	Jaxb2Marshaller jaxb2Marshaller() {
		var marshaller = new Jaxb2Marshaller();
		marshaller.setClassesToBeBound(JAXB_CLASSES);
		return marshaller;
	}

	@Bean
	WebServiceTemplate webServiceTemplate(

			Jaxb2Marshaller marshaller, ClientInterceptor[] interceptors, WebServiceTemplateBuilder builder) {
		return builder ///
			.interceptors(interceptors) //
			.setMarshaller(marshaller)
			.setUnmarshaller(marshaller)
			.setDefaultUri("http://localhost:8080/ws")
			.build();
	}

}

@Component
class OAuthTokenClientInterceptor implements ClientInterceptor {

	private final SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();

	private final OAuth2AuthorizedClientManager authorizedClientManager;

	OAuthTokenClientInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
		this.authorizedClientManager = authorizedClientManager;
	}

	@Override
	public boolean handleRequest(@NonNull MessageContext messageContext) throws WebServiceClientException {
		try {
			var jwt = this.jwt();
			if (messageContext.getRequest() instanceof SaajSoapMessage sm) {
				var env = sm.getSaajMessage().getSOAPPart().getEnvelope();
				if (env.getHeader() == null)
					env.addHeader();
				var security = env.getHeader().addHeaderElement(env.createName("Security", WSConstants.WSSE_NS));
				var bst = security.addChildElement(env.createName("BinarySecurityToken", WSConstants.WSSE_NS));
				bst.setTextContent(Base64.getEncoder()
					.encodeToString(Objects.requireNonNull(jwt).getBytes(StandardCharsets.UTF_8)));
			}
		} //
		catch (SOAPException e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	private String jwt() {
		var auth = this.strategy.getContext().getAuthentication();
		if (auth instanceof OAuth2AuthenticationToken oAuth2AuthenticationToken) {
			var request = OAuth2AuthorizeRequest
				.withClientRegistrationId(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())
				.principal(auth)
				.build();
			var authorized = this.authorizedClientManager.authorize(request);
			if (authorized != null) {
				return authorized.getAccessToken().getTokenValue();
			}
		}
		return null;
	}

	@Override
	public boolean handleResponse(@NonNull MessageContext messageContext) throws WebServiceClientException {
		return true;
	}

	@Override
	public boolean handleFault(@NonNull MessageContext messageContext) throws WebServiceClientException {
		return true;
	}

	@Override
	public void afterCompletion(@NonNull MessageContext messageContext, @Nullable Exception ex)
			throws WebServiceClientException {

	}

}

@Controller
@ResponseBody
class ClientController {

	private final WebServiceTemplate ws;

	ClientController(WebServiceTemplate ws) {
		this.ws = ws;
	}

	@GetMapping("/message")
	MessageResponse message() {
		var message = new MessageRequest();
		message.setName("Bob");
		return (MessageResponse) this.ws.marshalSendAndReceive(message);
	}

}