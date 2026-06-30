package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import com.hypno.hypnovibe.app.manager.ConfigManager;
import com.hypno.hypnovibe.app.manager.DeviceTypeRegistry;
import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.repository.IConfigRepository;
import com.hypno.hypnovibe.infrastructure.io.JsonFileRepository;
import java.util.ArrayList;
import java.util.List;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public class ConfigVM extends AndroidViewModel {
    /** 静态共享的 DeviceTypeRegistry（供 UI 层不持有 ConfigVM 时使用） */
    public static final DeviceTypeRegistry SHARED_REGISTRY = new DeviceTypeRegistry();

    private final ConfigManager manager;
    private final DeviceTypeRegistry typeRegistry;
    private final MutableStateFlow<List<DeviceConfig>> configs;

    public ConfigVM(Application app) {
        super(app);
        configs = StateFlowKt.MutableStateFlow(new ArrayList<>());
        JsonFileRepository jsonRepo = new JsonFileRepository(app, "configs");
        this.typeRegistry = SHARED_REGISTRY;
        this.manager = new ConfigManager(new ConfigRepo(jsonRepo), typeRegistry);
    }

    public StateFlow<List<DeviceConfig>> getConfigs() { return configs; }
    public void loadConfigs() { configs.setValue(manager.listConfigs()); }
    public void createConfig(String name, List<DeviceConfig.ChannelDef> channels) { manager.createConfig(name, channels); loadConfigs(); }
    public void updateConfig(DeviceConfig config) { manager.updateConfig(config); loadConfigs(); }
    public void deleteConfig(String id) { manager.deleteConfig(id); loadConfigs(); }
    public List<String> validate(DeviceConfig config) { return manager.validate(config); }

    /** 获取可用的设备类型列表（供 UI 下拉菜单） */
    public List<DeviceTypeDescriptor> getConfigurableDeviceTypes() {
        return typeRegistry.getConfigurableTypes();
    }

    public DeviceTypeDescriptor getDeviceTypeInfo(String deviceType) {
        return typeRegistry.getTypeInfo(deviceType);
    }

    private static class ConfigRepo implements IConfigRepository {
        private final JsonFileRepository repo;
        ConfigRepo(JsonFileRepository repo) { this.repo = repo; }
        public List<DeviceConfig> listAll() { return repo.listAll(".*\\.hvconfig", DeviceConfig.class); }
        public java.util.Optional<DeviceConfig> findById(String id) { return repo.findById(id, DeviceConfig.class); }
        public void save(DeviceConfig config) { repo.save(config.getId(), config); }
        public void delete(String id) { repo.delete(id); }
    }
}
