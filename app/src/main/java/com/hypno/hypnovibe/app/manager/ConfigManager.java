package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.repository.IConfigRepository;
import java.util.*;

public class ConfigManager {
    private final IConfigRepository configRepository;
    private final DeviceTypeRegistry typeRegistry;

    public ConfigManager(IConfigRepository configRepository, DeviceTypeRegistry typeRegistry) {
        this.configRepository = configRepository;
        this.typeRegistry = typeRegistry;
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
            errors.add("配置名称不能为空");
        }
        List<DeviceConfig.ChannelDef> channels = config.getChannels();
        if (channels == null || channels.isEmpty()) {
            errors.add("至少需要定义一个通道");
            return errors;
        }

        int loveSpouseCount = 0;
        for (int i = 0; i < channels.size(); i++) {
            DeviceConfig.ChannelDef ch = channels.get(i);
            if (ch.getChannelName() == null || ch.getChannelName().trim().isEmpty()) {
                errors.add("通道名称不能为空");
            }

            DeviceTypeDescriptor desc = typeRegistry.getTypeInfo(ch.getDeviceType());
            if (desc == null) {
                errors.add("通道 \"" + ch.getChannelName() + "\" 的设备类型无效: " + ch.getDeviceType());
                continue;
            }

            if (ch.getDefaultStrength() < desc.getStrengthMin()
                    || ch.getDefaultStrength() > desc.getStrengthMax()) {
                errors.add("通道 \"" + ch.getChannelName() + "\" 默认强度必须在 "
                    + desc.getStrengthMin() + " 到 " + desc.getStrengthMax() + " 之间");
            }

            if ("love_spouse".equals(ch.getDeviceType())) {
                loveSpouseCount++;
                if (loveSpouseCount > 1) {
                    errors.add("一个配置中最多只能有 1 个 Love Spouse 通道");
                }
            }
        }
        return errors;
    }
}
