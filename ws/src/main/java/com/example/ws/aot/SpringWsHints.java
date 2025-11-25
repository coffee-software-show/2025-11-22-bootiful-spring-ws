package com.example.ws.aot;

import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.transform.AttachmentCiphertextTransform;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ReflectionUtils;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.server.EndpointAdapter;
import org.springframework.ws.server.EndpointExceptionResolver;
import org.springframework.ws.server.EndpointMapping;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.server.endpoint.PayloadEndpoint;
import org.springframework.ws.server.endpoint.adapter.AbstractMethodEndpointAdapter;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.soap.server.endpoint.adapter.method.SoapHeaderElementMethodArgumentResolver;
import org.springframework.ws.soap.server.endpoint.adapter.method.SoapMethodArgumentResolver;
import org.springframework.ws.transport.WebServiceMessageReceiver;
import org.springframework.ws.transport.http.WebServiceMessageReceiverHandlerAdapter;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * @author Josh Long
 */
class SpringWsHints implements RuntimeHintsRegistrar {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

		this.registerWssConfigClasses(hints);

		for (var config : new String[] { "org/springframework/ws/server/MessageDispatcher.properties",
				"org/springframework/ws/client/core/WebServiceTemplate.properties",
				"org/springframework/ws/soap/server/SoapMessageDispatcher.properties",
				"org/springframework/ws/transport/http/MessageDispatcherServlet.properties" }) {
			try {
				this.registerWsServiceLoaderProperties(config, hints);
			} //
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		for (var r : new String[] { "org/apache/xml/security/resource/xmlsecurity*.properties",
				"com/sun/org/apache/xml/internal/security/resource/xmlsecurity_en.properties" })
			hints.resources().registerPattern(r);

		for (var p : new String[] { "messages/wss4j_errors", "com.sun.xml.messaging.saaj.util.LocalStrings" })
			hints.resources().registerResourceBundle(p);

		var values = MemberCategory.values();

		for (var c : new String[] { "nu.xom.Element", "org.glassfish.jaxb.runtime.v2.runtime.JAXBContextImpl",
				"org.glassfish.jaxb.runtime.v2.runtime.property.SingleElementNodeProperty", "org.dom4j.Element",
				"com.sun.org.apache.xpath.internal.functions.FuncNormalizeSpace",
				"com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl",
				"com.sun.xml.messaging.saaj.soap.SOAPDocumentImpl",
				"org.glassfish.jaxb.runtime.v2.model.runtime.RuntimeElementPropertyInfo", "org.jdom2.Element" })
			hints.reflection().registerType(TypeReference.of(c), values);

		for (var a : AotUtils.findAllClasses(Endpoint.class.getPackageName()))
			hints.reflection().registerType(TypeReference.of(a), values);

		for (var c : new Class<?>[] { AbstractMethodEndpointAdapter.class, EndpointAdapter.class,
				EndpointExceptionResolver.class, EndpointMapping.class, MethodEndpoint.class, PayloadEndpoint.class,
				SoapHeaderElementMethodArgumentResolver.class, SoapMethodArgumentResolver.class,
				WebServiceMessageFactory.class, WebServiceMessageReceiver.class,
				WebServiceMessageReceiverHandlerAdapter.class, })
			hints.reflection().registerType(c, values);

	}

	private void registerWsServiceLoaderProperties(String url, RuntimeHints hints) throws Exception {
		var c = new ClassPathResource(url);
		var properties = new Properties();
		var commmaDelimiter = ",";
		try (var inputStream = c.getInputStream();) {
			properties.load(inputStream);
			properties.propertyNames().asIterator().forEachRemaining(key -> {
				var classes = properties.getProperty((String) key);
				var splitClasses = Arrays.stream(
						classes.contains(commmaDelimiter) ? classes.split(commmaDelimiter) : new String[] { classes })
					.map(String::strip)
					.toList();
				hints.resources().registerPattern(url);
				for (var clazz : splitClasses) {
					hints.reflection().registerType(TypeReference.of(clazz), MemberCategory.values());
					this.log.info("Registering detected {}", clazz);
				}

			});

		}
	}

	private void registerWssConfigClasses(RuntimeHints hints) {
		var instance = WSSConfig.getNewInstance();

		hints.reflection().registerType(AttachmentCiphertextTransform.class, MemberCategory.values());

		for (var fieldName : new String[] { "actionMap", "processorMap", "validatorMap" })
			this.registerWssConfigType(instance, fieldName, hints);
	}

	private void registerWssConfigType(WSSConfig instance, String fieldName, RuntimeHints hints) {
		try {
			var dp = WSSConfig.class.getDeclaredField(fieldName);
			ReflectionUtils.makeAccessible(dp);
			var map = (Map<?, ?>) dp.get(instance);
			for (var clazz : map.values()) {
				if (clazz instanceof Class<?> v) {
					hints.reflection().registerType(v, MemberCategory.values());
					this.log.info("registering Spring WS {}, of key {}", clazz, fieldName);
				}
			}
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
