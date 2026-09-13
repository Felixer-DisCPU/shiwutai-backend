package com.shiwutai.backend.service;

import com.shiwutai.backend.model.Teacher;
import com.shiwutai.backend.repository.TeacherRepository;
import com.shiwutai.backend.util.Req;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ProfileService {

    private final TeacherRepository teacherRepo;

    public ProfileService(TeacherRepository teacherRepo) {
        this.teacherRepo = teacherRepo;
    }

    public Map<String, Object> getProfile(String openid) {
        Optional<Teacher> t = teacherRepo.findByOpenid(openid);
        if (t.isEmpty()) return null;
        Teacher x = t.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", x.getId());
        m.put("openid", x.getOpenid());
        m.put("name", x.getName());
        m.put("school", x.getSchool());
        m.put("subject", x.getSubject());
        m.put("grade", x.getGrade());
        m.put("settingsJson", x.getSettingsJson());
        m.put("updatedAt", x.getUpdatedAt());
        return m;
    }

    public void saveProfile(String openid, Map<String, Object> profile) {
        Teacher t = teacherRepo.findByOpenid(openid).orElse(new Teacher());
        if (t.getOpenid() == null) t.setOpenid(openid);
        t.setName(Req.str(profile, "name"));
        t.setSchool(Req.str(profile, "school"));
        t.setSubject(Req.str(profile, "subject"));
        t.setGrade(Req.str(profile, "grade"));
        t.setSettingsJson(Req.str(profile, "settingsJson"));
        t.setUpdatedAt(System.currentTimeMillis());
        teacherRepo.save(t);
    }
}
