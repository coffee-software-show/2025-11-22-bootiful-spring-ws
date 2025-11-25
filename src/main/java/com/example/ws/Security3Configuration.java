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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

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
    OAuthTokenProcessor processor() {
        return new OAuthTokenProcessor();
    }

    @Bean
    OAuthTokenValidator validator() {
        return new OAuthTokenValidator();
    }
}

class OAuthTokenProcessor implements Processor {

    public static final String OAUTH_NS = "http://joshlong.com/soap/security/oauth";

    public static final QName OAUTH_TOKEN_QNAME = new QName(OAUTH_NS, "BearerToken");

    @Override
    public List<WSSecurityEngineResult> handleToken(Element elem, RequestData requestData) throws WSSecurityException {

        // Sanity-check we’re on the right element
        var credential = this.credentialFromElement(elem);

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

    private  Credential credentialFromElement(Element elem) throws WSSecurityException {
        var qname = new QName(elem.getNamespaceURI(), elem.getLocalName());
        if (!OAUTH_TOKEN_QNAME.equals(qname)) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, "invalidToken",
                    new Object[]{"Unexpected element for OAuth token"});
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
        return credential;
    }

}

class OAuthTokenValidator implements Validator {

    @Override
    public Credential validate(Credential credential, RequestData data)
            throws WSSecurityException {

        if (credential.getPrincipal() == null
                || !(credential.getPrincipal() instanceof OAuthTokenPrincipal principal)) {
            throw new WSSecurityException(
                    WSSecurityException.ErrorCode.FAILURE,
                    "invalidToken",
                    new Object[]{"Missing OAuthTokenPrincipal"}
            );
        }

        var token = principal.getToken();
        if (!"123".equals(token))
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION,
                    "invalidToken", new Object[]{"OAuth bearer token is invalid"});

        return credential;
    }

}

class OAuthTokenPrincipal implements Principal {

    private final String token;

    OAuthTokenPrincipal(String token) {
        this.token = token;
    }

    @Override
    public String getName() {
        return token;
    }

    public String getToken() {
        return token;
    }

}
