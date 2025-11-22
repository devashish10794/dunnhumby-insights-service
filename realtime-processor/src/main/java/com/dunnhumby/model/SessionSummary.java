package com.dunnhumby.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummary {
    private String userId;
    private long sessionStart;
    private long sessionEnd;
    private int viewCount;
    private int clickCount;
}
