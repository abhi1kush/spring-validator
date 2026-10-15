package com.abhi1kush.validator.spring;

import com.abhi1kush.validator.core.GenericErrorCode;
import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that validator-core has zero side effects unless explicitly enabled, and that enabling it only adds
 * library-typed beans while leaving the application's ObjectMapper and Validator untouched.
 */
class IsolationTest {

    record Vo(@NotBlank String firstName) {
    }

    private final ObjectMapper appMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Validator appValidator = Validation.byProvider(HibernateValidator.class).configure()
            .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory().getValidator();

    /** A "real" app: its own ObjectMapper and Validator plus Boot's Jackson and Validation auto-configuration. */
    private ApplicationContextRunner app() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withBean("appObjectMapper", ObjectMapper.class, () -> appMapper)
                .withBean("appValidator", Validator.class, () -> appValidator);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJsonRequestValidator
    static class Enabled {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.abhi1kush.validator",
            // exclude this test tree's own @Configuration classes; we only want to scan the library's main code
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    static class ScansLibraryPackages {
    }

    private void assertAppBeansUntouched(ApplicationContext ctx) {
        assertThat(ctx.getBeansOfType(ObjectMapper.class)).containsOnlyKeys("appObjectMapper");
        assertThat(ctx.getBean(ObjectMapper.class)).isSameAs(appMapper);
        assertThat(appMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(appMapper.getPropertyNamingStrategy()).isNull();
        assertThat(appMapper.getRegisteredModuleIds()).isEmpty();
        assertThat(ctx.getBeansOfType(Validator.class)).containsOnlyKeys("appValidator");
        assertThat(ctx.getBean(Validator.class)).isSameAs(appValidator);
    }

    private static Set<String> libraryBeans(ApplicationContext ctx) {
        return Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(n -> {
                    Class<?> t = ctx.getType(n);
                    return t != null && t.getName().startsWith("com.abhi1kush.validator") && !t.getName().contains("Test");
                })
                .collect(Collectors.toSet());
    }

    @Test
    void libraryRegistersNoAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .noneMatch(c -> c.startsWith("com.abhi1kush"));
        assertThat(getClass().getClassLoader().getResource(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                .satisfiesAnyOf(u -> assertThat(u).isNull(), u -> assertThat(u.toString()).doesNotContain("validator-core"));
    }

    @Test
    void notEnabledMeansNoLibraryBeansAndNoBinding() {
        // an invalid mapping would fail startup if the library bound its properties
        app().withPropertyValues("spring-validator.error-mapping.X_001=E_TYPO", "validator.error-mapping.X_001=E_TYPO")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed()
                            .doesNotHaveBean(JsonRequestValidator.class)
                            .doesNotHaveBean(JsonRequestValidatorProperties.class)
                            .doesNotHaveBean(ErrorCodeMapper.class);
                    assertThat(libraryBeans(ctx)).isEmpty();
                    assertAppBeansUntouched(ctx);
                });
    }

