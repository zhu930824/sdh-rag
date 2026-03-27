package cn.sdh.backend.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SystemSettingsRequest {

    private Map<String, Object> settings;
}