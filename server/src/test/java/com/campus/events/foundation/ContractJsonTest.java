package com.campus.events.foundation;

import com.campus.events.contract.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.junit.jupiter.api.Assertions.*;

/** 用实际公共配置读取请求，防止数量、版本和授权枚举被隐式转换。 */
class ContractJsonTest {
    private static final String PLAN = """
            {"startAt":"2030-10-20T02:00:00Z","endAt":"2030-10-20T03:00:00Z",
             "setupMinutes":0,"teardownMinutes":0,
             "venueId":"00000000-0000-0000-0000-000000003101","equipment":[]}
            """;
    private final ObjectMapper mapper = configuredMapper();

    private static ObjectMapper configuredMapper() {
        var builder = Jackson2ObjectMapperBuilder.json();
        new FoundationConfiguration().contractJson().customize(builder);
        return builder.build();
    }

    static Stream<Arguments> fractionalRequests() {
        return Stream.of(
                Arguments.of(VersionCommand.class, "{\"expectedLockVersion\":0.9}"),
                Arguments.of(RegistrationCommand.class, "{\"action\":\"JOIN\",\"expectedVersionNo\":1.9}"),
                Arguments.of(EquipmentNeed.class, "{\"resourceId\":\"00000000-0000-0000-0000-000000003201\",\"quantity\":1.9}"),
                Arguments.of(Plan.class, PLAN.replace("\"setupMinutes\":0", "\"setupMinutes\":0.9")),
                Arguments.of(Plan.class, PLAN.replace("\"teardownMinutes\":0", "\"teardownMinutes\":0.9")),
                Arguments.of(Proposal.class, "{\"baseVersionNo\":1.9,\"targetPlan\":" + PLAN + "}"),
                Arguments.of(ResourceCreate.class, """
                        {"allowTeacher":true,"allowStudent":true,"kind":"VENUE","name":"场地",
                         "category":"VENUE","capacity":80.9,"quantity":1,"openFrom":"08:00","openTo":"22:00"}
                        """));
    }

    @ParameterizedTest @MethodSource("fractionalRequests")
    void fractionalQuantitiesAndVersionsAreRejected(Class<?> type, String body) {
        assertThrows(JsonMappingException.class, () -> mapper.readValue(body, type));
    }

    static Stream<Arguments> numericEnums() {
        return Stream.of(Identity.class, Role.class, Permission.class, Decision.class, ResourceKind.class)
                .flatMap(type -> Stream.of("0", "\"0\"").map(value -> Arguments.of(type, value)));
    }

    @ParameterizedTest @MethodSource("numericEnums")
    void enumOrdinalsCannotReplaceContractNames(Class<?> type, String body) {
        assertThrows(JsonMappingException.class, () -> mapper.readValue(body, type));
    }

    @Test void validIntegersEnumsAndNullableCapacityRemainReadable() throws Exception {
        assertEquals(0, mapper.readValue("{\"expectedLockVersion\":0}", VersionCommand.class).expectedLockVersion());
        assertEquals(0, mapper.readValue(PLAN, Plan.class).setupMinutes());
        assertEquals(Identity.STUDENT, mapper.readValue("\"STUDENT\"", Identity.class));
        assertEquals(Role.ADMIN, mapper.readValue("\"ADMIN\"", Role.class));
        assertEquals(Permission.RESOURCE_MANAGE, mapper.readValue("\"RESOURCE_MANAGE\"", Permission.class));
        assertEquals(Decision.APPROVE, mapper.readValue("\"APPROVE\"", Decision.class));
        var resource = mapper.readValue("""
                {"allowTeacher":true,"allowStudent":false,"kind":"EQUIPMENT","name":"设备",
                 "category":"PROJECTOR","capacity":null,"quantity":2,"openFrom":"08:00","openTo":"22:00"}
                """, ResourceCreate.class);
        assertNull(resource.capacity());
        assertEquals(2, resource.quantity());
    }

    @Test void missingNullUnknownDuplicateAndTrailingInputRemainInvalid() {
        for (String body : new String[] {"{}", "{\"expectedLockVersion\":null}",
                "{\"expectedLockVersion\":0,\"actorId\":\"other\"}",
                "{\"expectedLockVersion\":0,\"expectedLockVersion\":1}", "{\"expectedLockVersion\":0} {}"}) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.readValue(body, VersionCommand.class), body);
        }
    }
}
