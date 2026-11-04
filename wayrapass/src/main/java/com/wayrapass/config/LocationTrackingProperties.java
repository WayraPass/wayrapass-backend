package com.wayrapass.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.location")
@Validated
@Getter
@Setter
public class LocationTrackingProperties {

    @Min(1)
    private long staleAfterSeconds = 120;
}
