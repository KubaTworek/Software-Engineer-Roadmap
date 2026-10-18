package com.example.ecommerce.promotion;

import com.example.ecommerce.promotion.dto.PromotionDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionEngineTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Test
    void priorityAndNonStackableRuleStopFurtherDiscounts() {
        PromotionRepository promotions = mock(PromotionRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        Promotion first = new Promotion("10 percent", PromotionType.PERCENT_OFF,
                new BigDecimal("10"), NOW.minusSeconds(60), NOW.plusSeconds(60));
        first.setPriority(1);
        first.setStackable(false);
        Promotion second = new Promotion("extra amount", PromotionType.AMOUNT_OFF,
                new BigDecimal("25"), null, null);
        second.setPriority(2);
        when(promotions.findByStatusOrderByPriorityAsc(PromotionStatus.ACTIVE))
                .thenReturn(List.of(second, first));

        PromotionEngine engine = new PromotionEngine(
                promotions, coupons, Clock.fixed(NOW, ZoneOffset.UTC));
        var response = engine.price(new PromotionDtos.PriceRequest(
                List.of(new PromotionDtos.PriceLine(1L, 2L, 3L, 1, new BigDecimal("100.00"))),
                null,
                BigDecimal.ZERO));

        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.adjustments()).extracting(PromotionDtos.PromotionAdjustment::promotionName)
                .containsExactly("10 percent");
    }

    @Test
    void clockExcludesPromotionOutsideItsWindow() {
        PromotionRepository promotions = mock(PromotionRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        Promotion expired = new Promotion("expired", PromotionType.AMOUNT_OFF,
                BigDecimal.TEN, NOW.minusSeconds(120), NOW.minusSeconds(1));
        when(promotions.findByStatusOrderByPriorityAsc(PromotionStatus.ACTIVE))
                .thenReturn(List.of(expired));

        var response = new PromotionEngine(promotions, coupons, Clock.fixed(NOW, ZoneOffset.UTC))
                .price(new PromotionDtos.PriceRequest(
                        List.of(new PromotionDtos.PriceLine(1L, 2L, 3L, 1, BigDecimal.TEN)),
                        null, BigDecimal.ZERO));

        assertThat(response.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
