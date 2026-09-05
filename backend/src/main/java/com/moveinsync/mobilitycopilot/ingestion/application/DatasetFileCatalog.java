package com.moveinsync.mobilitycopilot.ingestion.application;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class DatasetFileCatalog {

    public List<String> requiredFiles() {
        return List.of(
                "Ride_data _trip-may_2026.csv",
                "Ride_data _trip-June_2026.csv",
                "Ride_data _trip-July_2026.csv",
                "emp_Data.csv",
                "bill_data.csv",
                "trip_feedback.csv",
                "alerts_data.csv");
    }
}
