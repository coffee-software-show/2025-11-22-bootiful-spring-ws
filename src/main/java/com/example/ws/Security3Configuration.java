package com.example.ws;

import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.processor.Processor;
import org.apache.wss4j.dom.validate.Credential;
import org.apache.wss4j.dom.validate.Validator;
import org.jspecify.annotations.NonNull;
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
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.example.ws.OAuthTokenProcessor.OAUTH_TOKEN_QNAME;
import static org.apache.wss4j.dom.WSConstants.CUSTOM_TOKEN;

/**
 * this demonstrates how to do OAuth token based authentication with Spring Security.
 */
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

	@Bean
	WSSConfig wssConfig(OAuthTokenProcessor processor, OAuthTokenValidator validator) {
		var wssconfig = WSSConfig.getNewInstance();
		wssconfig.setProcessor(OAUTH_TOKEN_QNAME, processor);
		wssconfig.setValidator(OAUTH_TOKEN_QNAME, validator);
		return wssconfig;
	}

	@Bean
	WSSecurityEngine wsSecurityEngine(WSSConfig wssConfig) {
		var wsse = new WSSecurityEngine();
		wsse.setWssConfig(wssConfig);
		return wsse;
	}

	@Bean
	Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSecurityEngine wsSecurityEngine) {
		var ws4jsi = new Wss4jSecurityInterceptor(wsSecurityEngine);
		ws4jsi.setValidationActions("CustomToken");
		return ws4jsi;
	}

	@Bean
	OAuthTokenProcessor oAuthTokenProcessor() {
		return new OAuthTokenProcessor();
	}

	@Bean
	OAuthTokenValidator oAuthTokenValidator(JwtAuthenticationProvider jwtAuthenticationProvider) {
		return new OAuthTokenValidator(jwtAuthenticationProvider);
	}

}

class OAuthTokenProcessor implements Processor {

	public static final String OAUTH_NS = "http://joshlong.com/soap/security/oauth";

	public static final QName OAUTH_TOKEN_QNAME = new QName(OAUTH_NS, "BearerToken");

	@Override
	public List<WSSecurityEngineResult> handleToken(Element elem, RequestData requestData) throws WSSecurityException {

		// Sanity-check we’re on the right element
		var qname = new QName(elem.getNamespaceURI(), elem.getLocalName());
		if (!OAUTH_TOKEN_QNAME.equals(qname)) {
			throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, "invalidToken",
					new Object[] { "Unexpected element for OAuth token" });
		}

		// Extract token text, trim whitespace
		var token = elem.getTextContent();
		if (token != null) {
			token = token.trim();
		}

		// Build Credential and stash our Principal in it
		var credential = new Credential();
		var principal = new OAuthTokenPrincipal(token);
		credential.setPrincipal(principal);

		// If a Validator is configured for our QName, call it
		var validator = requestData.getWssConfig().getValidator(OAUTH_TOKEN_QNAME);
		if (validator != null) {
			credential = validator.validate(credential, requestData);
		}
		var results = new ArrayList<WSSecurityEngineResult>();
		var result = new WSSecurityEngineResult(CUSTOM_TOKEN, List.of());
		results.add(result);
		if (requestData.getWsDocInfo() != null) {
			requestData.getWsDocInfo().addResult(result);
		}
		return results;
	}

}

class OAuthTokenValidator implements Validator {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JwtAuthenticationProvider jwtAuthenticationProvider;

	OAuthTokenValidator(JwtAuthenticationProvider jwtAuthenticationProvider) {
		this.jwtAuthenticationProvider = jwtAuthenticationProvider;
	}

	@Override
	public Credential validate(Credential credential, RequestData data) throws WSSecurityException {

		if (credential.getPrincipal() != null
				&& credential.getPrincipal() instanceof OAuthTokenPrincipal(String token)) {
			var authentication = this.jwtAuthenticationProvider.authenticate(new BearerTokenAuthenticationToken(token));
			if (Objects.requireNonNull(authentication).isAuthenticated()) {
				var upt = UsernamePasswordAuthenticationToken.authenticated(authentication.getName(), null,
						AuthorityUtils.NO_AUTHORITIES);
				SecurityContextHolder.getContext().setAuthentication(upt);
				if (this.log.isDebugEnabled()) {
					this.log.debug("authentication successful for: {}", upt.getName());
				}
				return credential;
			}
		}
		throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, "invalidToken",
				new Object[] { "missing or invalid OAuthTokenPrincipal" });
	}

}

record OAuthTokenPrincipal(String token) implements Principal {

	@Override
	public String getName() {
		return token;
	}

}
