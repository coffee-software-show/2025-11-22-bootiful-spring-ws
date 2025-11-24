package com.example.ws;

import org.apache.wss4j.dom.WSConstants;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Repository;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.SpringSecurityPasswordValidationCallbackHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class WsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WsApplication.class, args);
	}

}

@Configuration
class SecurityConfiguration {

	@Configuration
	static class SecurityWsConfigurer implements WsConfigurer {

		private final Wss4jSecurityInterceptor securityInterceptor;

		SecurityWsConfigurer(Wss4jSecurityInterceptor securityInterceptor) {
			this.securityInterceptor = securityInterceptor;
		}

		@Override
		public void addInterceptors(List<EndpointInterceptor> interceptors) {
			interceptors.add(this.securityInterceptor);
		}

	}

	// @Bean
	// PasswordEncoder passwordEncoder() {
	// return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	// }

	@Bean
	InMemoryUserDetailsManager inMemoryUserDetailsManager() {
		var users = Set.of("stephane", "rob", "josh")
			.stream()
			.map(username -> User //
				.withUsername(username)//
				.password(("pw")) // passwordEncoder.encode
				.roles("USER") //
				.build() //
			)
			.toList();
		return new InMemoryUserDetailsManager(users);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(a -> a.requestMatchers("/ws/**").permitAll().anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults())
			.build();
	}

	@Bean
	SpringSecurityPasswordValidationCallbackHandler springSecurityPasswordValidationCallbackHandler(
			UserDetailsService service) {
		var security = new SpringSecurityPasswordValidationCallbackHandler();
		security.setUserDetailsService(service);
		return security;
	}

	@Bean
	Wss4jSecurityInterceptor wss4jSecurityInterceptor(SpringSecurityPasswordValidationCallbackHandler handler) {
		var ws4jsi = new Wss4jSecurityInterceptor();
		ws4jsi.setValidationActions("UsernameToken");
		ws4jsi.setValidationCallbackHandler(handler);
		// ws4jsi.setSecurementPasswordType(WSConstants.PW_TEXT);
		return ws4jsi;
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
		return this.countries.get(name);
	}

}