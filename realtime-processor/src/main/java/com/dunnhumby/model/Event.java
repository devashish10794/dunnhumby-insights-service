package com.dunnhumby.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Event {
    private String userId;
    private String eventType;
    private long eventTime;
}
