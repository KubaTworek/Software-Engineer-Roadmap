package com.example.newsfeed.experiment;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ExperimentAssignmentId implements Serializable {
    private String experimentName;
    private UUID userId;

    public ExperimentAssignmentId() {
    }

    public ExperimentAssignmentId(String experimentName, UUID userId) {
        this.experimentName = experimentName;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExperimentAssignmentId that)) return false;
        return Objects.equals(experimentName, that.experimentName) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(experimentName, userId);
    }
}
