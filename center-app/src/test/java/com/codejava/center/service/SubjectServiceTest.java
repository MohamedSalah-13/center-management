package com.codejava.center.service;
import com.codejava.center.TestActor;
import com.codejava.center.config.SecurityConfig;
import com.codejava.center.config.TimeConfig;
import com.codejava.center.domain.Teacher;
import com.codejava.center.repository.*;
import com.codejava.center.service.dto.TeacherDraft;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest
@Import({SubjectService.class, TeacherService.class, TransactionService.class, SettingsService.class,
        AuditService.class, TestActor.class, SecurityConfig.class, TimeConfig.class})
class SubjectServiceTest {
    @Autowired SubjectService subjects;
    @Autowired TeacherService teachers;
    @Autowired TeacherRepository teacherRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired jakarta.persistence.EntityManager em;
    @Test void rejectsEquivalentNamesAndKeepsOnlyOneSubject() {
        subjects.save(null, "اللغة الإنجليزية");
        for (String alias : new String[]{"E", "لغة انجليزية", "انجليزى"})
            assertThatThrownBy(() -> subjects.save(null, alias)).hasMessage(I18n.get("subject.duplicate"));
        assertThat(subjectRepository.count()).isEqualTo(1);
    }
    @Test void renameKeepsTeacherLinkedAndUsedSubjectCannotBeDeleted() {
        var subject = subjects.save(null, "روبوتات");
        var teacher = teachers.saveTeacher(new TeacherDraft(null,"معلم",subject.getId(),"PERCENTAGE",BigDecimal.TEN));
        assertThatThrownBy(() -> subjects.delete(subject.getId())).hasMessage(I18n.get("subject.inUse"));
        subjects.save(subject.getId(), "الروبوتات"); em.flush(); em.clear();
        Teacher read = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertThat(read.getSubject()).isEqualTo("الروبوتات");
        assertThat(read.getSubjectDefinition().getId()).isEqualTo(subject.getId());
    }
    @Test void teacherRequiresAnExistingSubject() {
        assertThatThrownBy(() -> teachers.saveTeacher(new TeacherDraft(null,"معلم",null,"PERCENTAGE",BigDecimal.TEN)))
                .hasMessage(I18n.get("subject.required"));
        assertThatThrownBy(() -> teachers.saveTeacher(new TeacherDraft(null,"معلم",999999L,"PERCENTAGE",BigDecimal.TEN)))
                .hasMessage(I18n.get("subject.notFound"));
        assertThat(teacherRepository.count()).isZero();
    }
    @Test void canDeleteUnusedSubjectAndRejectBlankNames() {
        var subject=subjects.save(null,"روبوتات"); subjects.delete(subject.getId());
        assertThat(subjectRepository.count()).isZero();
        assertThatThrownBy(() -> subjects.save(null,"  ")).hasMessage(I18n.get("subject.invalidName"));
    }
}
