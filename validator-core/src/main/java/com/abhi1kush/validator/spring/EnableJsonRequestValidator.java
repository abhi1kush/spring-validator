package com.abhi1kush.validator.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly opts a Spring application (or one module's configuration) in to a {@code JsonRequestValidator} bean.
 *
 * <p>Nothing in {@code validator-core} activates by itself: there is no auto-configuration, no component, and no
 * {@code @Configuration} class that component scanning could pick up. Only with this annotation are
 * {@code spring-validator.*} properties bound and the library-typed beans registered (see
 * {@link JsonRequestValidatorConfiguration}).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(JsonRequestValidatorConfiguration.class)
public @interface EnableJsonRequestValidator {
}
