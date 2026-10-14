package com.abhi1kush.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-context MockMvc tests for the sample endpoints (run by surefire via the *IT suffix filter below). */
@SpringBootTest
@AutoConfigureMockMvc
class ApiEndpointsIT {

    static final String VALID_USER = """
            {"name":"Abhishek","email":"abhi@example.com","phone":"+919876543210","age":30,
             "nick_name":"abhi","birth_date":"1995-06-15"}""";

    static final String VALID_ORDER = """
            {"order_id":"ORD-123456","customer_email":"abhi@example.com",
             "shipping_address":{"street":"MG Road","city":"Bengaluru","zip_code":"560001"},
             "items":[{"sku_code":"SKU-1","qty":2,"price":499.99},{"sku_code":"SKU-2","qty":1,"price":99.50}],
             "delivery_date":"%s","notes":"Leave at the door"}""".formatted(LocalDate.now().plusDays(3));

    @Autowired
    MockMvc mvc;

    ResultActions postJson(String path, String body) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(body == null ? req : req.content(body));
    }

    void expectError(ResultActions r, int status, String code, String description) throws Exception {
        r.andExpect(status().is(status))
                .andExpect(jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.errorDescription").value(description));
    }

    @Test
    void createUserSuccess() throws Exception {
        postJson("/api/users", VALID_USER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Abhishek"))
                .andExpect(jsonPath("$.birth_date").value("1995-06-15"))
                .andExpect(jsonPath("$", not(hasKey("errorCode"))));
    }

    @Test
    void createUserOptionalFieldsMayBeNullEmptyOrAbsent() throws Exception {
        postJson("/api/users", """
                {"name":"A","email":"a@b.co","age":18,"nick_name":"","birth_date":null}""")
                .andExpect(status().isCreated());
    }

    @Test
    void userMandatoryFieldsGrouped() throws Exception {
        expectError(postJson("/api/users", "{\"age\":30}"), 400,
                "E_MANDATORY_MISSING", "Mandatory fields missing: {email, name}");
    }

    @Test
    void userAgeMissingUsesConstraintDefault() throws Exception {
        expectError(postJson("/api/users", "{\"name\":\"A\",\"email\":\"a@b.co\"}"), 400,
                "E_MANDATORY_MISSING", "Mandatory fields missing: {age}");
    }

    @Test
    void userFormatErrorsOutrankLengthAndRange() throws Exception {
        expectError(postJson("/api/users", """
                        {"name":"A","email":"not-an-email","phone":"12","age":10,"nick_name":"ab"}"""), 400,
                "E_INVALID_FORMAT", "Invalid format for fields: {email, mobile_number}");
    }

    @Test
    void userInvalidDate() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("1995-06-15", "2024-02-30")), 400,
                "E_INVALID_DATE", "Invalid date for fields: {birth_date}");
    }

    @Test
    void userFutureBirthDateRejected() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("1995-06-15", LocalDate.now().plusDays(1).toString())),
                400, "E_INVALID_DATE", "Invalid date for fields: {birth_date}");
    }

    @Test
    void userNicknameTooShort() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("\"abhi\"", "\"ab\"")), 400,
                "E_MIN_LENGTH_NOT_MET", "Length is less than allowed length for fields: {nick_name}");
    }

    @Test
    void userNicknameTooLong() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("\"abhi\"", "\"" + "a".repeat(21) + "\"")), 400,
                "E_MAX_LENGTH_EXCEEDED", "Max length exceeded for fields: {nick_name}");
    }

    @Test
    void userAgeOutOfRange() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("30", "150")), 400,
                "E_OUT_OF_RANGE", "Value out of range for fields: {age}");
    }

    @Test
    void malformedJson() throws Exception {
        expectError(postJson("/api/users", "{\"name\":"), 400, "E_MALFORMED_JSON", "Malformed JSON request body");
    }

    @Test
    void emptyBody() throws Exception {
        expectError(postJson("/api/users", null), 400, "E_EMPTY_BODY", "Request body is empty");
        expectError(postJson("/api/orders", ""), 400, "E_EMPTY_BODY", "Request body is empty");
    }

    @Test
    void typeMismatch() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("30", "\"thirty\"")), 400,
                "E_TYPE_MISMATCH", "Invalid type for fields: {age}");
    }

    @Test
    void unknownFieldRejected() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("\"age\"", "\"role\":\"admin\",\"age\"")), 400,
                "E_UNKNOWN_FIELD", "Unknown fields: {role}");
    }

    @Test
    void createOrderSuccess() throws Exception {
        postJson("/api/orders", VALID_ORDER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").value("ORD-123456"))
                .andExpect(jsonPath("$.shipping_address.city").value("Bengaluru"))
                .andExpect(jsonPath("$.items[1].sku_code").value("SKU-2"))
                .andExpect(jsonPath("$.items[0].price").value(499.99));
    }

    @Test
    void orderNestedAndListMandatoryFieldsGrouped() throws Exception {
        expectError(postJson("/api/orders", """
                        {"order_id":"ORD-123456","customer_email":"a@b.co",
                         "shipping_address":{"street":"MG Road","zip_code":"560001"},
                         "items":[{"sku_code":"A","qty":1,"price":1},{"qty":1,"price":1},{"sku_code":"C","price":1}]}"""), 400,
                "E_MANDATORY_MISSING", "Mandatory fields missing: {items[1].sku_code, items[2].qty, shipping_address.city}");
    }

    @Test
    void orderFormatErrors() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("ORD-123456", "123").replace("560001", "56A")), 400,
                "E_INVALID_FORMAT", "Invalid format for fields: {order_id, shipping_address.zip_code}");
    }

    @Test
    void orderListItemRange() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("\"qty\":2", "\"qty\":0")), 400,
                "E_OUT_OF_RANGE", "Value out of range for fields: {items[0].qty}");
    }

    @Test
    void orderEmptyItems() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replaceAll("\"items\":\\[.*?]", "\"items\":[]")), 400,
                "E_MANDATORY_MISSING", "Mandatory fields missing: {items}");
    }

    @Test
    void orderPastDeliveryDate() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace(LocalDate.now().plusDays(3).toString(), "2020-01-01")),
                400, "E_INVALID_DATE", "Invalid date for fields: {delivery_date}");
    }

    @Test
    void orderShortNotesUseOptionalMinSize() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("Leave at the door", "hi")), 400,
                "E_MIN_LENGTH_NOT_MET", "Length is less than allowed length for fields: {notes}");
    }

    @Test
    void orderNestedTypeMismatch() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("\"qty\":1", "\"qty\":\"one\"")), 400,
                "E_TYPE_MISMATCH", "Invalid type for fields: {items[1].qty}");
    }

    @Test
    void userTooLongNameAndTooShortNicknameMaxWins() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("Abhishek", "A".repeat(51)).replace("\"abhi\"", "\"ab\"")),
                400, "E_MAX_LENGTH_EXCEEDED", "Max length exceeded for fields: {name}");
    }

    @Test
    void userTooLongNameAndNicknameGrouped() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("Abhishek", "A".repeat(51))
                        .replace("\"abhi\"", "\"" + "n".repeat(21) + "\"")),
                400, "E_MAX_LENGTH_EXCEEDED", "Max length exceeded for fields: {name, nick_name}");
    }

    @Test
    void orderTooManyItemsIsMaxLengthOnCollection() throws Exception {
        String items = String.join(",", java.util.Collections.nCopies(51, "{\"sku_code\":\"S\",\"qty\":1,\"price\":1}"));
        expectError(postJson("/api/orders", VALID_ORDER.replaceAll("\"items\":\\[.*?]", "\"items\":[" + items + "]")),
                400, "E_MAX_LENGTH_EXCEEDED", "Max length exceeded for fields: {items}");
    }

    @Test
    void errorFieldOverrideIsUsedForPhone() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("+919876543210", "12")), 400,
                "E_INVALID_FORMAT", "Invalid format for fields: {mobile_number}");
    }

    @Test
    void javaVariableNamesNeverAppearInErrors() throws Exception {
        String body = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"order_id":"x","customer_email":"bad","shipping_address":{"street":"s","zip_code":"1"},
                         "items":[{"qty":1,"price":1}],"delivery_date":"2020-01-01"}"""))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("shipping_address.city", "items[0].sku_code")
                .doesNotContain("shippingAddress", "\"sku\"", "orderId", "customerEmail");
    }
}
