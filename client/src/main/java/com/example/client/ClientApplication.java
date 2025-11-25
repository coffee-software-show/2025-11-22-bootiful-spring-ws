package com.example.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.Charset;

@SpringBootApplication
public class ClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClientApplication.class, args);
	}

}

@Controller
@ResponseBody
class ClientController {

	private final RestClient http;

	private final String xml;

	ClientController(@Value("classpath:/request.xml") Resource xml, RestClient.Builder http) {
		this.http = http.build();
		try {
			this.xml = xml.getContentAsString(Charset.defaultCharset());
		} //
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@GetMapping("/")
	String index(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
		var token = client.getAccessToken().getTokenValue();
		return this.http.post()
			.uri("http://localhost:8080/ws")
			.contentType(MediaType.TEXT_XML)
			.headers(h -> h.setBearerAuth(token))
			.body(this.xml.replace("123", token))
			.retrieve()
			.body(String.class);
	}

}