package com.shiwutai.backend.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 作业影像落盘存储：保存上传文件并返回可访问 URL；识别时按文件名回读字节喂给视觉模型。
 */
@Service
public class FileStore {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.server-public-url:http://localhost:8080}")
    private String publicUrl;

    private Path dir;

    @PostConstruct
    void init() throws IOException {
        dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
    }

    public Map<String, String> save(MultipartFile mf) throws IOException {
        String ext = extOf(mf.getOriginalFilename());
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        try (InputStream in = mf.getInputStream()) {
            Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        }
        Map<String, String> r = new LinkedHashMap<>();
        r.put("id", filename);
        r.put("url", publicUrl + "/files/" + filename);
        return r;
    }

    public byte[] read(String filename) throws IOException {
        if (filename == null) return null;
        // 只允许同目录下的文件，防止路径穿越
        String base = Paths.get(filename).getFileName().toString();
        Path p = dir.resolve(base).normalize();
        if (!p.startsWith(dir)) return null;
        if (!Files.exists(p)) return null;
        return Files.readAllBytes(p);
    }

    public String filenameFromUrl(String url) {
        if (url == null) return null;
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }

    private static String extOf(String name) {
        if (name == null) return ".jpg";
        int i = name.lastIndexOf('.');
        if (i > 0 && i < name.length() - 1) {
            String e = name.substring(i).toLowerCase();
            if (e.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) return e;
        }
        return ".jpg";
    }
}
