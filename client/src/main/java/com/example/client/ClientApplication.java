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
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.security.AbstractWsSecurityInterceptor;
import org.springframework.ws.soap.security.WsSecuritySecurementException;
import org.springframework.ws.soap.security.WsSecurityValidationException;

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

    static final Class<?>[] JAXB_CLASSES = new Class<?>[]{
            MessageResponse.class,
            MessageRequest.class,
    };

    @Bean
    Jaxb2Marshaller jaxb2Marshaller() {
        var marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(JAXB_CLASSES);
        return marshaller;
    }

    @Bean
    WebServiceTemplate webServiceTemplate(
            Jaxb2Marshaller marshaller,
            OAuthClientInterceptor interceptor,
            WebServiceTemplateBuilder builder) {
        return builder
                .interceptors(interceptor)
                .setMarshaller(marshaller)
                .setUnmarshaller(marshaller)
                .setDefaultUri("http://localhost:8080/ws")
                .build();
    }
}


@Component
class OAuthClientInterceptor implements ClientInterceptor {

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    OAuthClientInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @Override
    public boolean handleRequest(@NonNull MessageContext messageContext) throws WebServiceClientException {
        var principal = this.securityContextHolderStrategy.getContext().getAuthentication();
        if (principal instanceof OAuth2AuthenticationToken oAuth2AuthenticationToken
                && messageContext.getRequest() instanceof SaajSoapMessage saajSoapMessage
        ) {
            var registeredClient = oAuth2AuthenticationToken.getAuthorizedClientRegistrationId();
            var token = OAuth2AuthorizeRequest
                    .withClientRegistrationId(registeredClient)
                    .principal(principal)
                    .build();
            var authorize = this.authorizedClientManager.authorize(token);
            var accessToken = Objects.requireNonNull(authorize).getAccessToken();
            var jwt = accessToken.getTokenValue();
            try {
                var soapMessage = saajSoapMessage.getSaajMessage();
                var envelope = soapMessage.getSOAPPart().getEnvelope();
                var header = envelope.getHeader();
                if (header == null)
                    header = envelope.addHeader();
                var securityHeader = header.addHeaderElement(envelope.createName("Security", "wsse", WSConstants.WSSE_NS));
                var binarySecurityToken = securityHeader.addChildElement(envelope.createName("BinarySecurityToken", "wsse", WSConstants.WSSE_NS));
                var encoded = Base64.getEncoder().encodeToString(jwt.getBytes(StandardCharsets.UTF_8));
                binarySecurityToken.addTextNode(encoded);
                return true;
            } //
            catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
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
    public void afterCompletion(@NonNull MessageContext messageContext, @Nullable Exception ex) throws WebServiceClientException {
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