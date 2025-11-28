package com.example.client;

import com.example.ws.Country;
import com.example.ws.GetCountryRequest;
import com.example.ws.GetCountryResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringResult;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import java.util.Objects;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
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

    private final WebServiceTemplate ws;

    ClientController(WebServiceTemplate template) {
        this.ws = template;
    }

    @GetMapping("/auth")
    ResponseEntity<@NonNull String> auth() throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var getMeElement = doc.createElementNS("http://example.com/ws", "getMeRequest");
        var request = new DOMSource(getMeElement);
        var response = new StringResult();
        this.ws.sendSourceAndReceiveToResult(request, response);
        return ResponseEntity.ok(response.toString());
    }

    @GetMapping("/country")
    Country country() {
        var getCountryRequest = new GetCountryRequest();
        getCountryRequest.setName("United Kingdom");
        var response = (GetCountryResponse) this.ws.marshalSendAndReceive(getCountryRequest);
        return Objects.requireNonNull(response).getCountry();
    }
}