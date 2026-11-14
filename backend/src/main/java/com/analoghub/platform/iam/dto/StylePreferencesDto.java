package com.analoghub.platform.iam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StylePreferencesDto(
        @Size(max = 20)
        List<String> favoriteFormats,
        @Size(max = 20)
        List<String> favoriteProcesses,
        @Size(max = 30)
        List<String> preferredColorTones,
        @Size(max = 30)
        List<String> favoriteCameras,
        @Size(max = 64)
        String shootingLevel
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (favoriteFormats != null) {
            map.put("favoriteFormats", List.copyOf(favoriteFormats));
        }
        if (favoriteProcesses != null) {
            map.put("favoriteProcesses", List.copyOf(favoriteProcesses));
        }
        if (preferredColorTones != null) {
            map.put("preferredColorTones", List.copyOf(preferredColorTones));
        }
        if (favoriteCameras != null) {
            map.put("favoriteCameras", List.copyOf(favoriteCameras));
        }
        if (shootingLevel != null && !shootingLevel.isBlank()) {
            map.put("shootingLevel", shootingLevel.trim());
        }
        return map;
    }

    public static StylePreferencesDto fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return new StylePreferencesDto(
                extractStringList(map.get("favoriteFormats")),
                extractStringList(map.get("favoriteProcesses")),
                extractStringList(map.get("preferredColorTones")),
                extractStringList(map.get("favoriteCameras")),
                map.get("shootingLevel") != null ? map.get("shootingLevel").toString() : null
        );
    }

    private static List<String> extractStringList(Object raw) {
        if (!(raw instanceof Collection<?> col)) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (Object item : col) {
            if (item != null) {
                list.add(item.toString());
            }
        }
        return List.copyOf(list);
    }
}
