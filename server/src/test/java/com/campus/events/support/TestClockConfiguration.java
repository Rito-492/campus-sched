package com.campus.events.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 固定 Clock 的测试配置（QA-01，03 §1）。
 *
 * <p>自动测试固定 Clock=2030-10-19T00:00:00Z、D=2030-10-20，
 * 严禁测试依赖实际当天，也绝不修改操作系统时间（01 基线 §8）。
 *
 * <p>用法：在 @SpringBootTest 测试类上 {@code @Import(TestClockConfiguration.class)}，
 * 业务代码只依赖 {@link Clock} bean 取当前时间；生产配置提供系统时间 Clock，
 * 本配置以 @Primary 覆盖为固定值。
 */
@TestConfiguration
public class TestClockConfiguration {

    /** 固定当前时刻（UTC）。 */
    public static final Instant FIXED_INSTANT = Instant.parse("2030-10-19T00:00:00Z");

    /** 页面与业务使用的时区（01 基线 §4：页面统一 Asia/Shanghai）。 */
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    /** 固定演示日期 D。 */
    public static final LocalDate DEMO_DATE = LocalDate.of(2030, 10, 20);

    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
