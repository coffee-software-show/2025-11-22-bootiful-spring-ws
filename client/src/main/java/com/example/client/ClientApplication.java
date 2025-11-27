package com.example.client;

import com.example.ws.Country;
import com.example.ws.GetCountryRequest;
import com.example.ws.GetCountryResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;
import org.springframework.ws.client.core.WebServiceTemplate;
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