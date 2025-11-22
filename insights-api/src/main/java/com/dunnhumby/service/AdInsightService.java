package com.dunnhumby.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.dunnhumby.integrators.AthenaQueryService;
import com.dunnhumby.repository.ScyllaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdInsightService {
    private final ScyllaRepository scyllaRepo;
    private final AthenaQueryService athenaService;

    public Long getClicks(String campaignId, LocalDate start, LocalDate end) {
        if (isRealTime(start, end)) {
            return scyllaRepo.getClicks(campaignId, start, end);
        } else {
            return athenaService.queryClicks(campaignId, start, end);
        }
    }

    public Long getImpressions(String campaignId, LocalDate start, LocalDate end) {
        if (isRealTime(start, end)) {
            return scyllaRepo.getImpressions(campaignId, start, end);
        } else {
            return athenaService.queryImpressions(campaignId, start, end);
        }
    }

    public Long getClickToCart(String campaignId, LocalDate start, LocalDate end) {
        if (isRealTime(start, end)) {
            return scyllaRepo.getClickToCart(campaignId, start, end);
        } else {
            return athenaService.queryClickToCart(campaignId, start, end);
        }
    }

    private boolean isRealTime(LocalDate start, LocalDate end) {
        return end == null || ChronoUnit.DAYS.between(end, LocalDate.now()) <= 30;
    }
}
