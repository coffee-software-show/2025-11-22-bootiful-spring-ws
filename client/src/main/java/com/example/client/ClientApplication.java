package com.example.client;

import com.example.ws.Country;
import com.example.ws.GetCountryRequest;
import com.example.ws.GetCountryResponse;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
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
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;
import org.springframework.xml.transform.TransformerObjectSupport;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;

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
        return httpSecurity -> httpSecurity
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/ws").permitAll() //
                        .requestMatchers("/username").permitAll() //
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
        return builder.interceptors(wss4jSecurityInterceptor)
                .setDefaultUri("http://localhost:8080/ws")
                .setMarshaller(jaxb2Marshaller)
                .setUnmarshaller(jaxb2Marshaller)
                .build();
    }

}

/**
 * In this scenario, we want to act as OAuth client.
 * All requests must originate with a valid OAuth token which we can then install
 * in the request made to the downstream SOAP service.
 *
 */
@Configuration
class Client3Configuration {


    @Bean
    OAuthBearerSecurityInterceptor oAuthBearerSecurityInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        return new OAuthBearerSecurityInterceptor(authorizedClientManager);
    }

    /**
     * injects a custom security header to convey the OAuth token on each request.
     */
    static class OAuthBearerSecurityInterceptor extends TransformerObjectSupport
            implements ClientInterceptor {

        private static final String WSSE_NS =
                "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        private static final String OAUTH_NS =
                "http://joshlong.com/soap/security/oauth";

        private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
                .getContextHolderStrategy();

        private final OAuth2AuthorizedClientManager authorizedClientManager;

        OAuthBearerSecurityInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
            this.authorizedClientManager = authorizedClientManager;
        }

        @Override
        public boolean handleRequest(@NonNull MessageContext messageContext) throws WebServiceClientException {


            var soapMessage = (SoapMessage) messageContext.getRequest();
            var soapHeader = soapMessage.getSoapHeader();
            var securityNs = new QName(WSSE_NS, "Security", "wsse");
            var soapHeaderElement = Objects.requireNonNull(soapHeader).addHeaderElement(securityNs);
            soapHeaderElement.setMustUnderstand(true);
            var bearerFragment = """
                    <oauth:BearerToken xmlns:oauth="%s">%s</oauth:BearerToken>
                    """.formatted(OAUTH_NS, this.token());


            try {
                var transformer = this.createTransformer();
                transformer.transform(new StringSource(bearerFragment), soapHeaderElement.getResult());
                return true;
            } //
            catch (Exception e) {
                throw new RuntimeException(e);
            }


        }

        private String token() {
            var principal = this.securityContextHolderStrategy.getContext().getAuthentication();
            if (principal instanceof OAuth2AuthenticationToken auth2AuthenticationToken) {
                var clientRegistrationId = auth2AuthenticationToken.getAuthorizedClientRegistrationId();
                var oAuth2AuthorizedClient = this.oAuth2AuthorizedClient(clientRegistrationId, auth2AuthenticationToken);
                return oAuth2AuthorizedClient.getAccessToken().getTokenValue();
            }
            throw new IllegalStateException("couldn't resolve the registered client id and an associated token!");
        }

        private OAuth2AuthorizedClient oAuth2AuthorizedClient(String clientRegistrationId,
                                                              Authentication principal) {
            var authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientRegistrationId)
                    .principal(principal)
                    .build();
            return this.authorizedClientManager.authorize(authorizeRequest);
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

    @Bean
    Jaxb2Marshaller jaxb2Marshaller() {
        var marshaller = new Jaxb2Marshaller();
        marshaller.setPackagesToScan(GetCountryRequest.class.getPackageName());
        return marshaller;
    }


    @Bean
    WebServiceTemplate webServiceTemplate(
            OAuthBearerSecurityInterceptor oAuthBearerSecurityInterceptor,
            Jaxb2Marshaller jaxb2Marshaller,
            WebServiceTemplateBuilder builder
    ) {
        return builder
                .interceptors(
                        oAuthBearerSecurityInterceptor)
                .setDefaultUri("http://localhost:8080/ws")
                .setMarshaller(jaxb2Marshaller)
                .setUnmarshaller(jaxb2Marshaller)
                .build();
    }

}


@Controller
@ResponseBody
class ClientController {

    private final RestClient rest;

    private final WebServiceTemplate ws;

    private final String xml;

    ClientController(@Value("classpath:/request.xml") Resource xml, WebServiceTemplate template, RestClient rest) {
        this.rest = rest;
        this.ws = template;
        try {
            this.xml = xml.getContentAsString(Charset.defaultCharset());
        } //
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/oauth")
    String oauthSecuredWebServiceTemplate()
            throws Exception {
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