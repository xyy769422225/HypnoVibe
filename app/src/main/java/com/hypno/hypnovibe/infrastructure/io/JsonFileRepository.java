package com.hypno.hypnovibe.infrastructure.io;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JsonFileRepository {
    private final File baseDir;
    private final Gson gson;
    private final String subDir;

    public JsonFileRepository(Context context, String subDir) {
        this.subDir = subDir;
        this.baseDir = new File(context.getFilesDir(), subDir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public <T> List<T> listAll(String glob, Class<T> clazz) {
        List<T> results = new ArrayList<>();
        File[] files = baseDir.listFiles((dir, name) -> {
            if (glob == null || glob.isEmpty() || glob.equals("*")) {
                return name.endsWith(".json");
            }
            return name.matches(globToRegex(glob));
        });
        if (files != null) {
            for (File f : files) {
                try {
                    T obj = gson.fromJson(readFile(f), clazz);
                    if (obj != null) {
                        results.add(obj);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return results;
    }

    public <T> Optional<T> findById(String id, Class<T> clazz) {
        File file = fileForId(id);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            T obj = gson.fromJson(readFile(file), clazz);
            return Optional.ofNullable(obj);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <T> void save(String id, T object) {
        File file = fileForId(id);
        try {
            String json = gson.toJson(object);
            writeFile(file, json);
        } catch (Exception ignored) {
        }
    }

    public void delete(String id) {
        File file = fileForId(id);
        if (file.exists()) {
            file.delete();
        }
    }

    private File fileForId(String id) {
        return new File(baseDir, id + ".json");
    }

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void writeFile(File file, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append('.'); break;
                case '.': sb.append("\\."); break;
                case '\\': sb.append("\\\\"); break;
                default: sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
