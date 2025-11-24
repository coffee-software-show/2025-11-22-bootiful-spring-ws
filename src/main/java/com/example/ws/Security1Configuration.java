package com.example.ws;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.SpringSecurityPasswordValidationCallbackHandler;

import java.util.List;
import java.util.Set;

/**
 * this "works" in that if your {@link User#getPassword()} is plaintext and it matches the
 * value in the SOAP WS-Security envelop {@literal Password}, the request will be
 * authenticated. Otherwise, rejected.
 * <p>
 * BUT IT REQUIRES to <EM>plaintext</EM>! Not good.
 * <p>
 * Going to try two things: teaching Spring WS to validate passwords using
 * {@link PasswordEncoder#matches(CharSequence, String)}, and then teaching Spring WS to
 * reject requests using a {@literal CustomToken} and an OAuth token.
 */
@Profile("one")
@Configuration
class Security1Configuration implements WsConfigurer {

	private final ObjectProvider<@NonNull Wss4jSecurityInterceptor> securityInterceptors;

	Security1Configuration(ObjectProvider<@NonNull Wss4jSecurityInterceptor> securityInterceptors) {
		this.securityInterceptors = securityInterceptors;
	}

	@Override
	public void addInterceptors(List<EndpointInterceptor> interceptors) {
		interceptors.add(securityInterceptors.getIfAvailable());
	}

	@Bean
	InMemoryUserDetailsManager inMemoryUserDetailsManager() {
		var users = Set.of("stephane", "rob", "josh")
			.stream()
			.map(username -> User //
				.withUsername(username)//
				.password("pw")
				.roles("USER") //
				.build() //
			)
			.toList();
		return new InMemoryUserDetailsManager(users);
	}

	@Bean
	SecurityFilterChain securityFilterChain(@Value("${spring.webservices.path:'/ws/**'}") String wsPath,
			HttpSecurity http) {
		var pathSuffix = wsPath.endsWith("/**") ? wsPath : wsPath + "/**";
		return http //
			.csrf(AbstractHttpConfigurer::disable) //
			.authorizeHttpRequests(a -> a //
				.requestMatchers(pathSuffix)
				.permitAll() //
				.anyRequest()
				.authenticated() //
			) //
			.httpBasic(Customizer.withDefaults()) //
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
		return ws4jsi;
	}

}
