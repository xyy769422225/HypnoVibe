# Phase 2 详细设计：核心数据层 + 配置管理

> 阶段目标：实现领域实体、JSON 持久化、配置/播放列表 CRUD，UI 全部接入真实数据  
> 产出物：可运行的 APK，可创建设备配置和播放列表并持久化

---

## 目录

1. [领域层设计](#1-领域层设计)
2. [基础设施层设计](#2-基础设施层设计)
3. [应用层设计](#3-应用层设计)
4. [ViewModel 设计](#4-viewmodel-设计)
5. [UI 集成设计](#5-ui-集成设计)
6. [自动匹配文件流程](#6-自动匹配文件流程)
7. [验证标准](#7-验证标准)
8. [文件清单](#8-文件清单)

---

## 1. 领域层设计

### 1.1 实体类（全部 Java）

#### 1.1.1 DeviceConfig.java

设备配置实体，拥有唯一 ID，定义所有通道的名称/设备类型/强度范围。

```java
package com.hypno.hypnovibe.domain.entity;

import java.util.List;

public class DeviceConfig {
    private String id;              // UUID 自动生成
    private String name;            // 用户命名，如 "双郊狼配置"
    private List<ChannelDef> channels;
    private long createdAt;
    private long updatedAt;

    public static class ChannelDef {
        private String channelId;       // UUID
        private String channelName;     // 如 "后穴"
        private String deviceType;      // "coyote_v3"
        private int minStrength;
        private int maxStrength;
        private int defaultStrength;
    }
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | UUID 格式，配置唯一标识 |
| `name` | String | 用户自定义名称，如"双郊狼配置" |
| `channels` | List\<ChannelDef\> | 通道定义列表 |
| `createdAt` | long | 创建时间戳（毫秒） |
| `updatedAt` | long | 最后更新时间戳（毫秒） |
| `ChannelDef.channelId` | String | 通道 UUID |
| `ChannelDef.channelName` | String | 通道名称，如"后穴"、"前部" |
| `ChannelDef.deviceType` | String | 设备类型标识，如 "coyote_v3" |
| `ChannelDef.minStrength` | int | 最小强度 |
| `ChannelDef.maxStrength` | int | 最大强度 |
| `ChannelDef.defaultStrength` | int | 默认强度 |

#### 1.1.2 Playlist.java

播放列表实体，组织多首音频及其时间轴脚本。

```java
package com.hypno.hypnovibe.domain.entity;

import java.util.List;
import java.util.Map;

public class Playlist {
    private String id;                      // UUID
    private String name;                    // 如 "周五催眠合集"
    private String configId;                // 关联的配置ID
    private String playMode;                // LOOP_LIST / STOP_AT_END / LOOP_LAST
    private List<Track> tracks;
    private Map<String, ChannelMappingEntry> channelMapping;
    private long createdAt;
    private long updatedAt;

    public static class Track {
        private String trackId;             // UUID
        private String audioFilePath;       // 音频文件绝对路径
        private String audioTitle;          // 音频标题（从元数据读取）
        private String timelineScriptPath;  // null = 无时间轴
        private boolean autoMatched;        // 是否自动匹配到脚本
        private long durationMs;            // 音频时长（毫秒）
    }

    public static class ChannelMappingEntry {
        private String deviceAlias;         // 设备别名
        private String macAddress;          // 设备MAC地址
        private String timelineChannelId;   // 映射到的时间轴通道ID
        private String timelineChannelName; // 映射到的时间轴通道名称
    }
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | UUID 格式 |
| `name` | String | 用户自定义播放列表名 |
| `configId` | String | 关联的 DeviceConfig.id |
| `playMode` | String | 播放模式枚举值 |
| `tracks` | List\<Track\> | 曲目列表 |
| `channelMapping` | Map | 以 `设备ID:物理通道` 为 key 的映射表 |
| `Track.audioFilePath` | String | 用户选择的音频文件绝对路径 |
| `Track.audioTitle` | String | 从 MediaMetadataRetriever 读取的标题 |
| `Track.timelineScriptPath` | String | 关联的时间轴脚本路径，null = 无 |
| `Track.autoMatched` | boolean | 是否通过 FilePathResolver 自动匹配 |
| `Track.durationMs` | long | 音频时长毫秒数 |

#### 1.1.3 其他实体（Phase 2 仅建空壳）

以下实体在 Phase 2 中仅定义完整的 Java POJO 结构（字段/getter/setter），业务逻辑在后续 Phase 实现：

| 类名 | 说明 | Phase 实现 |
|------|------|-----------|
| `TimelineScript` | 时间轴脚本，引用 configId + 波形段列表 | Phase 6 |
| `WaveformFile` | 波形文件，强度-时间采样数据 | Phase 6 |
| `WaveformSegment` | 波形段，时间轴上引用一段波形文件 | Phase 6 |
| `PairedDevice` | 已配对设备信息（MAC/别名/类型） | Phase 4 |
| `StrengthPreset` | 强度预设（名称/各通道强度） | Phase 4 |
| `ChannelMapping` | 通道映射条目 | Phase 4 |

这些空壳实体包含完整的字段定义和 Lombok 风格的 getter/setter 方法，确保 JSON 序列化/反序列化可用。具体结构参见《功能设计文档》§12 文件格式规范。

### 1.2 值对象

| 类 | 说明 |
|------|------|
| `ConfigId` | 包装 String，含 `isValid()` 校验 UUID 格式 |
| `ChannelId` | 包装 String |
| `Strength` | 包装 int，含 `clamp(min, max)` 方法将强度钳制在合法范围 |
| `TimeRange` | `long startMs, long endMs`，含 `contains(ms)` 判断时间点是否在范围内 |

```java
// Strength.java 示例
public class Strength {
    private final int value;

    public Strength(int value) {
        this.value = value;
    }

    public int getValue() { return value; }

    public int clamp(int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
```

```java
// TimeRange.java 示例
public class TimeRange {
    private final long startMs;
    private final long endMs;

    public TimeRange(long startMs, long endMs) {
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public long getStartMs() { return startMs; }
    public long getEndMs() { return endMs; }

    public boolean contains(long ms) {
        return ms >= startMs && ms <= endMs;
    }
}
```

### 1.3 Repository 接口

Phase 2 仅完整实现 `IConfigRepository` 和 `IPlaylistRepository`，其余接口留空壳供后续 Phase 实现。

```java
package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import java.util.List;
import java.util.Optional;

public interface IConfigRepository {
    List<DeviceConfig> listAll();
    Optional<DeviceConfig> findById(String id);
    void save(DeviceConfig config);
    void delete(String id);
}
```

```java
package com.hypno.hypnovibe.domain.repository;

import com.hypno.hypnovibe.domain.entity.Playlist;
import java.util.List;
import java.util.Optional;

public interface IPlaylistRepository {
    List<Playlist> listAll();
    Optional<Playlist> findById(String id);
    void save(Playlist playlist);
    void delete(String id);
}
```

**Phase 2 留空壳接口：**

| 接口名 | 对应实体 | 实现 Phase |
|--------|---------|-----------|
| `ITimelineRepository` | TimelineScript | Phase 6 |
| `IWaveformRepository` | WaveformFile | Phase 6 |
| `IDeviceRepository` | PairedDevice | Phase 4 |
| `IPresetRepository` | StrengthPreset | Phase 4 |

---

## 2. 基础设施层设计

### 2.1 JsonFileRepository（通用 JSON 文件仓储）

基于 Gson 的通用 JSON 文件 CRUD 实现，是所有文件持久化的基础。

```java
package com.hypno.hypnovibe.infrastructure.io;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.util.*;

public class JsonFileRepository {
    private final File baseDir;
    private final Gson gson;

    public JsonFileRepository(Context context, String subDir) {
        this.baseDir = new File(context.getFilesDir(), subDir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * 列出目录下所有 JSON 文件的反序列化对象
     */
    public <T> List<T> listAll(String glob, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        File[] files = baseDir.listFiles((dir, name) -> name.matches(glob));
        if (files == null) return result;
        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                T obj = gson.fromJson(reader, clazz);
                if (obj != null) result.add(obj);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 按 ID 查找 (文件名 = {id}.json)
     */
    public <T> Optional<T> findById(String id, Class<T> clazz) {
        File file = getFile(id);
        if (!file.exists()) return Optional.empty();
        try (Reader reader = new FileReader(file)) {
            return Optional.ofNullable(gson.fromJson(reader, clazz));
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * 保存对象到 {id}.json
     */
    public <T> void save(String id, T object) {
        File file = getFile(id);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(object, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除 {id}.json
     */
    public void delete(String id) {
        File file = getFile(id);
        if (file.exists()) {
            file.delete();
        }
    }

    private File getFile(String id) {
        return new File(baseDir, id + ".json");
    }
}
```

### 2.2 FilePathResolver

根据音频文件路径，自动查找同文件夹下的匹配时间轴脚本。

```java
package com.hypno.hypnovibe.infrastructure.io;

import java.io.File;
import java.util.Optional;

public class FilePathResolver {
    /**
     * 根据音频文件路径，查找同文件夹下的匹配时间轴脚本
     *
     * 匹配规则（按优先级）：
     * 1. 相同文件名、不同后缀：audio.mp3 → audio.hvscript
     * 2. 同文件夹下最新修改的 .hvscript（兜底匹配）
     * 3. 未匹配 → 返回空
     *
     * @param audioPath 音频文件绝对路径，如 /storage/music/song.mp3
     * @return 匹配到的 .hvscript 文件路径，未匹配返回 Optional.empty()
     */
    public static Optional<String> findTimelineForAudio(String audioPath) {
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) return Optional.empty();

        File parentDir = audioFile.getParentFile();
        if (parentDir == null) return Optional.empty();

        String audioName = audioFile.getName();
        int dotIndex = audioName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? audioName.substring(0, dotIndex) : audioName;

        // 规则1：同名 .hvscript
        File exactMatch = new File(parentDir, baseName + ".hvscript");
        if (exactMatch.exists()) {
            return Optional.of(exactMatch.getAbsolutePath());
        }

        // 规则2：同文件夹下最新 .hvscript
        File[] scripts = parentDir.listFiles((dir, name) -> name.endsWith(".hvscript"));
        if (scripts != null && scripts.length > 0) {
            File latest = scripts[0];
            for (File f : scripts) {
                if (f.lastModified() > latest.lastModified()) {
                    latest = f;
                }
            }
            return Optional.of(latest.getAbsolutePath());
        }

        return Optional.empty();
    }
}
```

### 2.3 目录结构

```
{context.filesDir}/
├── configs/
│   └── {configId}.hvconfig
├── playlists/
│   └── {playlistId}.hvplaylist
├── timelines/
│   └── {scriptId}.hvscript
├── waveforms/
│   └── {waveformId}.hvwav
└── presets/
    └── {presetId}.json
```

### 2.4 DevicePrefs（Phase 4 使用，Phase 2 仅建空壳）

```java
package com.hypno.hypnovibe.infrastructure.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import com.hypno.hypnovibe.domain.entity.PairedDevice;
import java.util.Collections;
import java.util.List;

public class DevicePrefs {
    private final SharedPreferences prefs;

    public DevicePrefs(Context context) {
        this.prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE);
    }

    public void saveDevice(PairedDevice device) {
        /* Phase 4 实现 */
    }

    public List<PairedDevice> loadAll() {
        /* Phase 4 实现 */
        return Collections.emptyList();
    }

    public void removeDevice(String mac) {
        /* Phase 4 实现 */
    }
}
```

---

## 3. 应用层设计

### 3.1 ConfigManager

配置的 CRUD 及校验管理器。

```java
package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import com.hypno.hypnovibe.domain.repository.IConfigRepository;
import java.util.*;

public class ConfigManager {
    private final IConfigRepository repo;

    public ConfigManager(IConfigRepository repo) {
        this.repo = repo;
    }

    /**
     * 列出所有配置
     */
    public List<DeviceConfig> listConfigs() {
        return repo.listAll();
    }

    /**
     * 创建新配置（自动生成 UUID + 时间戳）
     */
    public DeviceConfig createConfig(String name, List<DeviceConfig.ChannelDef> channels) {
        DeviceConfig config = new DeviceConfig();
        config.setId(UUID.randomUUID().toString());
        config.setName(name);
        config.setChannels(channels);
        long now = System.currentTimeMillis();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        repo.save(config);
        return config;
    }

    /**
     * 更新配置（自动更新 updatedAt）
     */
    public void updateConfig(DeviceConfig config) {
        config.setUpdatedAt(System.currentTimeMillis());
        repo.save(config);
    }

    /**
     * 删除配置
     */
    public void deleteConfig(String id) {
        repo.delete(id);
    }

    /**
     * 根据 ID 查找
     */
    public Optional<DeviceConfig> findById(String id) {
        return repo.findById(id);
    }

    /**
     * 验证通道定义合法性
     * @return 错误信息列表，空列表表示验证通过
     */
    public List<String> validate(DeviceConfig config) {
        List<String> errors = new ArrayList<>();

        // 1. 名称非空
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            errors.add("配置名称不能为空");
        }

        // 2. 通道列表非空
        if (config.getChannels() == null || config.getChannels().isEmpty()) {
            errors.add("至少需要定义一个通道");
            return errors;
        }

        // 3. 单个通道校验
        Set<String> channelIds = new HashSet<>();
        Set<String> channelNames = new HashSet<>();
        for (DeviceConfig.ChannelDef ch : config.getChannels()) {
            // channelName 非空
            if (ch.getChannelName() == null || ch.getChannelName().trim().isEmpty()) {
                errors.add("通道名称不能为空");
            }

            // deviceType 为已知类型
            if (ch.getDeviceType() == null || !isKnownDeviceType(ch.getDeviceType())) {
                errors.add("通道 \"" + ch.getChannelName() + "\" 的设备类型无效: " + ch.getDeviceType());
            }

            // 强度范围合法
            if (ch.getMinStrength() < 0) {
                errors.add("通道 \"" + ch.getChannelName() + "\" 最小强度不能小于 0");
            }
            if (ch.getMaxStrength() <= ch.getMinStrength()) {
                errors.add("通道 \"" + ch.getChannelName() + "\" 最大强度必须大于最小强度");
            }
            if (ch.getDefaultStrength() < ch.getMinStrength() || ch.getDefaultStrength() > ch.getMaxStrength()) {
                errors.add("通道 \"" + ch.getChannelName() + "\" 默认强度必须在最小和最大之间");
            }

            // channelId 唯一性
            if (ch.getChannelId() != null && !channelIds.add(ch.getChannelId())) {
                errors.add("通道ID重复: " + ch.getChannelId());
            }

            // channelName 唯一性（建议但不强制）
            if (ch.getChannelName() != null && !channelNames.add(ch.getChannelName())) {
                errors.add("通道名称重复: " + ch.getChannelName());
            }
        }

        return errors;
    }

    private boolean isKnownDeviceType(String type) {
        // Phase 2 支持的设备类型
        return "coyote_v3".equals(type) || "coyote_v2".equals(type);
    }
}
```

### 3.2 PlaylistManager

播放列表的 CRUD、音轨添加（含自动匹配）、拖拽排序。

```java
package com.hypno.hypnovibe.app.manager;

import android.media.MediaMetadataRetriever;
import com.hypno.hypnovibe.domain.entity.Playlist;
import com.hypno.hypnovibe.domain.repository.IPlaylistRepository;
import com.hypno.hypnovibe.infrastructure.io.FilePathResolver;
import java.util.*;

public class PlaylistManager {
    private final IPlaylistRepository repo;

    public PlaylistManager(IPlaylistRepository repo) {
        this.repo = repo;
    }

    /**
     * 列出所有播放列表
     */
    public List<Playlist> listPlaylists() {
        return repo.listAll();
    }

    /**
     * 创建新播放列表（自动生成 UUID + 时间戳）
     */
    public Playlist createPlaylist(String name, String configId, String playMode) {
        Playlist playlist = new Playlist();
        playlist.setId(UUID.randomUUID().toString());
        playlist.setName(name);
        playlist.setConfigId(configId);
        playlist.setPlayMode(playMode != null ? playMode : "LOOP_LIST");
        playlist.setTracks(new ArrayList<>());
        playlist.setChannelMapping(new HashMap<>());
        long now = System.currentTimeMillis();
        playlist.setCreatedAt(now);
        playlist.setUpdatedAt(now);
        repo.save(playlist);
        return playlist;
    }

    /**
     * 添加音频到列表，自动执行匹配流程
     *
     * 执行步骤：
     * 1. MediaMetadataRetriever 读取音频时长和标题
     * 2. FilePathResolver 查找同文件夹下的 .hvscript
     * 3. 若有脚本，验证 configId 是否匹配
     * 4. 若有脚本，验证时长是否一致（不一致标记 ⚠️）
     */
    public Playlist addTrack(String playlistId, String audioPath) {
        Playlist playlist = repo.findById(playlistId)
            .orElseThrow(() -> new IllegalArgumentException("播放列表不存在: " + playlistId));

        Playlist.Track track = new Playlist.Track();
        track.setTrackId(UUID.randomUUID().toString());
        track.setAudioFilePath(audioPath);

        // 步骤1：读取音频元数据
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(audioPath);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            track.setAudioTitle(title != null ? title : new java.io.File(audioPath).getName());
            track.setDurationMs(durationStr != null ? Long.parseLong(durationStr) : 0);
        } catch (Exception e) {
            track.setAudioTitle(new java.io.File(audioPath).getName());
            track.setDurationMs(0);
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }

        // 步骤2：自动查找时间轴脚本
        Optional<String> scriptPathOpt = FilePathResolver.findTimelineForAudio(audioPath);
        if (scriptPathOpt.isPresent()) {
            track.setTimelineScriptPath(scriptPathOpt.get());
            track.setAutoMatched(true);
            // 步骤3 & 4 在 validateConfigIdMatch 和 onTrackAdded 中处理
        } else {
            track.setTimelineScriptPath(null);
            track.setAutoMatched(false);
        }

        playlist.getTracks().add(track);
        playlist.setUpdatedAt(System.currentTimeMillis());
        repo.save(playlist);
        return playlist;
    }

    /**
     * 拖拽重排序
     */
    public Playlist reorderTracks(String playlistId, int fromIndex, int toIndex) {
        Playlist playlist = repo.findById(playlistId)
            .orElseThrow(() -> new IllegalArgumentException("播放列表不存在: " + playlistId));

        List<Playlist.Track> tracks = playlist.getTracks();
        if (fromIndex < 0 || fromIndex >= tracks.size() || toIndex < 0 || toIndex >= tracks.size()) {
            return playlist;
        }

        Playlist.Track track = tracks.remove(fromIndex);
        tracks.add(toIndex, track);
        playlist.setUpdatedAt(System.currentTimeMillis());
        repo.save(playlist);
        return playlist;
    }

    /**
     * 为曲目手动设置时间轴脚本
     */
    public Playlist setTimelineScript(String playlistId, String trackId, String scriptPath) {
        Playlist playlist = repo.findById(playlistId)
            .orElseThrow(() -> new IllegalArgumentException("播放列表不存在: " + playlistId));

        for (Playlist.Track t : playlist.getTracks()) {
            if (t.getTrackId().equals(trackId)) {
                t.setTimelineScriptPath(scriptPath);
                t.setAutoMatched(false);
                break;
            }
        }
        playlist.setUpdatedAt(System.currentTimeMillis());
        repo.save(playlist);
        return playlist;
    }

    /**
     * 删除播放列表
     */
    public void deletePlaylist(String id) {
        repo.delete(id);
    }

    /**
     * 根据 ID 查找
     */
    public Optional<Playlist> findById(String id) {
        return repo.findById(id);
    }

    /**
     * 验证新增时间轴脚本的 configId 与列表是否一致
     */
    public boolean validateConfigIdMatch(String playlistConfigId, String scriptConfigId) {
        return playlistConfigId != null && playlistConfigId.equals(scriptConfigId);
    }
}
```

---

## 4. ViewModel 设计

### 4.1 ConfigVM（Java ViewModel）

```java
package com.hypno.hypnovibe.app.viewmodel;

import androidx.lifecycle.ViewModel;
import com.hypno.hypnovibe.app.manager.ConfigManager;
import com.hypno.hypnovibe.domain.entity.DeviceConfig;
import java.util.ArrayList;
import java.util.List;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;

public class ConfigVM extends ViewModel {
    private final ConfigManager manager;

    // State
    private final MutableStateFlow<List<DeviceConfig>> configs = new MutableStateFlow<>(new ArrayList<>());
    private final MutableStateFlow<ConfigUiState> uiState = new MutableStateFlow<>(new ConfigUiState(false, null));

    public ConfigVM(ConfigManager manager) {
        this.manager = manager;
    }

    public StateFlow<List<DeviceConfig>> getConfigs() {
        return configs;
    }

    public StateFlow<ConfigUiState> getUiState() {
        return uiState;
    }

    public void loadConfigs() {
        try {
            uiState.setValue(new ConfigUiState(true, null));
            List<DeviceConfig> list = manager.listConfigs();
            configs.setValue(list);
            uiState.setValue(new ConfigUiState(false, null));
        } catch (Exception e) {
            uiState.setValue(new ConfigUiState(false, e.getMessage()));
        }
    }

    public void createConfig(String name, List<DeviceConfig.ChannelDef> channels) {
        try {
            DeviceConfig config = manager.createConfig(name, channels);
            loadConfigs();
        } catch (Exception e) {
            uiState.setValue(new ConfigUiState(false, e.getMessage()));
        }
    }

    public void updateConfig(DeviceConfig config) {
        try {
            List<String> errors = manager.validate(config);
            if (!errors.isEmpty()) {
                uiState.setValue(new ConfigUiState(false, String.join("\n", errors)));
                return;
            }
            manager.updateConfig(config);
            loadConfigs();
        } catch (Exception e) {
            uiState.setValue(new ConfigUiState(false, e.getMessage()));
        }
    }

    public void deleteConfig(String id) {
        try {
            manager.deleteConfig(id);
            loadConfigs();
        } catch (Exception e) {
            uiState.setValue(new ConfigUiState(false, e.getMessage()));
        }
    }

    public static class ConfigUiState {
        private final boolean loading;
        private final String error;   // null = 无错误

        public ConfigUiState(boolean loading, String error) {
            this.loading = loading;
            this.error = error;
        }

        public boolean isLoading() { return loading; }
        public String getError() { return error; }
    }
}
```

### 4.2 PlaySessionVM（Java ViewModel）

```java
package com.hypno.hypnovibe.app.viewmodel;

import androidx.lifecycle.ViewModel;
import com.hypno.hypnovibe.app.manager.PlaylistManager;
import com.hypno.hypnovibe.domain.entity.Playlist;
import java.util.ArrayList;
import java.util.List;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;

public class PlaySessionVM extends ViewModel {
    private final PlaylistManager manager;

    private final MutableStateFlow<List<Playlist>> playlists = new MutableStateFlow<>(new ArrayList<>());
    private final MutableStateFlow<Playlist> currentPlaylist = new MutableStateFlow<>(null);
    private final MutableStateFlow<List<Playlist.Track>> currentTracks = new MutableStateFlow<>(new ArrayList<>());
    private final MutableStateFlow<String> playMode = new MutableStateFlow<>("LOOP_LIST");
    private final MutableStateFlow<Boolean> loading = new MutableStateFlow<>(false);

    public PlaySessionVM(PlaylistManager manager) {
        this.manager = manager;
    }

    public StateFlow<List<Playlist>> getPlaylists() { return playlists; }
    public StateFlow<Playlist> getCurrentPlaylist() { return currentPlaylist; }
    public StateFlow<List<Playlist.Track>> getCurrentTracks() { return currentTracks; }
    public StateFlow<String> getPlayMode() { return playMode; }
    public StateFlow<Boolean> getLoading() { return loading; }

    public void loadPlaylists() {
        loading.setValue(true);
        try {
            List<Playlist> list = manager.listPlaylists();
            playlists.setValue(list);
        } finally {
            loading.setValue(false);
        }
    }

    public void openPlaylist(String id) {
        manager.findById(id).ifPresent(pl -> {
            currentPlaylist.setValue(pl);
            currentTracks.setValue(pl.getTracks() != null ? pl.getTracks() : new ArrayList<>());
            playMode.setValue(pl.getPlayMode());
        });
    }

    public void createPlaylist(String name, String configId) {
        Playlist pl = manager.createPlaylist(name, configId, "LOOP_LIST");
        loadPlaylists();
    }

    public void addTrack(String audioPath) {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null) return;
        Playlist updated = manager.addTrack(pl.getId(), audioPath);
        openPlaylist(updated.getId());
    }

    public void reorderTracks(int from, int to) {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null) return;
        Playlist updated = manager.reorderTracks(pl.getId(), from, to);
        openPlaylist(updated.getId());
    }

    public void setTimelineScript(String trackId, String scriptPath) {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null) return;
        Playlist updated = manager.setTimelineScript(pl.getId(), trackId, scriptPath);
        openPlaylist(updated.getId());
    }

    public void setPlayMode(String mode) {
        Playlist pl = currentPlaylist.getValue();
        if (pl == null) return;
        pl.setPlayMode(mode);
        pl.setUpdatedAt(System.currentTimeMillis());
        playMode.setValue(mode);
    }
}
```

### 4.3 Kotlin 调用 Java ViewModel

```kotlin
// ConfigListScreen.kt
@Composable
fun ConfigListScreen(navController: NavController) {
    val vm: ConfigVM = viewModel()  // 自动从 ViewModelProvider 获取
    val configs by vm.configs.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    // ...
}
```

---

## 5. UI 集成设计

### 5.1 ConfigListScreen（替换 Phase 1 空壳）

```
┌──────────────────────────────────┐
│ 设备配置列表           [+ 新建]  │
├──────────────────────────────────┤
│ ┌──────────────────────────────┐ │
│ │ 双郊狼配置                   │ │  ← StoneCard
│ │ 4 通道 (coyote_v3)          │ │     点击进入编辑
│ │ 创建于 06-28                 │ │     长按弹出删除
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │ 基础测试配置                 │ │
│ │ 2 通道 (coyote_v3)          │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

**功能要点：**
- 从 `ConfigVM.getConfigs()` 读取真实数据
- 列表项使用 `StoneCard` 显示配置名称、通道数、设备类型、创建日期
- 点击列表项 → 导航到 `ConfigEditorScreen(configId)`
- 长按列表项 → 弹出删除确认对话框
- FAB 按钮 → 导航到 `ConfigEditorScreen("new")`（新建模式）
- 空列表显示"暂无配置，点击 + 创建"居中文字

### 5.2 ConfigEditorScreen（替换 Phase 1 灰显表单）

```
┌──────────────────────────────────┐
│ 新建配置                  [保存] │
├──────────────────────────────────┤
│ 配置名称: [______________]       │
│                                  │
│ 通道列表:                [+ 添加] │
│ ┌──────────────────────────────┐ │
│ │ 名称: [后穴_____]     [✕]   │ │  ← 行内可编辑
│ │ 类型: [coyote_v3 ▼]         │ │
│ │ 强度: [  0 ] ~ [ 200 ]      │ │
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │ 名称: [前部_____]     [✕]   │ │
│ │ 类型: [coyote_v3 ▼]         │ │
│ │ 强度: [  0 ] ~ [ 200 ]      │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

**功能要点：**
- 新建模式（configId = "new"）：空表单，自动添加一个默认通道
- 编辑模式（configId = 已有 ID）：从 `ConfigVM` 加载现有数据填充表单
- 配置名称：`OutlinedTextField` 绑定
- 通道列表：每个通道为独立卡片，行内编辑：
  - 通道名称输入框
  - 设备类型下拉选择（coyote_v3 / coyote_v2）
  - 强度范围：两个 `DungeonSlider` 或数字输入框（min ~ max）
  - 删除按钮（✕）移除通道
- [+ 添加] 按钮追加新通道（使用临时 UUID）
- [保存] 按钮：调用 `ConfigVM.createConfig()` 或 `updateConfig()`
- 保存前执行 `ConfigManager.validate()`，有错误时 SnackBar 显示
- 保存成功后返回 ConfigListScreen

### 5.3 PlaylistScreen（接入 PlaySessionVM）

Phase 1 的空列表改为从 `PlaySessionVM` 读取数据。

```
┌──────────────────────────────────┐
│ 播放列表               [＋ 新建] │
├──────────────────────────────────┤
│ 当前: 周五催眠合集               │  ← 顶部显示播放列表名 + 配置信息
│ 配置: 双郊狼配置 (4通道)         │
├──────────────────────────────────┤
│ ┌──────────────────────────────┐ │
│ │ 🎵 催眠引导                  │ │  ← StoneCard 列表项
│ │    10:00  ⚠️ 时长不匹配      │ │     显示标题/时长/时间轴状态
│ │    📜 已关联脚本              │ │     ⚠️ = 时长不匹配警告
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │ 🎵 深度恍惚                  │ │
│ │    15:00                     │ │
│ │    📄 无时间轴               │ │
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │ 🎵 环境背景音               │ │
│ │    30:00                     │ │
│ │    📄 无时间轴               │ │
│ └──────────────────────────────┘ │
│                                  │
│ GothicDivider                    │
│ 播放模式: LOOP_LIST  [▼]        │  ← 底部播放模式选择
├──────────────────────────────────┤
│ PlayerBar（底部常驻）            │  ← Phase 3 可交互
│ ⏮   ▶   ⏭   ═══进度═══        │
└──────────────────────────────────┘
```

**功能要点：**
- 顶部显示当前播放列表名称和关联的配置信息
- 列表项显示曲目标题/时长/时间轴状态：
  - 有关联脚本 → 📜 图标 + "已关联脚本"
  - 无时间轴 → 📄 图标 + "无时间轴"
  - 时长不匹配 → ⚠️ 图标 + "时长不匹配"警告
- [+ 新建] 按钮创建新播放列表，需先选择关联配置
- [+ 添加音频] 通过 `ACTION_OPEN_DOCUMENT` 打开系统文件选择器，过滤 `audio/*`
  - 选择音频后自动执行 auto-match 流程
  - 匹配到的 .hvscript 自动关联，验证 configId 和时长
- 支持拖拽重排序：`LazyColumn` + `detectDragGesturesAfterLongPress`
- 底部显示播放模式下拉选择（LOOP_LIST / STOP_AT_END / LOOP_LAST）

### 5.4 ChannelMappingScreen（Phase 2 仅完善 UI 骨架）

配置详请界面（Phase 6 正式实现通道映射逻辑），Phase 2 仅显示当前播放列表的配置通道列表和设备连接状态提示。

### 5.5 HomeScreen（移除 Toast，接入 VM）

Phase 1 中三个快捷入口按钮的 `Toast("即将开放")` 替换为：
- "打开播放列表" → 导航到 PlaylistScreen
- "设备配置" → 导航到 ConfigListScreen
- "快速测试" → 导航到 CoyoteTestScreen

---

## 6. 自动匹配文件流程

```
用户选择 /music/hypno_guide.mp3
   │
   ▼
PlaylistManager.addTrack()
   │
   ├── 1. MediaMetadataRetriever.setDataSource(audioPath)
   │      ├── 读取 METADATA_KEY_DURATION → track.durationMs
   │      └── 读取 METADATA_KEY_TITLE → track.audioTitle
   │
   ├── 2. FilePathResolver.findTimelineForAudio(audioPath)
   │      ├── 查找 /music/hypno_guide.hvscript → 找到
   │      │    └── track.timelineScriptPath = 路径
   │      │    └── track.autoMatched = true
   │      │
   │      └── 未找到
   │           └── track.timelineScriptPath = null
   │           └── track.autoMatched = false
   │
   ├── 3. 验证 configId 匹配
   │      └── PlaylistManager.validateConfigIdMatch(
   │              playlist.configId, script.configId)
   │           ├── 匹配 → 继续
   │           └── 不匹配 → 拒绝关联，提示用户 configId 不一致
   │
   └── 4. 验证时长匹配
          └── 比较音频 durationMs vs 脚本 totalDurationMs
               ├── 差异 < 1秒 → 正常
               └── 差异 ≥ 1秒 → 标记 ⚠️ 时长不匹配
```

**匹配规则优先级：**
1. 同文件名、不同后缀：`hypno_guide.mp3` → `hypno_guide.hvscript`
2. 同文件夹下最新修改的 `.hvscript`（兜底匹配）
3. 未匹配到则不关联时间轴（纯音频播放）

---

## 7. 验证标准

| # | 检查项 | 方法 |
|:--:|------|------|
| 1 | 创建设备配置"双郊狼(4通道)" → 保存 → 列表可见 | 手动 |
| 2 | 重启 APP → 配置仍在 | 手动 |
| 3 | 编辑配置：修改通道名 → 保存 → 生效 | 手动 |
| 4 | 删除配置 → 列表消失 | 手动 |
| 5 | 创建播放列表 → 添加 3 首 MP3 → 拖拽排序 → 保存 | 手动 |
| 6 | 同文件夹下 .hvscript 被自动匹配 | 手动 |
| 7 | 无 .hvscript 时曲目标注"无时间轴" | 手动 |
| 8 | 配置 ID 不匹配的脚本被拒绝关联 | 手动 |
| 9 | 时长不匹配时显示 ⚠️ | 手动 |
| 10 | 播放模式可切换（LOOP_LIST/STOP_AT_END/LOOP_LAST） | 手动 |
| 11 | 播放列表可删除 | 手动 |
| 12 | 重启 APP → 播放列表仍在 | 手动 |

---

## 8. 文件清单

### 8.1 Java 新增（18 个文件）

```
app/src/main/java/com/hypno/hypnovibe/
├── domain/
│   ├── entity/
│   │   ├── DeviceConfig.java           # 设备配置实体（完整实现）
│   │   ├── Playlist.java               # 播放列表实体（完整实现）
│   │   ├── TimelineScript.java         # 时间轴脚本（空壳）
│   │   ├── WaveformFile.java           # 波形文件（空壳）
│   │   ├── WaveformSegment.java        # 波形段（空壳）
│   │   ├── PairedDevice.java           # 已配对设备（空壳）
│   │   ├── StrengthPreset.java         # 强度预设（空壳）
│   │   └── ChannelMapping.java         # 通道映射（空壳）
│   ├── value/
│   │   ├── ConfigId.java               # 配置ID值对象
│   │   ├── ChannelId.java              # 通道ID值对象
│   │   ├── Strength.java               # 强度值对象（含 clamp）
│   │   └── TimeRange.java              # 时间范围值对象
│   └── repository/
│       ├── IConfigRepository.java      # 配置仓储接口（完整实现）
│       ├── IPlaylistRepository.java    # 播放列表仓储接口（完整实现）
│       ├── ITimelineRepository.java    # 时间轴仓储接口（空壳）
│       ├── IWaveformRepository.java    # 波形仓储接口（空壳）
│       ├── IDeviceRepository.java      # 设备仓储接口（空壳）
│       └── IPresetRepository.java      # 预设仓储接口（空壳）
├── infrastructure/
│   ├── io/
│   │   ├── JsonFileRepository.java     # 通用 JSON 文件 CRUD
│   │   └── FilePathResolver.java       # 文件路径查找器
│   └── persistence/
│       └── DevicePrefs.java            # 设备偏好（空壳）
└── app/
    ├── viewmodel/
    │   ├── ConfigVM.java               # 配置 ViewModel
    │   └── PlaySessionVM.java          # 播放会话 ViewModel
    └── manager/
        ├── ConfigManager.java          # 配置管理器
        └── PlaylistManager.java        # 播放列表管理器
```

### 8.2 Kotlin 修改（4 个文件）

```
app/src/main/kotlin/com/hypno/hypnovibe/ui/screen/
├── config/
│   ├── ConfigListScreen.kt             # 重写为功能版（接入 ConfigVM）
│   └── ConfigEditorScreen.kt           # 重写为可编辑表单
├── playlist/
│   └── PlaylistScreen.kt               # 重写接入 PlaySessionVM
└── home/
    └── HomeScreen.kt                   # 删除 "即将开放" Toast，接入真实导航
```

### 8.3 文件扩展名约定

| 扩展名 | 用途 |
|--------|------|
| `.hvconfig` | 设备配置 JSON 文件 |
| `.hvscript` | 时间轴脚本 JSON 文件 |
| `.hvplaylist` | 播放列表 JSON 文件 |
| `.hvwav` | 波形数据文件 |

### 8.4 依赖项（build.gradle.kts 新增）

```kotlin
dependencies {
    // Gson（JSON 序列化）
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines（StateFlow in ViewModel）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Lifecycle ViewModel（Java 可用）
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
}
```

---

## 附录：ConfigRepository 和 PlaylistRepository 的实现方式

利用 `JsonFileRepository` 实现具体仓储类，在 `di/AppModule.java` 中装配：

```java
// 配置仓储实现
public class ConfigRepository implements IConfigRepository {
    private final JsonFileRepository repo;

    public ConfigRepository(Context context) {
        this.repo = new JsonFileRepository(context, "configs");
    }

    @Override
    public List<DeviceConfig> listAll() {
        return repo.listAll(".*\\.hvconfig", DeviceConfig.class);
    }

    @Override
    public Optional<DeviceConfig> findById(String id) {
        return repo.findById(id, DeviceConfig.class);
    }

    @Override
    public void save(DeviceConfig config) {
        repo.save(config.getId(), config);
    }

    @Override
    public void delete(String id) {
        repo.delete(id);
    }
}

// 播放列表仓储实现（同理）
public class PlaylistRepository implements IPlaylistRepository {
    private final JsonFileRepository repo;

    public PlaylistRepository(Context context) {
        this.repo = new JsonFileRepository(context, "playlists");
    }

    // ... 同 ConfigRepository 的实现模式
}
```
