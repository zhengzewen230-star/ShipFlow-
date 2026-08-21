package com.shipflow.tracking.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrackingCallbackProperties.class)
public class TrackingCallbackConfiguration {
}
