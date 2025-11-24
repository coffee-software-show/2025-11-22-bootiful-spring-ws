package com.example.ws;

import io.spring.guides.gs_producing_web_service.*;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.ws.server.endpoint.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//
// working through https://spring.io/guides/gs/producing-web-service
//
@SpringBootApplication
public class WsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WsApplication.class, args);
	}

}

@Configuration
class SecurityConfiguration {

	// todo make this work with Spring Boot
	// todo make this work with Security

}

@Configuration
@ImportRuntimeHints({ SpringWsHints.class, CountryHints.class })
class WsConfiguration {

	@Bean
	static EndpointBeanFactoryInitializationAotProcessor endpointBeanFactoryInitializationAotProcessor() {
		return new EndpointBeanFactoryInitializationAotProcessor();
	}

	static class EndpointBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

		@Override
		public @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(
				ConfigurableListableBeanFactory beanFactory) {

			var endpoints = new HashSet<TypeReference>();
			var beanNamesForAnnotation = beanFactory.getBeanNamesForAnnotation(Endpoint.class);
			for (var beanName : beanNamesForAnnotation) {
				var type = beanFactory.getType(beanName);
				Assert.notNull(type, "the type for beanName " + beanName + " not found");
				endpoints.add(TypeReference.of(type));
			}
			return (generationContext, _) -> {
				var runtimeHints = generationContext.getRuntimeHints().reflection();
				for (var tr : endpoints) {
					runtimeHints.registerType(tr, MemberCategory.values());
				}
			};
		}

	}

}

class CountryHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		hints.resources().registerResource(new ClassPathResource("countries.xsd"));

		var values = MemberCategory.values();

		for (var c : new Class<?>[] { Country.class, Currency.class, GetCountryRequest.class, GetCountryResponse.class,
				ObjectFactory.class })
			hints.reflection().registerType(c, values);
	}

}

@Endpoint
class CountryEndpoint {

	private static final String NAMESPACE_URI = "http://spring.io/guides/gs-producing-web-service";

	private final CountryRepository countryRepository;

	CountryEndpoint(CountryRepository countryRepository) {
		this.countryRepository = countryRepository;
	}

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
	@ResponsePayload
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
		this.countries.computeIfAbsent("Spain", k -> this.country(k, "Madrid", Currency.EUR, 46704314));
		this.countries.computeIfAbsent("Poland", k -> this.country(k, "Warsaw", Currency.PLN, 38186860));
		this.countries.computeIfAbsent("United Kingdom", k -> this.country(k, "London", Currency.GBP, 63705000));
		IO.println(this.countries);
	}

	private Country country(String name, String capital, Currency currency, int population) {
		var country = new Country();
		country.setCurrency(currency);
		country.setName(name);
		country.setCapital(capital);
		country.setPopulation(population);
		return country;
	}

	public Country findCountry(String name) {
		Assert.notNull(name, "The country's name must not be null");
		return countries.get(name);
	}

}