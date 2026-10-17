package com.example.smartwaste.dto;

import java.util.List;

public record RoutePlanResult(
        int candidateBins,
        List<CollectionRouteDTO> routes,
        List<String> unassignedBinCodes,
        String message
) {}
