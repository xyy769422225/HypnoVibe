package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.repository.IConfigRepository;
import java.util.*;

public class ConfigManager {
    private static final Set<String> VALID_DEVICE_TYPES = new HashSet<>(
            Arrays.asList("coyote_v3", "coyote_v2", "lovense_vibrate", "generic_pwm"));

    private final IConfigRepository configRepository;

    public ConfigManager(IConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public List<DeviceConfig> listConfigs() {
        return configRepository.listAll();
    }

    public DeviceConfig createConfig(String name, List<DeviceConfig.ChannelDef> channels) {
        DeviceConfig config = new DeviceConfig(name, channels);
        configRepository.save(config);
        return config;
    }

    public void updateConfig(DeviceConfig config) {
        config.touch();
        configRepository.save(config);
    }

    public void deleteConfig(String id) {
        configRepository.delete(id);
    }

    public List<String> validate(DeviceConfig config) {
        List<String> errors = new ArrayList<>();
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            errors.add("name must not be empty");
        }
        List<DeviceConfig.ChannelDef> channels = config.getChannels();
        if (channels == null || channels.isEmpty()) {
            errors.add("channels must not be empty");
        } else {
            for (int i = 0; i < channels.size(); i++) {
                DeviceConfig.ChannelDef ch = channels.get(i);
                if (ch.getChannelName() == null || ch.getChannelName().trim().isEmpty()) {
                    errors.add("channel[" + i + "].channelName must not be empty");
                }
                if (ch.getDeviceType() == null || !VALID_DEVICE_TYPES.contains(ch.getDeviceType())) {
                    errors.add("channel[" + i + "].deviceType must be one of " + VALID_DEVICE_TYPES);
                }
                if (ch.getMaxStrength() <= ch.getMinStrength()) {
                    errors.add("channel[" + i + "].maxStrength must be greater than minStrength");
                }
            }
        }
        return errors;
    }
}
