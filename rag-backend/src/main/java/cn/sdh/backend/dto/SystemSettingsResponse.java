package cn.sdh.backend.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SystemSettingsResponse {

    private Map<String, Object> basic;

    private Map<String, Object> model;

    private Map<String, Object> notification;
}