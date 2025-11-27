package com.example.ws;

import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityValidationException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
// @Profile("oauth")
class OAuthAuthenticationSecurityConfiguration extends AbstractSecurityConfiguration {

	OAuthAuthenticationSecurityConfiguration(
			ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
		super(wss4jSecurityInterceptors);
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
	OauthTokenBinaryTokenValidator oauthTokenBinaryTokenValidator(JwtAuthenticationProvider authenticationProvider) {
		return new OauthTokenBinaryTokenValidator(authenticationProvider);
	}

	@Bean
	@Override
	WSSConfig wssConfig() {
		var wssconfig = WSSConfig.getNewInstance();
		wssconfig.setValidator(WSConstants.BINARY_TOKEN, this.oauthTokenBinaryTokenValidator(null));
		return wssconfig;
	}

	@Bean
	@Override
	Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig wssConfig) {
		var ws4jsi = new Wss4jSecurityInterceptor() {
			@Override
			protected void checkResults(@NonNull List<WSSecurityEngineResult> results,
					@NonNull List<Integer> validationActions) throws Wss4jSecurityValidationException {
				// don't throw on imbalanced collection lengths
			}
		};
		ws4jsi.setValidationActions("Timestamp");
		ws4jsi.setWssConfig(wssConfig);
		return ws4jsi;
	}

	static class OauthTokenBinaryTokenValidator extends AbstractAuthenticationProviderValidator {

		OauthTokenBinaryTokenValidator(JwtAuthenticationProvider authenticationProvider) {
			super(authenticationProvider, (credential, requestData) -> {
				var binarySecurityToken = credential.getBinarySecurityToken();
				var jwt = new String(binarySecurityToken.getToken(), StandardCharsets.UTF_8);
				return new BearerTokenAuthenticationToken(jwt);
			});
		}

	}

}
