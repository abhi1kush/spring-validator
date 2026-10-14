package com.abhi1kush.validator.mapping;

import com.abhi1kush.validator.core.GenericErrorCode;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link ErrorCodeMapper} backed by an immutable map, typically
 * {@code validator.error-mapping.*} from {@code application.yml}.
 * Values may be wire codes ({@code E_MANDATORY_MISSING}) or enum names ({@code MANDATORY_MISSING}).
 */
public final class PropertiesErrorCodeMapper implements ErrorCodeMapper {

    private final Map<String, GenericErrorCode> mapping;

    /**
     * @param rawMapping VO code to generic code (wire code or enum name)
     * @throws IllegalArgumentException if a value is not a known generic code (fail fast at startup)
     */
    public PropertiesErrorCodeMapper(Map<String, String> rawMapping) {
        this.mapping = rawMapping.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> GenericErrorCode.fromValue(e.getValue())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown generic error code '" + e.getValue() + "' for VO code '" + e.getKey() + "'"))));
    }

    @Override
    public Optional<GenericErrorCode> map(String voErrorCode) {
        return Optional.ofNullable(mapping.get(voErrorCode));
    }
}
