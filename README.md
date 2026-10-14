# spring-validator

A reusable JSON request validator for Spring Boot 3.5 / Java 21. It parses a raw request body with the
Spring-managed Jackson `ObjectMapper`, validates it with Jakarta Bean Validation (Hibernate Validator), and
returns **one** prioritised, generic error, with every affected field grouped as `{a, b}`.

```java
ValidationResult<UserVO> r = jsonRequestValidator.validate(requestBody, UserVO.class);
// success: r.vo() = Optional[UserVO]       failure: r.errorCode()        = Optional[E_MANDATORY_MISSING]
//                                                    r.errorDescription() = Optional[Mandatory fields missing: {email, name}]
//                                                    r.httpStatusCode()   = Optional[400]
```

## Module layout

```
spring-validator/                 parent pom (packaging pom, imports spring-boot-dependencies 3.5.16)
├── validator-core/               the reusable library + Spring Boot auto-configuration
│   └── com.abhi1kush.validator
│       ├── core/                 JsonRequestValidator, ValidationResult, GenericErrorCode
│       ├── mapping/              ErrorCodeMapper, PropertiesErrorCodeMapper, ConstraintDefaults
│       ├── annotation/           @OptionalMinSize, @OptionalDate + validators
│       └── autoconfigure/        JsonRequestValidatorAutoConfiguration, ValidatorProperties
│       META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── api/                          sample REST controllers and VOs (depends on validator-core)
│   └── com.abhi1kush.api         controller/ (UserController, OrderController), vo/, web/ (ErrorResponse)
└── springboot-init/              @SpringBootApplication main class + application.yml (depends on api, validator-core)
```

**Why a separate `validator-core` module?** The validator has nothing to do with these particular endpoints.
As its own jar with an auto-configuration, any Spring Boot JSON API can add one dependency and inject
`JsonRequestValidator`. It does not pull in web or controller code (it depends only on
`spring-boot-autoconfigure`, Jackson and `spring-boot-starter-validation`), and it can be versioned and tested
on its own. It returns a plain `int` HTTP status rather than a Spring MVC type, so it also works outside servlet stacks.
`api` holds the controllers and VOs, and `springboot-init` only bootstraps and configures the application.

## Build & run

```bash
./mvnw verify                                  # 127 tests: 100 in validator-core, 27 in springboot-init
./mvnw -pl springboot-init spring-boot:run     # after ./mvnw install (or: java -jar springboot-init/target/springboot-init-0.1.0-SNAPSHOT.jar)
```

Requires JDK 21. The Maven wrapper pins Maven 3.9.9.

## Design

1. **Body checks**: a `null` or blank body, or the JSON literal `null`, gives `E_EMPTY_BODY`.
2. **Parsing** uses `objectMapper.readerFor(type)` with `FAIL_ON_UNKNOWN_PROPERTIES` switched per reader. The shared
   `ObjectMapper` is never mutated. Jackson exceptions map like this:
   * `UnrecognizedPropertyException` gives `E_UNKNOWN_FIELD` with the full path (e.g. `{address.planet}`)
   * `MismatchedInputException` / `InvalidFormatException` give `E_TYPE_MISMATCH` with the path (e.g. `{items[1].qty}`, root `{$}`)
   * any other parse error gives `E_MALFORMED_JSON`

   Jackson stops at the first binding problem, so these errors report one field.
