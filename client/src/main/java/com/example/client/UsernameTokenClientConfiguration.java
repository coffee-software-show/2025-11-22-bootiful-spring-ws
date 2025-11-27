package com.example.client;

import com.example.ws.GetCountryRequest;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

@Profile("username")
@Configuration
class UsernameTokenClientConfiguration {

	@Bean
	Customizer<HttpSecurity> httpSecurityCustomizer() {
		return h -> h.authorizeHttpRequests(ar -> ar.requestMatchers("/username").permitAll());
	}

	@Bean
	Jaxb2Marshaller jaxb2Marshaller() {
		var marshaller = new Jaxb2Marshaller();
		marshaller.setPackagesToScan(GetCountryRequest.class.getPackageName());
		return marshaller;
	}

	@Bean
	Wss4jSecurityInterceptor wss4jSecurityInterceptor() {
		var interceptor = new Wss4jSecurityInterceptor();
		interceptor.setSecurementUsername("josh");
		interceptor.setSecurementPassword("pw");
		interceptor.setSecurementActions(WSHandlerConstants.USERNAME_TOKEN);
		interceptor.setSecurementPasswordType(WSConstants.PW_TEXT);
		return interceptor;
	}

	@Bean
	WebServiceTemplate webServiceTemplate(Jaxb2Marshaller jaxb2Marshaller, WebServiceTemplateBuilder builder,
			Wss4jSecurityInterceptor wss4jSecurityInterceptor) {
		return builder //
			.interceptors(wss4jSecurityInterceptor) //
			.setDefaultUri("http://localhost:8080/ws") //
			.setMarshaller(jaxb2Marshaller)//
			.setUnmarshaller(jaxb2Marshaller)//
			.build();
	}

}
