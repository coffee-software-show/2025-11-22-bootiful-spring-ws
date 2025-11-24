package com.example.ws.aot;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.util.List;

/**
 * @author Josh Long
 */
abstract class AotUtils {

	static List<? extends Class<?>> findAllClasses(String basePackage) {
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
