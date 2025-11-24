package com.example.ws;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
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
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.mapping.PayloadRootAnnotationMethodEndpointMapping;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.server.SoapMessageDispatcher;
import org.springframework.ws.soap.server.endpoint.SimpleSoapExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultAnnotationExceptionResolver;
import org.springframework.ws.soap.server.endpoint.adapter.method.SoapHeaderElementMethodArgumentResolver;
import org.springframework.ws.soap.server.endpoint.adapter.method.SoapMethodArgumentResolver;
import org.springframework.ws.soap.server.endpoint.mapping.SoapActionAnnotationMethodEndpointMapping;
import org.springframework.ws.transport.WebServiceMessageReceiver;
import org.springframework.ws.transport.http.WebServiceMessageReceiverHandlerAdapter;

import java.util.List;

class SpringWsHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

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

		for (var a : this.findAllClasses(Endpoint.class.getPackageName()))
			hints.reflection().registerType(TypeReference.of(a), values);

		for (var c : new Class<?>[] { AbstractMethodEndpointAdapter.class, DefaultMethodEndpointAdapter.class,
				DefaultMethodEndpointAdapter.class, EndpointAdapter.class, EndpointExceptionResolver.class,
				EndpointMapping.class, MessageEndpointAdapter.class, MethodEndpoint.class, PayloadEndpoint.class,
				PayloadEndpointAdapter.class, PayloadRootAnnotationMethodEndpointMapping.class,
				SaajSoapMessageFactory.class, SoapHeaderElementMethodArgumentResolver.class,
				SimpleSoapExceptionResolver.class, SoapActionAnnotationMethodEndpointMapping.class,
				SoapMethodArgumentResolver.class, SoapFaultAnnotationExceptionResolver.class,
				SoapHeaderElementMethodArgumentResolver.class, SoapMessageDispatcher.class,
				SoapMethodArgumentResolver.class, WebServiceMessageFactory.class, WebServiceMessageReceiver.class,
				WebServiceMessageReceiverHandlerAdapter.class, })
			hints.reflection().registerType(c, values);

	}

	private List<? extends Class<?>> findAllClasses(String basePackage) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(@NonNull AnnotatedBeanDefinition beanDefinition) {
				return true;
			}
		};
		scanner.addIncludeFilter((_, _) -> true);
		return scanner //
			.findCandidateComponents(basePackage) //
			.stream() //
			.map(bd -> {
				try {
					// IO.println(bd.getBeanClassName());
					return Class.forName(bd.getBeanClassName());
				} //
				catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			})
			.toList();

	}

}
