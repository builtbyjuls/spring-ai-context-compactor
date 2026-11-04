package com.builtbyjuls.compactor.report;

import java.util.Objects;

public record BenchmarkMetricRowDto(
        String metricKey,
        String metricName,
        String rawBaseline,
        String compactedResult,
        String measuredGain,
        String evidenceDetail
) {
    public BenchmarkMetricRowDto {
        Objects.requireNonNull(metricKey, "metricKey must not be null");
        Objects.requireNonNull(metricName, "metricName must not be null");
        Objects.requireNonNull(rawBaseline, "rawBaseline must not be null");
        Objects.requireNonNull(compactedResult, "compactedResult must not be null");
        Objects.requireNonNull(measuredGain, "measuredGain must not be null");
        Objects.requireNonNull(evidenceDetail, "evidenceDetail must not be null");
    }
}
