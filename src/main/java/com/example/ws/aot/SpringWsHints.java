package com.example.ws.aot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
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

/**
 * @author Josh Long
 */
class SpringWsHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

		for (var config : new String[] { "org/springframework/ws/soap/server/SoapMessageDispatcher.properties",
				"org/springframework/ws/transport/http/MessageDispatcherServlet.properties" })
			hints.resources().registerPattern(config);

		hints.resources().registerResourceBundle("com.sun.xml.messaging.saaj.util.LocalStrings");

		var values = MemberCategory.values();

		for (var c : new String[] { "nu.xom.Element", "org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl",
				"org.glassfish.jaxb.runtime.v2.runtime.property.SingleElementNodeProperty", "org.dom4j.Element",
				"com.sun.org.apache.xpath.internal.functions.FuncNormalizeSpace",
				"org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeElementPropertyInfo", "org.jdom2.Element" })
			hints.reflection().registerType(TypeReference.of(c), values);

		for (var a : AotUtils.findAllClasses(Endpoint.class.getPackageName()))
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

}
