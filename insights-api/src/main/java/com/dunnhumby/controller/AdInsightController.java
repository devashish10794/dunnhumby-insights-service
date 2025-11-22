package com.dunnhumby.controller;

import java.time.LocalDate;

import com.dunnhumby.service.AdInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Ad Insight API", description = "Operations to retrieve Ad Campaign Insights")
@RestController
@RequestMapping("/ad")
@RequiredArgsConstructor
public class AdInsightController {

    private final AdInsightService adInsightService;

    @Operation(summary = "Get total clicks for a campaign", description = "Returns the number of customers who clicked on the ad.")
    @ApiResponse(responseCode = "200", description = "Click count retrieved", content = @Content(schema = @Schema(implementation = Long.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping("/{campaignId}/clicks")
    public ResponseEntity<Long> getClicks(@PathVariable String campaignId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return ResponseEntity.ok(adInsightService.getClicks(campaignId, startDate, endDate));
        } catch (Exception e) {
            log.error("Error retrieving clicks for campaign {}: {}", campaignId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Get total impressions for a campaign", description = "Returns the number of customers who viewed the ad.")
    @ApiResponse(responseCode = "200", description = "Impression count retrieved", content = @Content(schema = @Schema(implementation = Long.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping("/{campaignId}/impressions")
    public ResponseEntity<Long> getImpressions(@PathVariable String campaignId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return ResponseEntity.ok(adInsightService.getImpressions(campaignId, startDate, endDate));
        } catch (Exception e) {
            log.error("Error retrieving impressions for campaign {}: {}", campaignId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Get Click-to-Basket count", description = "Returns number of users who added a product to cart after clicking on the ad.")
    @ApiResponse(responseCode = "200", description = "Click-to-cart count retrieved", content = @Content(schema = @Schema(implementation = Long.class)))
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping("/{campaignId}/clickToBasket")
    public ResponseEntity<Long> getClickToBasket(@PathVariable String campaignId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return ResponseEntity.ok(adInsightService.getClickToCart(campaignId, startDate, endDate));
        } catch (Exception e) {
            log.error("Error retrieving click-to-cart for campaign {}: {}", campaignId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
