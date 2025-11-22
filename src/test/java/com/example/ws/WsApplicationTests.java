package com.example.ws;

import io.spring.guides.gs_producing_web_service.GetCountryRequest;
import io.spring.guides.gs_producing_web_service.GetCountryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
class ClientConfiguration {

    @Bean
    WebServiceTemplate webServiceTemplate(Jaxb2Marshaller countryMarshaller) {
        var wst = new WebServiceTemplate();
        wst.setDefaultUri("http://localhost:8080/ws");
        wst.setMarshaller(countryMarshaller);
        wst.setUnmarshaller(countryMarshaller);
        return wst;
    }

    @Bean
    Jaxb2Marshaller countryMarshaller() {
        var marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(GetCountryRequest.class.getPackageName());
        return marshaller;
    }

}

// curl --header "content-type: text/xml" -d @request.xml http://localhost:8080/ws
@SpringBootTest(classes = ClientConfiguration.class)
class WsApplicationTests {

    @Test
    void one(@Autowired WebServiceTemplate webServiceTemplate) throws Exception {
        var request = new GetCountryRequest();
        request.setName("United Kingdom");
        var response = (GetCountryResponse) webServiceTemplate.marshalSendAndReceive(request);
        var country = response.getCountry();
        IO.println(country.getName() + "=" + response);
    }
}
