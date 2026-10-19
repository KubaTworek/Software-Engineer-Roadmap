package pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BigDecimalBenchmarkCorrectnessTest {

    @Test
    void stringAndScaledLongConstructionProduceTheSameDecimalTotal() {
        BigDecimalConstructionBenchmark benchmark = new BigDecimalConstructionBenchmark();
        benchmark.setup();

        assertThat(benchmark.constructFromString())
                .isEqualByComparingTo(benchmark.constructWithValueOfLongScale());
    }

    @Test
    void roundedBigDecimalAndScaledLongUseTheSameHalfUpRule() {
        BigDecimalHotLoopBenchmark benchmark = new BigDecimalHotLoopBenchmark();
        benchmark.setup();

        assertThat(benchmark.scaledLongThenConvertAtBoundary())
                .isEqualByComparingTo(benchmark.bigDecimalHotLoopWithRounding());
    }

    @Test
    void allMoneyRepresentationsPreserveTheSameAmount() {
        MoneyRepresentationBenchmark benchmark = new MoneyRepresentationBenchmark();
        benchmark.setup();

        BigDecimal decimalTotal = benchmark.sumBigDecimalAmounts();
        long cents = benchmark.sumPrimitiveCents();

        assertThat(benchmark.sumWrappedMoneyAmounts().cents()).isEqualTo(cents);
        assertThat(benchmark.sumPrimitiveCentsThenConvert()).isEqualByComparingTo(decimalTotal);
        assertThat(BigDecimal.valueOf(cents, 2)).isEqualByComparingTo(decimalTotal);
    }

    @Test
    void binaryDoubleConstructionRemainsACorrectnessExampleNotABenchmarkAlternative() {
        assertThat(new BigDecimal(0.1)).isNotEqualByComparingTo(new BigDecimal("0.1"));

        assertThat(Arrays.stream(BigDecimalConstructionBenchmark.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Benchmark.class))
                .map(Method::getName))
                .doesNotContain("constructFromDouble");
    }
}
