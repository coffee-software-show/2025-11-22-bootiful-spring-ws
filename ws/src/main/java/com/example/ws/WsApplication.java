package com.example.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class WsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WsApplication.class, args);
	}

}

@Endpoint
class MeEndpoint {

	private static final String NAMESPACE_URI = "http://example.com/ws";

	@ResponsePayload
	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "getMeRequest")
	GetMeResponse getMe() {
		var me = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
		IO.println("authenticated request for " + me);
		var meResponse = new GetMeResponse();
		meResponse.setName(me);
		return meResponse;
	}

}

@Endpoint
class CountryEndpoint {

	private static final String NAMESPACE_URI = "http://example.com/ws";

	private final Map<String, Country> countries = new ConcurrentHashMap<>();

	CountryEndpoint() {
		this.add("Spain", "Madrid", Currency.EUR, 46704314);
		this.add("Poland", "Warsaw", Currency.PLN, 38186860);
		this.add("United Kingdom", "London", Currency.GBP, 63705000);
	}

	@ResponsePayload
	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
	GetCountryResponse getCountry(@RequestPayload GetCountryRequest request) {
		var response = new GetCountryResponse();
		response.setCountry(this.countries.get(request.getName()));
		return response;
	}

	private void add(String name, String capital, Currency currency, int population) {
		this.countries.computeIfAbsent(name, s -> {
			var country = new Country();
			country.setCurrency(currency);
			country.setName(name);
			country.setCapital(capital);
			country.setPopulation(population);
			return country;
		});
	}

}