package pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyAsScaledLongTest {

    @Test
    void shouldAddAmountsWithoutLosingSmallestCurrencyUnit() {
        MoneyAsScaledLong total = MoneyAsScaledLong.ofCents(1_099)
                .plus(MoneyAsScaledLong.ofCents(1));

        assertThat(total.cents()).isEqualTo(1_100);
    }

    @Test
    void shouldRejectNullAndArithmeticOverflow() {
        MoneyAsScaledLong amount = MoneyAsScaledLong.ofCents(Long.MAX_VALUE);

        assertThatNullPointerException().isThrownBy(() -> amount.plus(null));
        assertThatThrownBy(() -> amount.plus(MoneyAsScaledLong.ofCents(1)))
                .isInstanceOf(ArithmeticException.class);
    }
}
