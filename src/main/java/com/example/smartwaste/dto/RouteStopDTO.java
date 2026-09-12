package com.example.smartwaste.dto;

import lombok.Data;

@Data
public class RouteStopDTO {
    private Long id;
    private int sequenceOrder;
    private String binCode;
    private String locationName;
}
