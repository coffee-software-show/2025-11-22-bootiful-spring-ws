package com.example.client;

import com.example.ws.Country;
import com.example.ws.GetCountryRequest;
import com.example.ws.GetCountryResponse;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.xml.transform.StringResult;

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
@Configuration
class Client2Configuration {

    // let's NOT lock down the ws endpoint since it's just a username/pw in this case, not an OAuth client
    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return httpSecurity -> httpSecurity
                .authorizeHttpRequests(a -> a
                                .requestMatchers("/ws").permitAll()
                                .requestMatchers("/secure").permitAll()
                        // .anyRequest().authenticated()
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
    WebServiceTemplate webServiceTemplate(
            Jaxb2Marshaller jaxb2Marshaller,
            WebServiceTemplateBuilder builder,
            Wss4jSecurityInterceptor wss4jSecurityInterceptor
    ) {
        return builder
                .interceptors(wss4jSecurityInterceptor)
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

    ClientController(@Value("classpath:/request.xml") Resource xml,
                     WebServiceTemplate template,
                     RestClient rest) {
        this.rest = rest;
        this.ws = template;
        try {
            this.xml = xml.getContentAsString(Charset.defaultCharset());
        } //
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/secure")
    String webServiceTemplateSecured() throws Exception {
        var doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();
        var getMeElement = doc.createElementNS("http://example.com/ws", "getMeRequest");
        var request = new DOMSource(getMeElement);
        var response = new StringResult();
        this.ws.sendSourceAndReceiveToResult(request, response);
        IO.println("Response: " + response.toString());
        return response .toString() ;
//        return Objects.requireNonNull(response).getCountry();
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