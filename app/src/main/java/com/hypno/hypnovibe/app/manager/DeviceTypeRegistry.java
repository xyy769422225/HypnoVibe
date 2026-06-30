package com.hypno.hypnovibe.app.manager;

import com.hypno.hypnovibe.domain.DeviceTypeDescriptor;
import com.hypno.hypnovibe.domain.DeviceProtocolAdapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.dglab.DGLabV3Adapter;
import com.hypno.hypnovibe.infrastructure.ble.adapter.lovespouse.LoveSpouseAdapter;

import java.util.*;
import java.util.function.Function;

/**
 * 设备类型注册表。管理所有已注册的 DeviceTypeDescriptor 和 Adapter 工厂。
 */
public class DeviceTypeRegistry {

    private final Map<String, DeviceTypeDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, Function<String, DeviceProtocolAdapter>> factories = new LinkedHashMap<>();

    public DeviceTypeRegistry() {
        register(
            new DeviceTypeDescriptor(
                "dglab_v3", "DG-LAB V3",
                List.of(new DeviceTypeDescriptor.PhysicalChannelDef("A", "A 通道"),
                        new DeviceTypeDescriptor.PhysicalChannelDef("B", "B 通道")),
                0, 200,
                DeviceTypeDescriptor.ConnectionModel.CONNECTION,
                true, 0
            ),
            DGLabV3Adapter::new
        );

        register(
            new DeviceTypeDescriptor(
                "love_spouse", "Love Spouse 震动玩具",
                List.of(new DeviceTypeDescriptor.PhysicalChannelDef("vibrate", "振动")),
                0, 9,
                DeviceTypeDescriptor.ConnectionModel.BROADCAST,
                false, 0
            ),
            LoveSpouseAdapter::new
        );
    }

    public void register(DeviceTypeDescriptor descriptor,
                         Function<String, DeviceProtocolAdapter> factory) {
        descriptors.put(descriptor.getDeviceType(), descriptor);
        factories.put(descriptor.getDeviceType(), factory);
    }

    public DeviceTypeDescriptor getTypeInfo(String deviceType) {
        return descriptors.get(deviceType);
    }

    public DeviceProtocolAdapter createAdapter(String deviceType, String deviceId) {
        Function<String, DeviceProtocolAdapter> f = factories.get(deviceType);
        return f != null ? f.apply(deviceId) : null;
    }

    public Collection<DeviceTypeDescriptor> getRegisteredTypes() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    public List<DeviceTypeDescriptor> getConfigurableTypes() {
        DeviceTypeDescriptor dglab = descriptors.get("dglab_v3");
        DeviceTypeDescriptor ls = descriptors.get("love_spouse");
        return dglab != null && ls != null ? List.of(dglab, ls) : List.of();
    }
}
