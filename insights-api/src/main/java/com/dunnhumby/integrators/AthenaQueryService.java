package com.dunnhumby.integrators;

import java.time.LocalDate;

public interface AthenaQueryService {
    Long queryClicks(String campaignId, LocalDate start, LocalDate end);

    Long queryImpressions(String campaignId, LocalDate start, LocalDate end);

    Long queryClickToCart(String campaignId, LocalDate start, LocalDate end);
}
