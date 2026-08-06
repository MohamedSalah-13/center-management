package com.codejava.center.service;

import com.codejava.center.config.SecurityConfig;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Session;
import com.codejava.center.domain.Teacher;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.util.I18n;
import com.codejava.center.util.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * قيود فتح الحصة وتصفيتها.
 *
 * <p>القيد الذي يستحق اختباراً هو "حصة واحدة للمجموعة في اليوم": إغلاق الحصة كان يفتح
 * الباب لفتح أخرى لنفس المجموعة في نفس اليوم، فتُخصم رسوم اليوم من الطالب مرتين -
 * خطأ لا يظهر في الشاشة، إنما في رصيد الطالب ومستحقات المعلم.</p>
 */
@DataJpaTest
@Import({SessionService.class, AuditService.class, UserSession.class, SecurityConfig.class})
class SessionServiceTest {

    @Autowired private SessionService sessionService;
    @Autowired private CourseGroupRepository courseGroupRepository;
    @Autowired private TeacherRepository teacherRepository;

    private CourseGroup group;

    @BeforeEach
    void setUp() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("أ/ محمد").subject("رياضيات")
                .commissionType("PERCENTAGE").commissionValue(new BigDecimal("50.00"))
                .build());

        group = courseGroupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة الحصص").teacher(teacher)
                .maxCapacity(20).sessionPrice(new BigDecimal("40.00"))
                .build());
    }

    @Test
    void rejectsASecondSessionForTheSameGroupOnTheSameDayEvenAfterClosingTheFirst() {
        Session first = sessionService.openSession(group, LocalDate.now());
        sessionService.closeSession(first.getId());

        assertThatThrownBy(() -> sessionService.openSession(group, LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.session.duplicateOnDate",
                        group.getName(), LocalDate.now()));
    }

    /** الحصة المفتوحة رسالتها الخاصة: المطلوب إغلاقها، لا مجرد العلم بأن اليوم محجوز */
    @Test
    void reportsTheOpenSessionRatherThanTheDuplicateWhenOneIsStillOpen() {
        sessionService.openSession(group, LocalDate.now());

        assertThatThrownBy(() -> sessionService.openSession(group, LocalDate.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.format("error.session.alreadyOpen",
                        group.getName(), LocalDate.now()));
    }

    /** يومان مختلفان حصتان مشروعتان - وهو الوضع الطبيعي لمجموعة تجتمع مرتين أسبوعياً */
    @Test
    void allowsAnotherSessionOnAnotherDay() {
        Session yesterday = sessionService.openSession(group, LocalDate.now().minusDays(1));
        sessionService.closeSession(yesterday.getId());

        assertThatCode(() -> sessionService.openSession(group, LocalDate.now()))
                .doesNotThrowAnyException();
    }

    /** التاريخ الفارغ معناه اليوم، ويخضع للقيد نفسه؛ وإلا التفّ عليه من ترك الخانة فارغة */
    @Test
    void treatsAMissingDateAsTodayForTheDuplicateCheck() {
        Session today = sessionService.openSession(group, null);
        sessionService.closeSession(today.getId());

        assertThat(today.getSessionDate()).isEqualTo(LocalDate.now());
        assertThatThrownBy(() -> sessionService.openSession(group, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void filtersByDateAndByStatus() {
        Session old = sessionService.openSession(group, LocalDate.now().minusDays(2));
        sessionService.closeSession(old.getId());
        Session open = sessionService.openSession(group, LocalDate.now());

        assertThat(sessionService.findSessions(null, null))
                .extracting(Session::getId)
                .containsExactly(open.getId(), old.getId()); // الأحدث أولاً

        assertThat(sessionService.findSessions(null, true))
                .extracting(Session::getId).containsExactly(open.getId());
        assertThat(sessionService.findSessions(null, false))
                .extracting(Session::getId).containsExactly(old.getId());
        assertThat(sessionService.findSessions(LocalDate.now(), null))
                .extracting(Session::getId).containsExactly(open.getId());
        assertThat(sessionService.findSessions(LocalDate.now(), false)).isEmpty();
    }
}