    @Test
    void componentScanningLibraryPackagesRegistersNothing() {
        app().withUserConfiguration(ScansLibraryPackages.class).run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(JsonRequestValidator.class);
            assertThat(libraryBeans(ctx)).isEmpty();
            assertAppBeansUntouched(ctx);
        });
    }

    @Test
    void enabledAddsOnlyLibraryTypedBeansAndKeepsAppBeans() {
        Set<String> without = new HashSet<>();
        app().run(ctx -> without.addAll(Arrays.asList(ctx.getBeanDefinitionNames())));
        Properties sysBefore = (Properties) System.getProperties().clone();

        app().withUserConfiguration(Enabled.class).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(JsonRequestValidator.class)
                    .doesNotHaveBean(JsonRequestValidatorProperties.class);
            Set<String> added = new HashSet<>(Arrays.asList(ctx.getBeanDefinitionNames()));
            added.removeAll(without);
            added.remove(Enabled.class.getName());
            added.removeIf(n -> n.equals("isolationTest.Enabled"));
            assertThat(added).allSatisfy(n -> assertThat(ctx.getType(n).getName())
                    .as(n).startsWith("com.abhi1kush.validator"));
            // exactly the imported configuration class and the validator; no Boot infrastructure beans
            assertThat(added).containsExactlyInAnyOrder(JsonRequestValidatorConfiguration.class.getName(),
                    "jsonRequestValidator");
            assertAppBeansUntouched(ctx);

            JsonRequestValidator lib = ctx.getBean(JsonRequestValidator.class);
            // the library rejects unknown fields with its own copy ...
            assertThat(lib.validate("{\"firstName\":\"a\",\"x\":1}", Vo.class).errorCode()).contains("E_UNKNOWN_FIELD");
            // ... while the app's mapper keeps ignoring them
            assertThat(appMapper.readValue("{\"firstName\":\"a\",\"x\":1}", Vo.class).firstName()).isEqualTo("a");
            assertAppBeansUntouched(ctx);

            // later changes to the app mapper do not leak into the library (it holds a copy)
            appMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            assertThat(lib.validate("{}", Vo.class).errorDescription())
                    .contains("Mandatory fields missing: {firstName}");
        });
        assertThat(System.getProperties()).isEqualTo(sysBefore);
    }

    @Test
    void secondModuleContextIsUnaffected() {
        ObjectMapper moduleBMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        app().withUserConfiguration(Enabled.class).run(moduleA -> {
            assertThat(moduleA).hasSingleBean(JsonRequestValidator.class);
            new ApplicationContextRunner()
                    .withBean("moduleBMapper", ObjectMapper.class, () -> moduleBMapper)
                    .run(moduleB -> {
                        assertThat(moduleB).doesNotHaveBean(JsonRequestValidator.class);
                        assertThat(libraryBeans(moduleB)).isEmpty();
                        assertThat(moduleB.getBean(ObjectMapper.class)).isSameAs(moduleBMapper);
                        assertThat(moduleBMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
                    });
        });
    }

    @Test
    void namespacedPropertiesAreBoundOnlyWhenEnabled() {
        app().withUserConfiguration(Enabled.class)
                .withPropertyValues("spring-validator.error-mapping.X_001=E_TYPO").run(ctx -> assertThat(ctx).hasFailed());
        app().withUserConfiguration(Enabled.class)
                .withPropertyValues("spring-validator.fail-on-unknown-properties=false",
                        "validator.fail-on-unknown-properties=true")
                .run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                        .validate("{\"firstName\":\"a\",\"x\":1}", Vo.class).isValid()).isTrue());
    }

    @Test
    void appObjectMapperIsUsedAsTemplateUnlessDisabled() {
        appMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        app().withUserConfiguration(Enabled.class).run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                .validate("{}", Vo.class).errorDescription()).contains("Mandatory fields missing: {first_name}"));
        app().withUserConfiguration(Enabled.class).withPropertyValues("spring-validator.use-application-object-mapper=false")
                .run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                        .validate("{}", Vo.class).errorDescription()).contains("Mandatory fields missing: {firstName}"));
    }

    @Test
    void customErrorCodeMapperBeanIsUsed() {
        app().withUserConfiguration(Enabled.class)
                .withBean(ErrorCodeMapper.class, () -> code -> Optional.of(GenericErrorCode.OUT_OF_RANGE))
                .run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                        .validate("{}", MappedVo.class).errorCode()).contains("E_OUT_OF_RANGE"));
    }

    record MappedVo(@NotBlank(message = "M_001") String a) {
    }

    @Configuration(proxyBeanMethods = false)
    static class PerModuleValidators {

        @Bean
        JsonRequestValidator ordersValidator(Environment env) {
            return JsonRequestValidatorProperties.bind(env, "orders.validator").toBuilder().build();
        }

        @Bean
        JsonRequestValidator usersValidator(Environment env) {
            return JsonRequestValidatorProperties.bind(env, "users.validator").toBuilder().build();
        }

        @Bean
        String probe(@Qualifier("ordersValidator") JsonRequestValidator orders,
                     @Qualifier("usersValidator") JsonRequestValidator users) {
            return orders.validate("{}", MappedVo.class).errorCode().orElseThrow() + "/"
                    + users.validate("{}", MappedVo.class).errorCode().orElseThrow();
        }
    }

    @Test
    void multipleInstancesWithDifferentMappingsViaQualifiers() {
        app().withUserConfiguration(PerModuleValidators.class)
                .withPropertyValues("orders.validator.error-mapping.M_001=E_INVALID_FORMAT",
                        "users.validator.error-mapping.M_001=E_OUT_OF_RANGE")
                .run(ctx -> {
                    assertThat(ctx.getBean("probe", String.class)).isEqualTo("E_INVALID_FORMAT/E_OUT_OF_RANGE");
                    assertThat(ctx).doesNotHaveBean(JsonRequestValidatorProperties.class);
                    assertAppBeansUntouched(ctx);
                });
    }

    @Test
    void privateValidatorFactoryIsClosedWithTheContext() {
        JsonRequestValidator[] holder = new JsonRequestValidator[1];
        app().withUserConfiguration(Enabled.class).run(ctx -> holder[0] = ctx.getBean(JsonRequestValidator.class));
        assertThatThrownBy(() -> holder[0].validate("{}", Vo.class)).isInstanceOf(IllegalStateException.class);
        // the app's own validator is unaffected by the library's shutdown
        assertThat(appValidator.validate(new Vo(""))).hasSize(1);
    }

    @Test
    void enabledWithoutAnyAppBeansStillWorks() {
        new ApplicationContextRunner().withUserConfiguration(Enabled.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(JsonRequestValidator.class)
                    .doesNotHaveBean(ObjectMapper.class).doesNotHaveBean(Validator.class);
            assertThat(ctx.getBean(JsonRequestValidator.class).validate("{}", Vo.class).errorCode())
                    .contains("E_MANDATORY_MISSING");
        });
    }

    @Test
    void closingTheAppContextDoesNotAffectOtherInstances() {
        JsonRequestValidator standalone = JsonRequestValidator.builder().build();
        app().withUserConfiguration(Enabled.class).run(ConfigurableApplicationContext::close);
        assertThat(standalone.validate("{}", Vo.class).errorCode()).contains("E_MANDATORY_MISSING");
        standalone.close();
    }
}
