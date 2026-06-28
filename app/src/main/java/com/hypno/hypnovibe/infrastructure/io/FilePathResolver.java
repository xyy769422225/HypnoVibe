package com.hypno.hypnovibe.infrastructure.io;

import java.io.File;
import java.util.Optional;

public class FilePathResolver {

    public static Optional<String> findTimelineForAudio(String audioPath) {
        if (audioPath == null || audioPath.isEmpty()) {
            return Optional.empty();
        }
        int dotIndex = audioPath.lastIndexOf('.');
        if (dotIndex <= 0) {
            return Optional.empty();
        }
        String base = audioPath.substring(0, dotIndex);
        String scriptPath = base + ".hvscript";
        File scriptFile = new File(scriptPath);
        if (scriptFile.exists()) {
            return Optional.of(scriptPath);
        }
        return Optional.empty();
    }

    public static String getTitleFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String fileName = new File(path).getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
}
