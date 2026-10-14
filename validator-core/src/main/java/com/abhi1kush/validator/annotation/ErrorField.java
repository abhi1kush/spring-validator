package com.abhi1kush.validator.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the name under which a property appears in error descriptions, e.g.
 * {@code Mandatory fields missing: {customer_name}}.
 *
 * <p>It works with <em>every</em> constraint on the property (Jakarta, Hibernate or custom), and with
 * Jackson binding errors, so no constraint needs its own "field" attribute. Without it the Jackson JSON
 * property name is used ({@code @JsonProperty} and the mapper's {@code PropertyNamingStrategy}), then
 * the Java name. Errors therefore follow the API contract, not Java variable names.
 *
 * <p>Place it on a field, a record component (it propagates to the field and accessor) or a getter.
 * The name applies to one path segment; nested paths are built segment by segment,
 * e.g. {@code shipping_address.city} or {@code items[1].sku_code}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ErrorField {

    /** @return the name shown in errors for this property */
    String value();
}
