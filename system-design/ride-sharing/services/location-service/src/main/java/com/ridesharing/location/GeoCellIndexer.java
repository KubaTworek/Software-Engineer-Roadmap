package com.ridesharing.location;

import java.util.List;

public interface GeoCellIndexer {
    String cell(double lat, double lng);
    List<String> nearbyCells(double lat, double lng, int ringSize);
}
