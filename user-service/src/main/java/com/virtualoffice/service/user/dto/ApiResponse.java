package com.virtualoffice.service.user.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ApiResponse {

    private String status;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public ApiResponse(String message) {
        this.status = message;
    }

    public ApiResponse add(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    @JsonAnyGetter // Used to flatten the map
    public Map<String, Object> getFields() {
        return fields;
    }
}