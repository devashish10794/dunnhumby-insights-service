package com.dunnhumby.controller;

import com.dunnhumby.producer.KinesisEventProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {
    private final KinesisEventProducer producer;

    public EventController(KinesisEventProducer producer) {
        this.producer = producer;
    }

    @PostMapping
    public ResponseEntity<String> publish(@RequestParam String stream, @RequestParam(defaultValue = "user-1") String key,
                                          @RequestBody String payload) {
        producer.sendEvent(stream, key, payload);
        return ResponseEntity.accepted().body("Event published to Kinesis.");
    }
}