3. **Validation**: `Validator.validate(vo)` runs, with cascading through `@Valid` on nested objects and list elements.
4. **Code resolution** for each violation:
   * If the constraint's `message` is a VO code (it matches `[A-Z][A-Z0-9]*(_[A-Z0-9]+)+`, e.g. `USR_001`), the
     `ErrorCodeMapper` maps it to a generic code.
   * If the message is not a VO code, or the code is not mapped, the default for the constraint type is used
     (`ConstraintDefaults`: `@NotNull/@NotBlank/@NotEmpty` → `E_MANDATORY_MISSING`, `@Pattern/@Email/@Digits`
     → `E_INVALID_FORMAT`, `@Size`/`@Length` → `E_MAX_LENGTH_EXCEEDED` or `E_MIN_LENGTH_NOT_MET` (see *Length errors*), `@OptionalMinSize` → `E_MIN_LENGTH_NOT_MET`, `@Min/@Max/@DecimalMin/…`
     → `E_OUT_OF_RANGE`, `@OptionalDate/@Past/@Future…` → `E_INVALID_DATE`, anything else → `E_INVALID_VALUE`).
5. **Priority & grouping**: the generic code with the highest priority wins. All property paths with that code
   are de-duplicated and sorted in a deterministic natural order (`address.city < items[2].qty < items[10].qty < name`),
   then inserted into the code's template as `{a, b}`.
6. **Result**: an immutable `record ValidationResult<T>(Optional<T> vo, Optional<String> errorCode,
   Optional<String> errorDescription, Optional<Integer> httpStatusCode)`. Its canonical constructor enforces
   "either vo or all error fields".

Other points:

* **Thread safety**: `JsonRequestValidator` is immutable. `ObjectReader` and the Jakarta `Validator` are thread-safe,
  and the constraint validators hold only immutable state after `initialize`. A concurrency test covers this.
* **No reflection hacks**: everything goes through the standard Jackson and Jakarta APIs (message template and
  constraint descriptor).
* **Constructor injection** is used everywhere. The auto-configuration uses `@ConditionalOnMissingBean`, so every bean can be replaced.

### Why the VO code goes in `message`

`@NotBlank(message = "USR_001")` works with every standard and custom constraint and needs no extra annotation
or payload class. A field can also carry several constraints with different codes (e.g. `USR_003` for blank and
`USR_004` for a bad email), which a field-level `@ErrorCode` could not express. Messages that are not codes
(e.g. the default `{jakarta.validation.constraints.NotBlank.message}`) still work, because they fall back to
the constraint-type default. The service always sees the generic code and generic message. VO codes are an
internal mapping key.

## Generic error codes and priorities

The higher the priority, the more it wins. Codes are defined in `GenericErrorCode`.

| Priority | Code                  | HTTP | Default message template                  |
|---------:|-----------------------|-----:|-------------------------------------------|
| 1000     | `E_EMPTY_BODY`        | 400  | `Request body is empty`                   |
| 900      | `E_MALFORMED_JSON`    | 400  | `Malformed JSON request body`             |
| 800      | `E_UNKNOWN_FIELD`     | 400  | `Unknown fields: {fields}`                |
| 700      | `E_TYPE_MISMATCH`     | 400  | `Invalid type for fields: {fields}`       |
| 600      | `E_MANDATORY_MISSING` | 400  | `Mandatory fields missing: {fields}`      |
| 500      | `E_INVALID_FORMAT`    | 400  | `Invalid format for fields: {fields}`     |
| 450      | `E_INVALID_DATE`      | 400  | `Invalid date for fields: {fields}`       |
| 410      | `E_MAX_LENGTH_EXCEEDED` | 400 | `Max length exceeded for fields: {fields}` |
| 400      | `E_MIN_LENGTH_NOT_MET`  | 400 | `Length is less than allowed length for fields: {fields}` |
| 300      | `E_OUT_OF_RANGE`      | 400  | `Value out of range for fields: {fields}` |
| 100      | `E_INVALID_VALUE`     | 400  | `Invalid value for fields: {fields}`      |

## Length errors

Length violations come in two categories:

* `E_MAX_LENGTH_EXCEEDED` (priority 410): the value is longer than allowed. It is ranked just above min so the
  result stays deterministic, and an over-long value is the stronger signal (truncation or abuse).
* `E_MIN_LENGTH_NOT_MET` (priority 400): the value is shorter than allowed.

