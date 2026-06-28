package com.hypno.hypnovibe.infrastructure.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypno.hypnovibe.domain.entity.PairedDevice;
import java.lang.reflect.Type;
import java.util.*;

public class DevicePrefs {
    private static final String PREFS_NAME = "hypno_device_prefs";
    private static final String KEY_DEVICES = "paired_devices";
    private final SharedPreferences prefs;
    private final Gson gson;

    public DevicePrefs(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void saveDevice(PairedDevice device) {
        List<PairedDevice> devices = loadAll();
        boolean found = false;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getMacAddress().equals(device.getMacAddress())) {
                devices.set(i, device);
                found = true;
                break;
            }
        }
        if (!found) {
            devices.add(device);
        }
        saveList(devices);
    }

    public List<PairedDevice> loadAll() {
        String json = prefs.getString(KEY_DEVICES, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type listType = new TypeToken<List<PairedDevice>>() {}.getType();
        List<PairedDevice> list = gson.fromJson(json, listType);
        return list != null ? list : new ArrayList<>();
    }

    public void removeDevice(String mac) {
        List<PairedDevice> devices = loadAll();
        devices.removeIf(d -> d.getMacAddress().equals(mac));
        saveList(devices);
    }

    private void saveList(List<PairedDevice> devices) {
        String json = gson.toJson(devices);
        prefs.edit().putString(KEY_DEVICES, json).apply();
    }
}
