package com.analoghub.platform.aiassistant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record FilmStockAdvisorRequest(
        @NotBlank(message = "Mô tả bối cảnh chụp không được để trống")
        @Size(min = 5, max = 500, message = "Mô tả bối cảnh chụp phải từ 5 đến 500 ký tự")
        String shootingScenario,

        @NotNull(message = "Điều kiện ánh sáng (lightingCondition) là bắt buộc")
        LightingCondition lightingCondition,

        @NotNull(message = "Khổ film (filmFormat) là bắt buộc")
        FilmFormatType filmFormat,

        PreferredColorTone preferredColorTone,

        @DecimalMin(value = "50000", message = "Ngân sách tối đa mỗi cuộn film tối thiểu là 50,000 VND")
        @DecimalMax(value = "2000000", message = "Ngân sách tối đa mỗi cuộn film không vượt quá 2,000,000 VND")
        BigDecimal maxBudgetPerRollVnd,

        @Size(max = 32, message = "Tốc độ màn trập tối đa không vượt quá 32 ký tự")
        String cameraMaxShutterSpeed
) {

    public enum LightingCondition {
        BRIGHT_SUNLIGHT,
        OVERCAST_SHADE,
        GOLDEN_HOUR,
        INDOOR_WARM,
        NIGHT_NEON,
        MIXED_LIGHTING
    }

    public enum FilmFormatType {
        FMT_135,
        FMT_120,
        FMT_SHEET
    }

    public enum PreferredColorTone {
        WARM_PASTEL,
        VINTAGE_GREEN,
        VIVID_SATURATED,
        CINEMATIC_TUNGSTEN,
        HIGH_CONTRAST_BW,
        FINE_GRAIN_BW
    }
}