Both sit between `E_INVALID_DATE` (450) and `E_OUT_OF_RANGE` (300).

* `@Size` and Hibernate `@Length` have both bounds, so the category is decided **per violation**. The validator
  takes the actual length of the rejected value and compares it with the constraint's `max` attribute: longer
  gives max, otherwise min. Length means characters for a `CharSequence`, elements for a `Collection`, entries
  for a `Map`, or the array length. For example, `@Size(max = 50) List<…> items` with 51 items gives
  `E_MAX_LENGTH_EXCEEDED: {items}`, and `@NotEmpty` still reports an empty list as mandatory.
* `@Size` ignores `null`, but `""` counts as length 0, so `@Size(min = 2)` rejects `""` as min-not-met.
  `@Size(max)` alone accepts `""`.
* `@OptionalMinSize` always gives `E_MIN_LENGTH_NOT_MET`.
* If a VO code is mapped explicitly, the mapping wins: a VO code can map to either category, whatever the actual length.
* If a field is too long and another is too short, max wins. Fields are grouped within the winning category only.

## Custom annotations

| Annotation | null / "" / absent | Otherwise | Whitespace |
|---|---|---|---|
| `@OptionalMinSize(n)` | valid | `length >= n` | **not trimmed**: `"   "` has length 3 and is checked like any other value |
| `@OptionalDate(pattern = "yyyy-MM-dd", when = ANY\|PAST\|PAST_OR_PRESENT\|FUTURE\|FUTURE_OR_PRESENT)` | valid | must parse with `ResolverStyle.STRICT` (`2024-02-30` and `2023-02-29` fail), plus the optional bound relative to today | not trimmed: `" "` and `" 2024-01-01"` are invalid |

`@OptionalDate` defaults `ERA` while parsing, so the familiar `yyyy` works under STRICT resolution as well as `uuuu`.
For an upper bound on an optional string, add a plain `@Size(max = …)` next to `@OptionalMinSize`.
`@Size` ignores `null`, and `""` counts as length 0, so it stays within `max`. Optional values therefore stay
valid (see `UserVO.nickname`). Misconfigured values (e.g. `@OptionalMinSize(-1)`) fail fast.

## Adding a VO with custom codes

```java
public record CustomerVO(
        @NotBlank(message = "CUS_001") String name,
        @NotBlank(message = "CUS_002") @Email(message = "CUS_003") String email,
        @OptionalMinSize(value = 3, message = "CUS_004") @Size(max = 20, message = "CUS_006") String nickname,
        @OptionalDate(when = OptionalDate.Bound.PAST, message = "CUS_005") String birthDate,
        @NotNull @Valid AddressVO address,          // nested: paths like address.city
        @NotEmpty List<@Valid ItemVO> items) {      // list: paths like items[0].qty
}

@PostMapping("/api/customers")
public ResponseEntity<Object> create(@RequestBody(required = false) String body) {
    return ValidationResponses.toResponse(validator.validate(body, CustomerVO.class), HttpStatus.CREATED);
}
```

Records are the natural fit for immutable VOs. For a class-style VO, use Lombok (`@Value @Builder @Jacksonized`,
see `OrderItemVO`), or `@Data @NoArgsConstructor` if it must be mutable.

## Configuring the mapping

```yaml
validator:
  fail-on-unknown-properties: true      # false = ignore unknown JSON properties
  error-mapping:                        # VO code -> generic code (wire code or enum name)
    CUS_001: E_MANDATORY_MISSING
    CUS_003: INVALID_FORMAT
    CUS_004: E_MIN_LENGTH_NOT_MET
    CUS_006: MAX_LENGTH_EXCEEDED
```

An unknown generic code in the mapping stops application startup. To use another source (database, per-tenant
rules …), declare a bean and the properties mapper backs off:

