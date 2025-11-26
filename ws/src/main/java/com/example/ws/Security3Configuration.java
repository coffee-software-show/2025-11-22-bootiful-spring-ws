package com.example.ws;

import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.validate.Credential;
import org.apache.wss4j.dom.validate.Validator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.CollectionUtils;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityValidationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Configuration
class Security3Configuration implements WsConfigurer {

	private final ObjectProvider<@NonNull Wss4jSecurityInterceptor> securityInterceptors;

	Security3Configuration(ObjectProvider<@NonNull Wss4jSecurityInterceptor> securityInterceptors) {
		this.securityInterceptors = securityInterceptors;
	}

	@Override
	public void addInterceptors(List<EndpointInterceptor> interceptors) {
		interceptors.add(securityInterceptors.getIfAvailable());
	}

	@Bean
	JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder decoder) {
		return new JwtAuthenticationProvider(decoder);
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
		return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		return new JwtAuthenticationConverter();
	}

	@Bean
	SecurityFilterChain securityFilterChain(@Value("${spring.webservices.path:'/ws/**'}") String wsPath,
			HttpSecurity http) {
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

	static class OauthTokenBinaryTokenValidator implements Validator {

		private final Logger log = LoggerFactory.getLogger(getClass());

		private final JwtAuthenticationProvider jwtAuthenticationProvider;

		OauthTokenBinaryTokenValidator(JwtAuthenticationProvider authenticationProvider) {
			this.jwtAuthenticationProvider = authenticationProvider;
		}

		@Override
		public Credential validate(Credential credential, RequestData data) throws WSSecurityException {
			var binarySecurityToken = credential.getBinarySecurityToken();
			if (binarySecurityToken == null)
				throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE);
			var jwt = new String(binarySecurityToken.getToken(), StandardCharsets.UTF_8);
			this.log.info("the JWT is {}", jwt);
			var authentication = this.jwtAuthenticationProvider.authenticate(new BearerTokenAuthenticationToken(jwt));
			if (Objects.requireNonNull(authentication).isAuthenticated()) {
				var upt = UsernamePasswordAuthenticationToken.authenticated(authentication.getName(), null,
						AuthorityUtils.NO_AUTHORITIES);
				SecurityContextHolder.getContext().setAuthentication(upt);
				return credential;
			}
			throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
		}

	}

	@Bean
	OauthTokenBinaryTokenValidator oauthTokenBinaryTokenValidator(JwtAuthenticationProvider authenticationProvider) {
		return new OauthTokenBinaryTokenValidator(authenticationProvider);
	}

	@Bean
	WSSConfig wssConfig(OauthTokenBinaryTokenValidator oauthTokenBinaryTokenValidator) {
		var wssconfig = WSSConfig.getNewInstance();
		wssconfig.setValidator(WSConstants.BINARY_TOKEN, oauthTokenBinaryTokenValidator);
		return wssconfig;
	}

	@Bean
	WSSecurityEngine wsSecurityEngine(WSSConfig wssConfig) {
		var wsse = new WSSecurityEngine();
		wsse.setWssConfig(wssConfig);
		return wsse;
	}

	@Bean
	JwtWss4jSecurityInterceptor wss4jSecurityInterceptor(WSSecurityEngine wsSecurityEngine) {
		var ws4jsi = new JwtWss4jSecurityInterceptor(wsSecurityEngine);
		ws4jsi.setValidationActions("Timestamp");
		return ws4jsi;
	}

	// override so that we can tell WSS4J to not freak out if we don't have a
	// corresponding action
	static class JwtWss4jSecurityInterceptor extends Wss4jSecurityInterceptor {

		JwtWss4jSecurityInterceptor(WSSecurityEngine securityEngine) {
			super(securityEngine);
		}

		@Override
		protected void checkResults(@NonNull List<WSSecurityEngineResult> results,
				@NonNull List<Integer> validationActions) throws Wss4jSecurityValidationException {
		}

	}

}
