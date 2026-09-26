package com.codejava.center.service;
import com.codejava.center.core.catalog.SubjectNames;
import com.codejava.center.domain.Subject;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.SubjectRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
@Service @RequiredArgsConstructor
public class SubjectService {
    private final SubjectRepository subjects;
    private final TeacherRepository teachers;
    private final AuditService audit;
    @Transactional(readOnly = true) @RequiresRole(Role.ADMIN)
    public List<Subject> getAllSubjects() { return subjects.findAllByOrderByNameAsc(); }
    @Transactional @RequiresRole(Role.ADMIN)
    public Subject save(Long id, String input) {
        String name = input == null ? "" : input.trim().replaceAll("[\\s\\p{Z}]+", " ");
        if (name.isBlank() || name.length() > 50 || SubjectNames.normalized(name).isBlank())
            throw new IllegalArgumentException(I18n.get("subject.invalidName"));
        String key = SubjectNames.key(name);
        subjects.findByNameKey(key).filter(existing -> !Objects.equals(id, existing.getId()))
                .ifPresent(existing -> { throw new IllegalStateException(I18n.get("subject.duplicate")); });
        Subject subject = id == null ? new Subject() : subjects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("subject.notFound")));
        subject.setName(name); subject.setNameKey(key);
        try { subjects.saveAndFlush(subject); }
        catch (DataIntegrityViolationException e) { throw new IllegalStateException(I18n.get("subject.duplicate"), e); }
        audit.record(id == null ? AuditAction.SUBJECT_CREATED : AuditAction.SUBJECT_UPDATED, subject.getId(), name);
        return subject;
    }
    @Transactional @RequiresRole(Role.ADMIN)
    public void delete(Long id) {
        Subject subject = subjects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("subject.notFound")));
        if (teachers.existsBySubjectDefinitionId(id)) throw new IllegalStateException(I18n.get("subject.inUse"));
        try { subjects.delete(subject); subjects.flush(); }
        catch (DataIntegrityViolationException e) { throw new IllegalStateException(I18n.get("subject.inUse"), e); }
        audit.record(AuditAction.SUBJECT_DELETED, id, subject.getName());
    }
}
