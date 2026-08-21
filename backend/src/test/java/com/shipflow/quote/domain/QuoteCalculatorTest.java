package com.shipflow.quote.domain;

import com.shipflow.logistics.domain.model.PriceRuleTier;
import com.shipflow.logistics.domain.model.PublishedPriceRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteCalculatorTest {

    private static final PublishedPriceRule RULE = new PublishedPriceRule(1L, 1L, 1, "Air", "USD",
            new BigDecimal("5000"), PublishedPriceRule.RoundingMode.CEILING, new BigDecimal("0.5"),
            LocalDateTime.of(2026, 1, 1, 0, 0), List.of(
                    new PriceRuleTier(1, BigDecimal.ZERO, new BigDecimal("2"), PriceRuleTier.BillingMode.FIXED,
                            null, null, null, null, new BigDecimal("10")),
                    new PriceRuleTier(2, new BigDecimal("2"), null, PriceRuleTier.BillingMode.FIRST_CONTINUE,
                            new BigDecimal("2"), new BigDecimal("12"), new BigDecimal("0.5"), new BigDecimal("3"), null)));

    @Test
    void usesRoundedVolumeWeightAndFirstContinueTier() {
        var result = QuoteCalculator.calculate(RULE, new BigDecimal("1.2"), new BigDecimal("20"),
                new BigDecimal("20"), new BigDecimal("20"));

        assertThat(result.volumeWeight()).isEqualByComparingTo("1.600");
        assertThat(result.chargeableWeight()).isEqualByComparingTo("2.0");
        assertThat(result.tierNo()).isEqualTo(2);
        assertThat(result.amount()).isEqualByComparingTo("12.00");
    }

    @Test
    void rejectsNonPositiveMeasurements() {
        assertThatThrownBy(() -> QuoteCalculator.calculate(RULE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
