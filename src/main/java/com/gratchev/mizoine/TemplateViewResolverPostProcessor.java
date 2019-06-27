package com.gratchev.mizoine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.view.groovy.GroovyMarkupViewResolver;

/**
 * fix for exposing _csrf attribute to groovy templates
 * 
 * See https://stackoverflow.com/questions/31122158/spring-boot-groovy-templates-not-adding-csrf-to-model
 * 
 * The bean must be initialized either in xml: https://www.tutorialspoint.com/spring/spring_bean_post_processors.htm
 * Or using @Component annotation
 */
@Component
public class TemplateViewResolverPostProcessor implements BeanPostProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateViewResolverPostProcessor.class);

	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof GroovyMarkupViewResolver) {
			LOGGER.debug("Update groovy markup configuration: " + beanName + " - expose request attributes");
			((GroovyMarkupViewResolver) bean).setExposeRequestAttributes(true);
			
		}
		return bean;
	}

	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}
}