package com.wayrapass.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.simulator")
@Validated
@Getter
@Setter
public class SimulatorProperties {

    private boolean enabled = false;

    @Min(1)
    private long intervalSeconds = 5;

    @Min(1)
    private int interpolationStepsPerSegment = 5;
}
