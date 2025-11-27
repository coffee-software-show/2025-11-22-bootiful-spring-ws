package com.example.ws;

import org.apache.wss4j.dom.engine.WSSConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.SpringSecurityPasswordValidationCallbackHandler;

import java.util.Set;

@Configuration
@Profile("plaintext")
class PlaintextAuthenticationSecurityConfiguration extends AbstractSecurityConfiguration {

	PlaintextAuthenticationSecurityConfiguration(
			ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
		super(wss4jSecurityInterceptors);
	}

	@Bean
	@Override
	Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig config) {
		var ws4jsi = new Wss4jSecurityInterceptor();
		ws4jsi.setValidationActions("UsernameToken");
		ws4jsi.setWssConfig(config);
		ws4jsi.setValidationCallbackHandler(this.springSecurityPasswordValidationCallbackHandler(null));
		return ws4jsi;
	}

	@Override
	WSSConfig wssConfig() {
		return WSSConfig.getNewInstance();
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
	SpringSecurityPasswordValidationCallbackHandler springSecurityPasswordValidationCallbackHandler(
			UserDetailsService service) {
		var security = new SpringSecurityPasswordValidationCallbackHandler();
		security.setUserDetailsService(service);
		return security;
	}

}
