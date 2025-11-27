package com.example.ws;

import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.validate.Credential;
import org.apache.wss4j.dom.validate.Validator;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.Assert;

import java.util.function.BiFunction;

abstract class AbstractAuthenticationProviderValidator implements Validator {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final AuthenticationProvider authenticationProvider;

    private final BiFunction<Credential, RequestData, Authentication> authenticationFunction;

    AbstractAuthenticationProviderValidator(@NonNull AuthenticationProvider authenticationProvider,
                                            @NonNull BiFunction<Credential, RequestData, Authentication> authenticationFunction) {
        this.authenticationProvider = authenticationProvider;
        this.authenticationFunction = authenticationFunction;
    }

    @Override
    public Credential validate(Credential credential, RequestData data) throws WSSecurityException {
        try {
            var authentication = this.authenticationFunction.apply(credential, data);
            var authenticated = this.authenticationProvider.authenticate(authentication);
            if (authenticated != null && authenticated.isAuthenticated()) {
                this.securityContextHolderStrategy.getContext().setAuthentication(authenticated);
                return credential;
            }
        } //
        catch (Exception e) {
            this.log.warn("couldn't authenticate! {} ", e.getMessage());
        }
        throw new WSSecurityException(WSSecurityException.ErrorCode.FAILED_AUTHENTICATION);
    }
}

