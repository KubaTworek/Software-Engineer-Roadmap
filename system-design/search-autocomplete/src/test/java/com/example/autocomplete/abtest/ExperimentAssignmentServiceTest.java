package com.example.autocomplete.abtest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentAssignmentServiceTest {
    @Test
    void shouldAssignStableVariant() {
        ExperimentAssignmentService service = new ExperimentAssignmentService();
        assertThat(service.assign("u1", "ranking-v6")).isEqualTo(service.assign("u1", "ranking-v6"));
    }
}
