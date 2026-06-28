package com.hypno.hypnovibe.app.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import com.hypno.hypnovibe.app.manager.ConfigManager;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.repository.IConfigRepository;
import com.hypno.hypnovibe.infrastructure.io.JsonFileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public class ConfigVM extends AndroidViewModel {
    private final ConfigManager manager;
    private final MutableStateFlow<List<DeviceConfig>> configs;

    public ConfigVM(Application app) {
        super(app);
        configs = StateFlowKt.MutableStateFlow(new ArrayList<>());
        JsonFileRepository jsonRepo = new JsonFileRepository(app, "configs");
        this.manager = new ConfigManager(new ConfigRepo(jsonRepo));
    }

    public StateFlow<List<DeviceConfig>> getConfigs() { return configs; }
    public void loadConfigs() { configs.setValue(manager.listConfigs()); }
    public void createConfig(String name, List<DeviceConfig.ChannelDef> channels) { manager.createConfig(name, channels); loadConfigs(); }
    public void updateConfig(DeviceConfig config) { manager.updateConfig(config); loadConfigs(); }
    public void deleteConfig(String id) { manager.deleteConfig(id); loadConfigs(); }

    static class ConfigRepo implements IConfigRepository {
        private final JsonFileRepository r;
        ConfigRepo(JsonFileRepository r) { this.r = r; }
        public List<DeviceConfig> listAll() { return r.listAll("*.json", DeviceConfig.class); }
        public Optional<DeviceConfig> findById(String id) { return r.findById(id, DeviceConfig.class); }
        public void save(DeviceConfig c) { c.touch(); r.save(c.getId(), c); }
        public void delete(String id) { r.delete(id); }
    }
}
