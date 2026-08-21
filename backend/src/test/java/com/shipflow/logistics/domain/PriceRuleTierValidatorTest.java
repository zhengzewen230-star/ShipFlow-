package com.shipflow.logistics.domain;

import com.shipflow.logistics.domain.model.PriceRuleTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PriceRuleTierValidatorTest {
    @Test void acceptsContiguousFixedTiersWithUnboundedFinalTier() {
        assertThatCode(() -> PriceRuleTierValidator.validate(List.of(fixed(1, "0", "1"), fixed(2, "1", null)))).doesNotThrowAnyException();
    }

    @Test void rejectsGapsOrOverlaps() {
        assertThatIllegalArgumentException().isThrownBy(() -> PriceRuleTierValidator.validate(List.of(fixed(1, "0", "1"), fixed(2, "1.1", null))));
    }

    @Test void rejectsBoundedFinalTier() {
        assertThatIllegalArgumentException().isThrownBy(() -> PriceRuleTierValidator.validate(List.of(fixed(1, "0", "1"))));
    }

    private PriceRuleTier fixed(int no, String min, String max) {
        return new PriceRuleTier(no, new BigDecimal(min), max == null ? null : new BigDecimal(max), PriceRuleTier.BillingMode.FIXED, null, null, null, null, new BigDecimal("10.00"));
    }
}
