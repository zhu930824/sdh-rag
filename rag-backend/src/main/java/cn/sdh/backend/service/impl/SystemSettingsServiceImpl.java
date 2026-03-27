package cn.sdh.backend.service.impl;

import cn.sdh.backend.dto.SystemSettingsResponse;
import cn.sdh.backend.entity.SystemSettings;
import cn.sdh.backend.mapper.SystemSettingsMapper;
import cn.sdh.backend.service.SystemSettingsService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private final SystemSettingsMapper settingsMapper;

    @Override
    public SystemSettingsResponse getAll() {
        SystemSettingsResponse response = new SystemSettingsResponse();
        
        List<SystemSettings> settings = settingsMapper.selectAll();
        for (SystemSettings setting : settings) {
            Map<String, Object> value = JSON.parseObject(setting.getSettingValue(), Map.class);
            switch (setting.getSettingKey()) {
                case "basic":
                    response.setBasic(value);
                    break;
                case "model":
                    response.setModel(value);
                    break;
                case "notification":
                    response.setNotification(value);
                    break;
            }
        }
        
        return response;
    }

    @Override
    public Map<String, Object> getByKey(String key) {
        SystemSettings setting = settingsMapper.selectByKey(key);
        if (setting != null && setting.getSettingValue() != null) {
            return JSON.parseObject(setting.getSettingValue(), Map.class);
        }
        return new HashMap<>();
    }

    @Override
    public void updateByKey(String key, Map<String, Object> value) {
        SystemSettings setting = settingsMapper.selectByKey(key);
        if (setting != null) {
            setting.setSettingValue(JSON.toJSONString(value));
            settingsMapper.updateById(setting);
        } else {
            setting = new SystemSettings();
            setting.setSettingKey(key);
            setting.setSettingValue(JSON.toJSONString(value));
            settingsMapper.insert(setting);
        }
    }

    @Override
    public void updateAll(Map<String, Map<String, Object>> settings) {
        settings.forEach(this::updateByKey);
    }
}