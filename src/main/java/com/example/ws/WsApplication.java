package com.example.ws;

import io.spring.guides.gs_producing_web_service.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.server.EndpointAdapter;
import org.springframework.ws.server.EndpointExceptionResolver;
import org.springframework.ws.server.EndpointMapping;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.server.endpoint.PayloadEndpoint;
import org.springframework.ws.server.endpoint.adapter.AbstractMethodEndpointAdapter;
import org.springframework.ws.server.endpoint.adapter.DefaultMethodEndpointAdapter;
import org.springframework.ws.server.endpoint.adapter.MessageEndpointAdapter;
import org.springframework.ws.server.endpoint.adapter.PayloadEndpointAdapter;
import org.springframework.ws.server.endpoint.annotation.*;
import org.springframework.ws.server.endpoint.mapping.PayloadRootAnnotationMethodEndpointMapping;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.server.SoapMessageDispatcher;
import org.springframework.ws.soap.server.endpoint.SimpleSoapExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultAnnotationExceptionResolver;
import org.springframework.ws.soap.server.endpoint.adapter.method.SoapHeaderElementMethodArgumentResolver;
import org.springframework.ws.soap.server.endpoint.adapter.method.SoapMethodArgumentResolver;
import org.springframework.ws.soap.server.endpoint.mapping.SoapActionAnnotationMethodEndpointMapping;
import org.springframework.ws.transport.WebServiceMessageReceiver;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.transport.http.WebServiceMessageReceiverHandlerAdapter;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.net.PasswordAuthentication;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//
// working through https://spring.io/guides/gs/producing-web-service
//
@SpringBootApplication
public class WsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WsApplication.class, args);
	}

}

@Configuration
class SecurityConfiguration {

	// todo make this work with Spring Boot
	// todo make this work with Security

}

@Configuration
@ImportRuntimeHints(WsConfiguration.Hints.class)
class WsConfiguration {

	@Bean
	static EndpointBeanFactoryInitializationAotProcessor endpointBeanFactoryInitializationAotProcessor() {
		return new EndpointBeanFactoryInitializationAotProcessor();
	}

	static class EndpointBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

		@Override
		public @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(
				ConfigurableListableBeanFactory beanFactory) {

			var endpoints = new HashSet<TypeReference>();
			var beanNamesForAnnotation = beanFactory.getBeanNamesForAnnotation(Endpoint.class);
			for (var beanName : beanNamesForAnnotation) {
				var type = beanFactory.getType(beanName);
				Assert.notNull(type, "the type for beanName " + beanName + " not found");
				endpoints.add(TypeReference.of(type));
			}
			return (generationContext, _) -> {
				var runtimeHints = generationContext.getRuntimeHints().reflection();
				for (var tr : endpoints) {
					runtimeHints.registerType(tr, MemberCategory.values());
				}
			};
		}

	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

			hints.resources().registerResource(new ClassPathResource("countries.xsd"));
			for (var config : new String[] { "org/springframework/ws/soap/server/SoapMessageDispatcher.properties",
					"org/springframework/ws/transport/http/MessageDispatcherServlet.properties" })
				hints.resources().registerPattern(config);

			hints.resources().registerResourceBundle("com.sun.xml.messaging.saaj.util.LocalStrings");

			var values = MemberCategory.values();

			for (var c : new String[] { "org.dom4j.Element", "jakarta.xml.bind.Binder", "org.jdom2.Element",
					"javax.xml.stream.XMLInputFactory", "nu.xom.Element", "com.ibm.wsdl.extensions.schema.SchemaImpl",
					"com.ibm.wsdl.extensions.soap.SOAPBindingImpl",
					"org.glassfish.jaxb.runtime.v2.runtime.property.SingleElementNodeProperty",
					"org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl",
					"org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeElementPropertyInfo",
					"com.sun.org.apache.xpath.internal.functions.FuncNormalizeSpace",
					"com.ibm.wsdl.extensions.soap.SOAPBodyImpl", "com.ibm.wsdl.extensions.soap.SOAPAddressImpl",
					"com.ibm.wsdl.extensions.soap.SOAPOperationImpl", "com.ibm.wsdl.factory.WSDLFactoryImpl" })
				hints.reflection().registerType(TypeReference.of(c), values);

			for (var c : new Class<?>[] { AbstractMethodEndpointAdapter.class, DefaultMethodEndpointAdapter.class,
					DefaultMethodEndpointAdapter.class, EndpointAdapter.class, EndpointExceptionResolver.class,
					EndpointMapping.class, MessageEndpointAdapter.class, MethodEndpoint.class, Namespace.class,
					Namespaces.class, PayloadEndpoint.class, PayloadEndpointAdapter.class, PayloadRoot.class,
					PayloadRootAnnotationMethodEndpointMapping.class, PayloadRoots.class, RequestPayload.class,
					ResponsePayload.class, SaajSoapMessageFactory.class, SoapHeaderElementMethodArgumentResolver.class,
					SimpleSoapExceptionResolver.class, SoapActionAnnotationMethodEndpointMapping.class,
					SoapMethodArgumentResolver.class, SoapFaultAnnotationExceptionResolver.class,
					SoapHeaderElementMethodArgumentResolver.class, SoapMessageDispatcher.class,
					SoapMethodArgumentResolver.class, WebServiceMessageFactory.class, WebServiceMessageReceiver.class,
					WebServiceMessageReceiverHandlerAdapter.class, XPathParam.class, })
				hints.reflection().registerType(c, values);
			for (var c : new Class<?>[] { Country.class, Currency.class, GetCountryRequest.class,
					GetCountryResponse.class, ObjectFactory.class })
				hints.reflection().registerType(c, values);

		}

	}

}

@Endpoint
class CountryEndpoint {

	private static final String NAMESPACE_URI = "http://spring.io/guides/gs-producing-web-service";

	private final CountryRepository countryRepository;

	CountryEndpoint(CountryRepository countryRepository) {
		this.countryRepository = countryRepository;
	}

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
	@ResponsePayload
	public GetCountryResponse getCountry(@RequestPayload GetCountryRequest request) {
		var response = new GetCountryResponse();
		response.setCountry(this.countryRepository.findCountry(request.getName()));
		return response;
	}

}

@Repository
class CountryRepository {

	private final Map<String, Country> countries = new ConcurrentHashMap<>();

	CountryRepository() {
		this.countries.computeIfAbsent("Spain", k -> this.country(k, "Madrid", Currency.EUR, 46704314));
		this.countries.computeIfAbsent("Poland", k -> this.country(k, "Warsaw", Currency.PLN, 38186860));
		this.countries.computeIfAbsent("United Kingdom", k -> this.country(k, "London", Currency.GBP, 63705000));
		IO.println(this.countries);
	}

	private Country country(String name, String capital, Currency currency, int population) {
		var country = new Country();
		country.setCurrency(currency);
		country.setName(name);
		country.setCapital(capital);
		country.setPopulation(population);
		return country;
	}

	public Country findCountry(String name) {
		Assert.notNull(name, "The country's name must not be null");
		return countries.get(name);
	}

}