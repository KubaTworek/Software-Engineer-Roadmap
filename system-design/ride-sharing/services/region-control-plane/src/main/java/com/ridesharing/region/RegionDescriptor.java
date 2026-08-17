package com.ridesharing.region;

public record RegionDescriptor(String regionId, String status, int weight, boolean acceptsWrites, String kafkaCluster, String databaseRole) {}
