package pl.jakubtworek.marketplace.api.graphql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQlQueryGuardTest {
    @org.junit.jupiter.api.Test void variablesAndDefaultsCannotHideListCardinality() {
        var guard = new GraphQlQueryGuard();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> guard.verify("query($size:Int!){ products(first:$size){ id name } }", java.util.Map.of("size", 100)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> guard.verify("query($size:Int=100){ products(first:$size){ id name } }", java.util.Map.of()));
    }
    private final GraphQlQueryGuard guard = new GraphQlQueryGuard();

    @Test void evaluatesAliasesAndFragmentsFromActualAst() {
        assertThatCode(() -> guard.verify("query { first: order(id: \"1\") { ...Core } } fragment Core on Order { id status }"))
                .doesNotThrowAnyException();
    }

    @Test void rejectsExcessiveDepthBeforeCallingResolvers() {
        assertThatThrownBy(() -> guard.verify("query { a { b { c { d { e { f { g { h { i } } } } } } } } }"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("depth");
    }
}
