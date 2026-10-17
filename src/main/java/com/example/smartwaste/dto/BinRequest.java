package com.example.smartwaste.dto;

import com.example.smartwaste.entity.WasteType;
import jakarta.validation.constraints.*;

public record BinRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "may only contain letters, digits, - and _")
        String binCode,
        @NotBlank @Size(max = 120) String locationName,
        @Size(max = 60) String zone,
        WasteType wasteType,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @Positive @Max(20000) Double capacity,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double currentFillPercentage
) {}
