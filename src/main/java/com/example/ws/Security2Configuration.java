package com.example.ws;

import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.common.principal.SAMLTokenPrincipalImpl;
import org.apache.wss4j.common.principal.WSUsernameTokenPrincipalImpl;
import org.apache.wss4j.common.util.UsernameTokenUtil;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.message.token.UsernameToken;
import org.apache.wss4j.dom.validate.Credential;
import org.apache.wss4j.dom.validate.Validator;
import org.apache.xml.security.utils.XMLUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.SpringSecurityPasswordValidationCallbackHandler;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Set;

/**
 * this demonstrates how to do username and password based authentication with Spring
 * Security.
 */
// @Profile("two")
@Configuration
class Security2Configuration implements WsConfigurer {

	private final ObjectProvider<@NonNull Wss4jSecurityInterceptor> securityInterceptors;

	Security2Configuration(ObjectProvider<@NonNull Wss4jSecurityInterceptor> securityInterceptors) {
		this.securityInterceptors = securityInterceptors;
	}

	@Override
	public void addInterceptors(List<EndpointInterceptor> interceptors) {
		interceptors.add(securityInterceptors.getIfAvailable());
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
	SecurityFilterChain securityFilterChain(@Value("${spring.webservices.path:'/ws/**'}") String wsPath,
			HttpSecurity http) throws Exception {
		return http //
			.csrf(AbstractHttpConfigurer::disable) //
			.authorizeHttpRequests(a -> a //
				.requestMatchers(wsPath.endsWith("/**") ? wsPath : wsPath + "/**") //
				.permitAll()
				.anyRequest()
				.authenticated() //
			)
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
	UserDetailsServiceUsernameTokenValidator userDetailsServiceUsernameTokenValidator(PasswordEncoder passwordEncoder,
			UserDetailsService userDetailsService) {
		return new UserDetailsServiceUsernameTokenValidator(passwordEncoder, userDetailsService);
	}

	/**
	 * @author Josh Long
	 */
	static class UserDetailsServiceUsernameTokenValidator implements Validator {

		private final Logger log = LoggerFactory.getLogger(getClass());

		private final DaoAuthenticationProvider daoAuthenticationProvider;

		UserDetailsServiceUsernameTokenValidator(DaoAuthenticationProvider authenticationProvider) {
			this.daoAuthenticationProvider = authenticationProvider;
		}

		UserDetailsServiceUsernameTokenValidator(PasswordEncoder passwordEncoder,
				UserDetailsService userDetailsService) {
			this.daoAuthenticationProvider = new DaoAuthenticationProvider(userDetailsService);
			this.daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
			this.daoAuthenticationProvider.afterPropertiesSet();
		}

		@Override
		public Credential validate(Credential credential, RequestData data) throws WSSecurityException {
			try {
				var credentialUsernametoken = credential.getUsernametoken();
				var pw = credentialUsernametoken.getPassword();
				var name = credentialUsernametoken.getName();
				var usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(name, pw);
				var authenticated = this.daoAuthenticationProvider.authenticate(usernamePasswordAuthenticationToken);
				if (authenticated.isAuthenticated()) {
					if (this.log.isDebugEnabled())
						this.log.debug("the user {} has been authenticated", name);
					SecurityContextHolder.getContext().setAuthentication(authenticated);
					return credential;
				}
			} //
			catch (UsernameNotFoundException e) {
				// we'll fall through to the exception thrown below.
				this.log.warn("couldn't authenticate! {} ", e.getMessage());
			}

			throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
		}

	}

	@Bean
	WSSConfig wssConfig(UserDetailsServiceUsernameTokenValidator userDetailsServiceUsernameTokenValidator) {
		var wssconfig = WSSConfig.getNewInstance();
		wssconfig.setValidator(WSConstants.USERNAME_TOKEN, userDetailsServiceUsernameTokenValidator);
		return wssconfig;
	}

	@Bean
	WSSecurityEngine wsSecurityEngine(WSSConfig wssConfig) {
		var wsse = new WSSecurityEngine();
		wsse.setWssConfig(wssConfig);
		return wsse;
	}

	@Bean
	Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSecurityEngine wsSecurityEngine,
			SpringSecurityPasswordValidationCallbackHandler handler) {
		var ws4jsi = new Wss4jSecurityInterceptor(wsSecurityEngine);
		ws4jsi.setValidationActions("UsernameToken");
		ws4jsi.setValidationCallbackHandler(handler);
		return ws4jsi;
	}

}
