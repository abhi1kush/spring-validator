package com.abhi1kush.validator.core;

import com.abhi1kush.validator.annotation.ErrorField;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Turns validation and binding paths into API-facing names, segment by segment.
 *
 * <p>For each property the display name is resolved in this order:
 * <ol>
 *   <li>{@link ErrorField @ErrorField} on the field, record component or getter;</li>
 *   <li>the JSON property name from the configured {@link ObjectMapper}'s introspection, which honours
 *       {@code @JsonProperty} and the global {@code PropertyNamingStrategy};</li>
 *   <li>the Java property name.</li>
 * </ol>
 * Nested beans, collection, array and map elements are followed through their declared (generic) types,
 * e.g. {@code shipping_address.city}, {@code items[1].sku_code}. Results are cached per class in a
 * {@link ConcurrentHashMap}; the resolver is thread-safe. Only public reflection read APIs are used
 * (no {@code setAccessible}).
 */
public final class FieldNameResolver {

    static final String ROOT = "$";

    private final ObjectMapper objectMapper;
    private final Map<Class<?>, ClassNames> cache = new ConcurrentHashMap<>();

    /** @param objectMapper the mapper whose naming rules define the JSON names */
    public FieldNameResolver(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Renders a Jakarta violation path, e.g. {@code items[1].sku_code}.
     *
     * @param rootType the validated root class
     * @param path     the violation property path
     * @return the display path, {@code $} for the root bean
     */
    public String resolve(Class<?> rootType, Path path) {
        StringBuilder sb = new StringBuilder();
        Prop previous = Prop.root(objectMapper.constructType(rootType));
        for (Path.Node node : path) {
            JavaType owner = previous.type();
            if (node.isInIterable()) {
                appendIndex(sb, node.getIndex(), node.getKey());
                owner = previous.elementType();
            }
            if (node.getKind() == ElementKind.PROPERTY && node.getName() != null) {
                Prop prop = names(owner).byJava(node.getName());
                appendName(sb, prop.displayName());
                previous = prop;
            } else if (node.getKind() == ElementKind.CONTAINER_ELEMENT) {
                previous = Prop.root(previous.elementType());
            }
        }
        return sb.isEmpty() ? ROOT : sb.toString();
    }

    /**
     * Renders a Jackson binding path (JSON names) using {@link ErrorField} where a segment maps to a property.
     *
     * @param rootType the class being deserialized
     * @param path     the Jackson reference chain
     * @return the display path, {@code $} for the root
     */
    public String resolve(Class<?> rootType, List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        JavaType current = objectMapper.constructType(rootType);
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                Prop prop = current == null || current.isContainerType() && !current.isMapLikeType()
                        ? null : names(current).byJson(ref.getFieldName());
                if (current != null && current.isMapLikeType()) {
                    appendIndex(sb, null, ref.getFieldName());
                    current = current.getContentType();
                    continue;
                }
                appendName(sb, prop == null ? ref.getFieldName() : prop.displayName());
                current = prop == null ? null : prop.type();
            } else if (ref.getIndex() >= 0) {
                appendIndex(sb, ref.getIndex(), null);
                current = current == null ? null : current.getContentType();
            }
        }
        return sb.isEmpty() ? ROOT : sb.toString();
    }

    private static void appendName(StringBuilder sb, String name) {
        if (!sb.isEmpty()) {
            sb.append('.');
        }
        sb.append(name);
    }

    private static void appendIndex(StringBuilder sb, Integer index, Object key) {
        sb.append('[').append(index != null ? index : key).append(']');
    }

    private ClassNames names(JavaType type) {
        if (type == null) {
            return ClassNames.EMPTY;
        }
        return cache.computeIfAbsent(type.getRawClass(), c -> introspect(type));
    }

    private ClassNames introspect(JavaType type) {
        Map<String, Prop> byJava = new HashMap<>();
        Map<String, Prop> byJson = new HashMap<>();
        Class<?> raw = type.getRawClass();
        if (raw.isPrimitive() || raw.isEnum() || raw.getName().startsWith("java.")) {
            return ClassNames.EMPTY;
        }
        BeanDescription deser = objectMapper.getDeserializationConfig().introspect(type);
        BeanDescription ser = objectMapper.getSerializationConfig().introspect(type);
        for (BeanDescription desc : List.of(deser, ser)) {
            for (BeanPropertyDefinition def : desc.findProperties()) {
                String javaName = def.getInternalName();
                if (byJava.containsKey(javaName)) {
                    continue;
                }
                String display = errorField(def).orElseGet(() -> reflectiveErrorField(raw, javaName, def.getName()));
                Prop prop = new Prop(display, def.getPrimaryType());
                byJava.put(javaName, prop);
                byJson.putIfAbsent(def.getName(), prop);
            }
        }
        return new ClassNames(this, raw, Map.copyOf(byJava), Map.copyOf(byJson));
    }

    private static java.util.Optional<String> errorField(BeanPropertyDefinition def) {
        return Stream.<AnnotatedMember>of(def.getField(), def.getGetter(), def.getConstructorParameter(),
                        def.getSetter())
                .filter(Objects::nonNull)
                .map(m -> m.getAnnotation(ErrorField.class))
                .filter(Objects::nonNull)
                .map(ErrorField::value)
                .findFirst();
    }

    /** Fallback for properties Jackson does not expose (or where its annotation view lacks the marker). */
    private static String reflectiveErrorField(Class<?> type, String javaName, String fallback) {
        ErrorField ef = findErrorField(type, javaName);
        return ef != null ? ef.value() : fallback;
    }

    private static ErrorField findErrorField(Class<?> type, String javaName) {
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                if (rc.getName().equals(javaName) && rc.isAnnotationPresent(ErrorField.class)) {
                    return rc.getAnnotation(ErrorField.class);
                }
            }
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(javaName) && f.isAnnotationPresent(ErrorField.class)) {
                    return f.getAnnotation(ErrorField.class);
                }
            }
        }
        String cap = javaName.isEmpty() ? javaName : Character.toUpperCase(javaName.charAt(0)) + javaName.substring(1);
        for (Method m : type.getMethods()) {
            if (m.getParameterCount() == 0 && m.isAnnotationPresent(ErrorField.class)
                    && (m.getName().equals("get" + cap) || m.getName().equals("is" + cap) || m.getName().equals(javaName))) {
                return m.getAnnotation(ErrorField.class);
            }
        }
        return null;
    }

    /** A resolved property: its display name and declared type. */
    private record Prop(String displayName, JavaType type) {

        static Prop root(JavaType type) {
            return new Prop(null, type);
        }

        JavaType elementType() {
            return type == null ? null : type.getContentType();
        }
    }

    /** Per-class name tables. Unknown Java names fall back to reflection on the class, then to the name itself. */
    private record ClassNames(FieldNameResolver resolver, Class<?> type, Map<String, Prop> javaNames,
                              Map<String, Prop> jsonNames) {

        static final ClassNames EMPTY = new ClassNames(null, null, Map.of(), Map.of());

        Prop byJava(String javaName) {
            Prop p = javaNames.get(javaName);
            if (p != null) {
                return p;
            }
            String display = type == null ? javaName : reflectiveErrorField(type, javaName, javaName);
            return new Prop(display, null);
        }

        Prop byJson(String jsonName) {
            return jsonNames.get(jsonName);
        }
    }
}
