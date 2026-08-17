package com.ridesharing.location;

public final class LocationKeys {
    private LocationKeys() {}
    public static String driver(String driverId) { return "loc:driver:" + driverId; }
    public static String cell(String cityId, String h3Cell) { return "loc:city:" + cityId + ":h3:" + h3Cell; }
    public static String available(String cityId) { return "loc:city:" + cityId + ":available"; }
}
