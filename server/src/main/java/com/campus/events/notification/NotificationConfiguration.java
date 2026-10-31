package com.campus.events.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用通知消费者定时调度（CampusEventsApplication 未开启 @EnableScheduling，由 CM 在此开启）。 */
@Configuration
@EnableScheduling
public class NotificationConfiguration {}