```java
@Bean
ErrorCodeMapper errorCodeMapper(MyRepo repo) {
    return voCode -> repo.findGenericCode(voCode).flatMap(GenericErrorCode::fromValue);
}
```

## Using it in another application

Add the dependency and inject `JsonRequestValidator`. The auto-configuration in `AutoConfiguration.imports` does
the rest:

```xml
<dependency>
    <groupId>com.abhi1kush</groupId>
    <artifactId>validator-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Sample endpoints: real curl output

These were captured from `./mvnw -pl springboot-init spring-boot:run` on 2026-10-14.

### Valid user → 201
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"Abhishek","email":"abhi@example.com","phone":"+919876543210","age":30,"nickname":"abhi","birthDate":"1995-06-15"}'
HTTP/1.1 201 
{"name":"Abhishek","email":"abhi@example.com","phone":"+919876543210","age":30,"nickname":"abhi","birthDate":"1995-06-15"}

```

### Mandatory fields grouped → E_MANDATORY_MISSING
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"age":30}'
HTTP/1.1 400 
{"errorCode":"E_MANDATORY_MISSING","errorDescription":"Mandatory fields missing: {email, name}"}

```

### Priority: format beats length and range
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"not-an-email","phone":"12","age":10,"nickname":"ab"}'
HTTP/1.1 400 
{"errorCode":"E_INVALID_FORMAT","errorDescription":"Invalid format for fields: {email, phone}"}

```

### @OptionalDate strict (2024-02-30)
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"a@b.co","age":30,"birthDate":"2024-02-30"}'
HTTP/1.1 400 
{"errorCode":"E_INVALID_DATE","errorDescription":"Invalid date for fields: {birthDate}"}

```

### @OptionalMinSize: nickname too short → E_MIN_LENGTH_NOT_MET
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"a@b.co","age":30,"nickname":"ab"}'
HTTP/1.1 400 
{"errorCode":"E_MIN_LENGTH_NOT_MET","errorDescription":"Length is less than allowed length for fields: {nickname}"}

```

### @Size(max): nickname too long → E_MAX_LENGTH_EXCEEDED
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"a@b.co","age":30,"nickname":"aaaaaaaaaaaaaaaaaaaaa"}'
HTTP/1.1 400 
{"errorCode":"E_MAX_LENGTH_EXCEEDED","errorDescription":"Max length exceeded for fields: {nickname}"}

```

### Too long + too short at once: max wins
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","email":"a@b.co","age":30,"nickname":"ab"}'
HTTP/1.1 400 
{"errorCode":"E_MAX_LENGTH_EXCEEDED","errorDescription":"Max length exceeded for fields: {name}"}

```

### Max length grouped
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","email":"a@b.co","age":30,"nickname":"aaaaaaaaaaaaaaaaaaaaa"}'
HTTP/1.1 400 
{"errorCode":"E_MAX_LENGTH_EXCEEDED","errorDescription":"Max length exceeded for fields: {name, nickname}"}

```

### @Min/@Max (constraint default, no VO code)
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"a@b.co","age":150}'
HTTP/1.1 400 
{"errorCode":"E_OUT_OF_RANGE","errorDescription":"Value out of range for fields: {age}"}

```

### Malformed JSON
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":'
HTTP/1.1 400 
{"errorCode":"E_MALFORMED_JSON","errorDescription":"Malformed JSON request body"}

```

### Empty body
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json'
HTTP/1.1 400 
{"errorCode":"E_EMPTY_BODY","errorDescription":"Request body is empty"}

```

### Type mismatch
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"a@b.co","age":"thirty"}'
HTTP/1.1 400 
{"errorCode":"E_TYPE_MISMATCH","errorDescription":"Invalid type for fields: {age}"}

```

### Unknown field
```console
$ curl -s -i -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"A","email":"a@b.co","age":30,"role":"admin"}'
HTTP/1.1 400 
{"errorCode":"E_UNKNOWN_FIELD","errorDescription":"Unknown fields: {role}"}

