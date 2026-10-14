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
             "nickname":"abhi","birthDate":"1995-06-15"}""";

    static final String VALID_ORDER = """
            {"orderId":"ORD-123456","customerEmail":"abhi@example.com",
             "shippingAddress":{"street":"MG Road","city":"Bengaluru","zip":"560001"},
             "items":[{"sku":"SKU-1","qty":2,"price":499.99},{"sku":"SKU-2","qty":1,"price":99.50}],
             "deliveryDate":"%s","notes":"Leave at the door"}""".formatted(LocalDate.now().plusDays(3));

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
                .andExpect(jsonPath("$.birthDate").value("1995-06-15"))
                .andExpect(jsonPath("$", not(hasKey("errorCode"))));
    }

    @Test
    void createUserOptionalFieldsMayBeNullEmptyOrAbsent() throws Exception {
        postJson("/api/users", """
                {"name":"A","email":"a@b.co","age":18,"nickname":"","birthDate":null}""")
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
                        {"name":"A","email":"not-an-email","phone":"12","age":10,"nickname":"ab"}"""), 400,
                "E_INVALID_FORMAT", "Invalid format for fields: {email, phone}");
    }

    @Test
    void userInvalidDate() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("1995-06-15", "2024-02-30")), 400,
                "E_INVALID_DATE", "Invalid date for fields: {birthDate}");
    }

    @Test
    void userFutureBirthDateRejected() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("1995-06-15", LocalDate.now().plusDays(1).toString())),
                400, "E_INVALID_DATE", "Invalid date for fields: {birthDate}");
    }

    @Test
    void userOptionalSizeViolation() throws Exception {
        expectError(postJson("/api/users", VALID_USER.replace("\"abhi\"", "\"ab\"")), 400,
                "E_INVALID_LENGTH", "Invalid length for fields: {nickname}");
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
                .andExpect(jsonPath("$.orderId").value("ORD-123456"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Bengaluru"))
                .andExpect(jsonPath("$.items[1].sku").value("SKU-2"))
                .andExpect(jsonPath("$.items[0].price").value(499.99));
    }

    @Test
    void orderNestedAndListMandatoryFieldsGrouped() throws Exception {
        expectError(postJson("/api/orders", """
                        {"orderId":"ORD-123456","customerEmail":"a@b.co",
                         "shippingAddress":{"street":"MG Road","zip":"560001"},
                         "items":[{"sku":"A","qty":1,"price":1},{"qty":1,"price":1},{"sku":"C","price":1}]}"""), 400,
                "E_MANDATORY_MISSING", "Mandatory fields missing: {items[1].sku, items[2].qty, shippingAddress.city}");
    }

    @Test
    void orderFormatErrors() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("ORD-123456", "123").replace("560001", "56A")), 400,
                "E_INVALID_FORMAT", "Invalid format for fields: {orderId, shippingAddress.zip}");
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
                400, "E_INVALID_DATE", "Invalid date for fields: {deliveryDate}");
    }

    @Test
    void orderShortNotesUseOptionalMinSize() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("Leave at the door", "hi")), 400,
                "E_INVALID_LENGTH", "Invalid length for fields: {notes}");
    }

    @Test
    void orderNestedTypeMismatch() throws Exception {
        expectError(postJson("/api/orders", VALID_ORDER.replace("\"qty\":1", "\"qty\":\"one\"")), 400,
                "E_TYPE_MISMATCH", "Invalid type for fields: {items[1].qty}");
    }
}
