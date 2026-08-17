package com.ridesharing.mvp.scaling;

public record CityShard(String cityId, String shardName, String jdbcUrl) {}
