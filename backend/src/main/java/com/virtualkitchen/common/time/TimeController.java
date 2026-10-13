package com.virtualkitchen.common.time;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/** Server clock for devices that stamp footage in server time (station phones). Public. */
@RestController
public class TimeController {

    private final Clock clock;

    public TimeController(Clock clock) {
        this.clock = clock;
    }

    public record ServerTime(long epochMillis) {
    }

    @GetMapping("/api/time")
    public ServerTime now() {
        return new ServerTime(clock.millis());
    }
}
