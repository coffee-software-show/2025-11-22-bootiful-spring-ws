package com.example.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Repository;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class WsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WsApplication.class, args);
	}

}

@Endpoint
class CountryEndpoint {

	private static final String NAMESPACE_URI = "http://example.com/ws";

	private final CountryRepository countryRepository;

	CountryEndpoint(CountryRepository countryRepository) {
		this.countryRepository = countryRepository;
	}

	@ResponsePayload
	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
	public GetCountryResponse getCountry(@RequestPayload GetCountryRequest request) {
		var response = new GetCountryResponse();
		response.setCountry(this.countryRepository.findCountry(request.getName()));
		return response;
	}

}

@Repository
class CountryRepository {

	private final Map<String, Country> countries = new ConcurrentHashMap<>();

	CountryRepository() {
		this.add("Spain", "Madrid", Currency.EUR, 46704314);
		this.add("Poland", "Warsaw", Currency.PLN, 38186860);
		this.add("United Kingdom", "London", Currency.GBP, 63705000);
		IO.println(this.countries);
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

	public Country findCountry(String name) {
		return countries.get(name);
	}

}