package com.dunnhumby.repository;

import java.time.LocalDate;

public interface ScyllaRepository {
    Long getClicks(String campaignId, LocalDate start, LocalDate end);

    Long getImpressions(String campaignId, LocalDate start, LocalDate end);

    Long getClickToCart(String campaignId, LocalDate start, LocalDate end);
}
