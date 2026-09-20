package com.codejava.center.service;

import com.codejava.center.AspectProxying;
import com.codejava.center.TestActor;
import com.codejava.center.config.SecurityConfig;
import com.codejava.center.config.TimeConfig;
import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.CourseGroup;
import com.codejava.center.domain.Teacher;
import com.codejava.center.domain.User;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.CourseGroupRepository;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.security.AccessDeniedException;
import com.codejava.center.security.RoleEnforcementAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * الافتراضُ منعٌ: الكتابة لا تقع بلا من يُنسب إليها.
 *
 * <p>كانت ستّ خدمات بلا حارس واحد - نحو إحدى وأربعين دالة عامة - ومنها ما يخصم مالاً
 * من رصيد طالب وما يعيد تعريف كل الأرصدة. وغيابُ الحارس لا يظهر في شيء: الشاشة تخفي
 * الزرّ، فيبدو الحدّ قائماً وهو في الواجهة وحدها.</p>
 *
 * <p>ولذلك يفحص هذا الصفّ <b>ما لا يقع</b>: أنّ الاستدعاء يُرفض بلا جلسة، وأنّ حدود
 * الأدوار ليست واحدة - السكرتارية تفتح الحصص وتسجّل الحضور، ولا تمسّ إعدادات السنتر
 * التي يقرّر فيها {@code ledgerStartDate} أيّ الحركات تُحتسب أصلاً.</p>
 */
@DataJpaTest
@Import({SessionService.class, SettingsService.class, AuditService.class,
        SecurityConfig.class, TimeConfig.class, TestActor.class,
        RoleEnforcementAspect.class, AspectProxying.class})
class DefaultDenyTest {

    @Autowired private SessionService sessionService;
    @Autowired private SettingsService settingsService;
    @Autowired private TestActor actor;
    @Autowired private CourseGroupRepository groupRepository;
    @Autowired private TeacherRepository teacherRepository;

    @AfterEach
    void signOut() {
        actor.cleanUserSession();
    }

    /** خيطٌ بلا جلسة: المجدوِل والنظام يكتبان عبر دوالّ أخرى غير محروسة عن قصد */
    @Test
    void openingASessionWithNobodySignedInIsRefused() {
        assertThatThrownBy(() -> sessionService.openSession(group(), LocalDate.now()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void savingCentreSettingsWithNobodySignedInIsRefused() {
        assertThatThrownBy(() -> settingsService.save(CenterSettings.builder().build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** بوابة الحضور وفتح الحصص عملُ من يجلس عند الاستقبال، لا عمل المدير وحده */
    @Test
    void aSecretaryMayOpenASession() {
        actor.setCurrentUser(userWithRole(Role.SECRETARY));

        assertThatCode(() -> sessionService.openSession(group(), LocalDate.now()))
                .doesNotThrowAnyException();
    }

    /**
     * وإعدادات السنتر لا.
     *
     * <p>{@code ledgerStartDate} فيها يقرّر من أيّ تاريخ تُحتسب الحركات: تبديلُه يغيّر
     * رصيد كل طالب في السنتر بلا أن يمسّ صفّاً واحداً في جدول الحركات.</p>
     */
    @Test
    void aSecretaryMayNotSaveCentreSettings() {
        actor.setCurrentUser(userWithRole(Role.SECRETARY));

        assertThatThrownBy(() -> settingsService.save(CenterSettings.builder().build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * ما يكتبه النظام بلا جلسة يبقى بلا حارس عن قصد.
     *
     * <p>{@code recordAlertScanAt} يكتبها مجدوِل التنبيهات من خيطٍ لا مستخدم عليه -
     * كما {@code BackupService.executeBackup} تماماً. حارسٌ هنا يعني أن كل فحص مجدول
     * يُرفض، وأن التاريخ المعروض في الشاشة يبقى قديماً بلا سبب ظاهر.</p>
     */
    @Test
    void whatTheSchedulerWritesStaysUnguardedOnPurpose() {
        assertThatCode(() -> settingsService.recordAlertScanAt(java.time.LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    private CourseGroup group() {
        Teacher teacher = teacherRepository.saveAndFlush(Teacher.builder()
                .name("معلّم")
                .subject("رياضيات")
                .commissionType("PERCENTAGE")
                .commissionValue(new BigDecimal("50.00"))
                .build());
        return groupRepository.saveAndFlush(CourseGroup.builder()
                .name("مجموعة")
                .teacher(teacher)
                .sessionPrice(new BigDecimal("50.00"))
                .build());
    }

    private User userWithRole(Role role) {
        return User.builder().id(1L).username("tester").password("x").role(role).build();
    }
}
