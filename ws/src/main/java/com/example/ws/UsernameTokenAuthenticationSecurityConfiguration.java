package com.example.ws;

import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.AbstractWsPasswordCallbackHandler;

import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.util.Set;

@Configuration
// @Profile("username")
class UsernameTokenAuthenticationSecurityConfiguration extends AbstractSecurityConfiguration {

	UsernameTokenAuthenticationSecurityConfiguration(
			ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
		super(wss4jSecurityInterceptors);
	}

	@Bean
	@Override
	WSSConfig wssConfig() {
		var wssconfig = WSSConfig.getNewInstance();
		wssconfig.setValidator(WSConstants.USERNAME_TOKEN, this.userDetailsServiceUsernameTokenValidator(null));
		return wssconfig;
	}

	@Bean
	@Override
	Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig wssConfig) {
		var ws4jsi = new Wss4jSecurityInterceptor();
		ws4jsi.setValidationActions("UsernameToken");
		ws4jsi.setWssConfig(wssConfig);
		ws4jsi.setValidationCallbackHandler(new AbstractWsPasswordCallbackHandler() {
			@Override
			protected void handleUsernameToken(WSPasswordCallback callback)
					throws IOException, UnsupportedCallbackException {
				// noop. don't care. the validator will do the hardest work.
			}
		});
		return ws4jsi;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder passwordEncoder) {
		var users = Set.of("stephane", "rob", "josh")
			.stream()
			.map(username -> User //
				.withUsername(username)//
				.password(passwordEncoder.encode("pw"))
				.roles("USER") //
				.build() //
			)
			.toList();
		return new InMemoryUserDetailsManager(users);
	}

	@Bean
	DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService) {
		return new DaoAuthenticationProvider(userDetailsService);
	}

	@Bean
	UserDetailsServiceUsernameTokenValidator userDetailsServiceUsernameTokenValidator(
			DaoAuthenticationProvider daoAuthenticationProvider) {
		return new UserDetailsServiceUsernameTokenValidator(daoAuthenticationProvider);
	}

	static class UserDetailsServiceUsernameTokenValidator extends AbstractAuthenticationProviderValidator {

		UserDetailsServiceUsernameTokenValidator(DaoAuthenticationProvider jwtAuthenticationProvider) {
			super(jwtAuthenticationProvider, (credential, _) -> {
				var credentialUsernametoken = credential.getUsernametoken();
				var pw = credentialUsernametoken.getPassword();
				var name = credentialUsernametoken.getName();
				return new UsernamePasswordAuthenticationToken(name, pw);
			});
		}

	}

}
