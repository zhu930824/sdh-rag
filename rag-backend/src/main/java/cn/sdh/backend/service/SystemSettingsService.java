package cn.sdh.backend.service;

import cn.sdh.backend.dto.SystemSettingsResponse;

import java.util.Map;

public interface SystemSettingsService {

    SystemSettingsResponse getAll();

    Map<String, Object> getByKey(String key);

    void updateByKey(String key, Map<String, Object> value);

    void updateAll(Map<String, Map<String, Object>> settings);
}