```

### Valid order → 201
```console
$ curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"orderId":"ORD-123456","customerEmail":"abhi@example.com","shippingAddress":{"street":"MG Road","city":"Bengaluru","zip":"560001"},"items":[{"sku":"SKU-1","qty":2,"price":499.99},{"sku":"SKU-2","qty":1,"price":99.50}],"deliveryDate":"2030-01-01","notes":"Leave at the door"}'
HTTP/1.1 201 
{"orderId":"ORD-123456","customerEmail":"abhi@example.com","shippingAddress":{"street":"MG Road","city":"Bengaluru","zip":"560001"},"items":[{"sku":"SKU-1","qty":2,"price":499.99},{"sku":"SKU-2","qty":1,"price":99.50}],"deliveryDate":"2030-01-01","notes":"Leave at the door"}

```

### Nested + list paths grouped
```console
$ curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"orderId":"ORD-123456","customerEmail":"a@b.co","shippingAddress":{"street":"MG Road","zip":"560001"},"items":[{"sku":"A","qty":1,"price":1},{"qty":1,"price":1},{"sku":"C","price":1}]}'
HTTP/1.1 400 
{"errorCode":"E_MANDATORY_MISSING","errorDescription":"Mandatory fields missing: {items[1].sku, items[2].qty, shippingAddress.city}"}

```

### Nested format errors
```console
$ curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"orderId":"123","customerEmail":"a@b.co","shippingAddress":{"street":"MG Road","city":"Pune","zip":"56A"},"items":[{"sku":"A","qty":1,"price":1}]}'
HTTP/1.1 400 
{"errorCode":"E_INVALID_FORMAT","errorDescription":"Invalid format for fields: {orderId, shippingAddress.zip}"}

```

### List element out of range
```console
$ curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"orderId":"ORD-123456","customerEmail":"a@b.co","shippingAddress":{"street":"MG Road","city":"Pune","zip":"411001"},"items":[{"sku":"A","qty":0,"price":1}]}'
HTTP/1.1 400 
{"errorCode":"E_OUT_OF_RANGE","errorDescription":"Value out of range for fields: {items[0].qty}"}

```

### Nested type mismatch
```console
$ curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"orderId":"ORD-123456","customerEmail":"a@b.co","shippingAddress":{"street":"MG Road","city":"Pune","zip":"411001"},"items":[{"sku":"A","qty":1,"price":1},{"sku":"B","qty":"one","price":1}]}'
HTTP/1.1 400 
{"errorCode":"E_TYPE_MISMATCH","errorDescription":"Invalid type for fields: {items[1].qty}"}

```

### Empty items list
```console
$ curl -s -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -d '{"orderId":"ORD-123456","customerEmail":"a@b.co","shippingAddress":{"street":"MG Road","city":"Pune","zip":"411001"},"items":[]}'
HTTP/1.1 400 
{"errorCode":"E_MANDATORY_MISSING","errorDescription":"Mandatory fields missing: {items}"}

```

## Tests

* `validator-core` (100 tests): each custom annotation (null, "", absent, valid, invalid, whitespace, bounds,
  custom pattern); empty / malformed / unknown-field / type-mismatch bodies; priority selection; grouping
  as `{a, b}` and de-duplication; nested and indexed list paths in numeric order; VO→generic mapping, fallback
  and a custom `ErrorCodeMapper`; max/min length categories (too long, too short, both at once, grouping per category, `@Size` and `@Length` with both bounds, empty string, lists, maps, arrays, VO-code override); path ordering; fail-fast mapping; `ValidationResult` invariants; thread safety;
  the auto-configuration (`ApplicationContextRunner`).
* `springboot-init` (27 tests): `@SpringBootTest` + MockMvc for every endpoint scenario above, the
  `fail-on-unknown-properties=false` policy, and the binding of the `application.yml` mapping.
