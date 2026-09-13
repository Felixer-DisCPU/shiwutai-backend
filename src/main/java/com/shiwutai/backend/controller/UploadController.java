package com.shiwutai.backend.controller;

import com.shiwutai.backend.service.FileStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class UploadController {

    private final FileStore fileStore;

    public UploadController(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            Map<String, String> saved = fileStore.save(file);
            r.put("id", saved.get("id"));
            r.put("url", saved.get("url"));
            r.put("ok", true);
        } catch (Exception e) {
            r.put("error", e.getMessage());
        }
        return r;
    }
}
