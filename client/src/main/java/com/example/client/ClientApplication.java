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
            for (var t : JAXB)
                hints.reflection().registerType(t, MemberCategory.values());
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    static final Class<?>[] JAXB = new Class[]{
            MessageRequest.class,
            MessageResponse.class
    };


    @Bean
    Jaxb2Marshaller marshaller() {
        var marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(JAXB);
        return marshaller;
    }

    @Bean
    WebServiceTemplate webServiceTemplate(
            Jaxb2Marshaller marshaller,
            OAuthTokenClientInterceptor oAuthTokenClientInterceptor,
            WebServiceTemplateBuilder builder) {
        return builder
                .setDefaultUri("http://localhost:8080/ws")
                .interceptors(oAuthTokenClientInterceptor)
                .setUnmarshaller(marshaller)
                .setMarshaller(marshaller)
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
            if (messageContext.getRequest() instanceof SaajSoapMessage saajSoapMessage) {
                var envelope = saajSoapMessage.getSaajMessage().getSOAPPart().getEnvelope();
                var header = envelope.getHeader();
                if (header == null) envelope.addHeader();
                var securityElement = envelope.getHeader().addHeaderElement(
                        envelope.createName("Security", WSConstants.WSSE_NS)
                );
                var bstElement = securityElement
                        .addChildElement(envelope.createName("BinarySecurityToken", WSConstants.WSSE_NS));
                bstElement.setTextContent(Base64.getEncoder().encodeToString(jwt.getBytes(StandardCharsets.UTF_8)));
                return true;
            }
        }//
        catch (SOAPException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private String jwt() {
        var auth = this.strategy.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken auth2AuthenticationToken) {
            var clientId = auth2AuthenticationToken.getAuthorizedClientRegistrationId();
            var request = OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientId)
                    .principal(auth2AuthenticationToken)
                    .build();
            var authorizedClient = this.authorizedClientManager.authorize(request);
            return Objects.requireNonNull(authorizedClient).getAccessToken().getTokenValue();
        }
        throw new IllegalStateException("could not install JWT token!");
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
        var request = new MessageRequest();
        request.setName("Spring Fans!");
        return (MessageResponse) this.ws.marshalSendAndReceive(request);
    }

}