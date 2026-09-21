package com.codejava.center.service;

import com.codejava.center.TestActor;
import com.codejava.center.config.SecurityConfig;
import com.codejava.center.config.TimeConfig;
import com.codejava.center.domain.Teacher;
import com.codejava.center.repository.TeacherRepository;
import com.codejava.center.service.dto.TeacherDraft;
import com.codejava.center.util.CommissionTypes;
import com.codejava.center.util.I18n;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * حفظ المعلم بمسودة، ونوعُ العمولة يُفحص عند الحفظ لا عند الصرف.
 *
 * <p>كان أيُّ نصّ يُقبل في عمود نوع العمولة، و{@code calculatePayout} وحده يرفضه -
 * أي بعد أسابيع، أمام من يعدّ المال، وعن معلمٍ حفظه شخصٌ آخر.</p>
 */
@DataJpaTest
@Import({TeacherService.class, TransactionService.class, SettingsService.class,
        AuditService.class, TestActor.class, SecurityConfig.class, TimeConfig.class})
class TeacherDraftTest {

    @Autowired private TeacherService teacherService;
    @Autowired private TeacherRepository teacherRepository;

    @Test
    void aTeacherIsSavedThenEdited() {
        Teacher saved = teacherService.saveTeacher(
                new TeacherDraft(null, "  أ/ محمد  ", "رياضيات", "PERCENTAGE", new BigDecimal("50")));

        assertThat(saved.getName()).isEqualTo("أ/ محمد");
        assertThat(saved.getCommissionValue()).isEqualByComparingTo("50.00");

        Teacher edited = teacherService.saveTeacher(
                new TeacherDraft(saved.getId(), "أ/ محمد", "علوم", "FIXED_AMOUNT", new BigDecimal("80")));

        assertThat(edited.getId()).isEqualTo(saved.getId());
        assertThat(edited.getSubject()).isEqualTo("علوم");
        assertThat(teacherRepository.count()).isEqualTo(1);
    }

    /** معرّفٌ لا وجود له يُرفض، ولا يُكتب صفٌّ جديد مكانه */
    @Test
    void anUnknownIdIsRefusedRatherThanCreatingARow() {
        assertThatThrownBy(() -> teacherService.saveTeacher(
                new TeacherDraft(4242L, "معلّم وهمي", "رياضيات", "PERCENTAGE", BigDecimal.TEN)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(I18n.get("error.teacher.notFound"));

        assertThat(teacherRepository.count()).isZero();
    }

    /**
     * نوعٌ لا يعرفه الحساب يُردّ الآن.
     *
     * <p>ولو حُفظ لَكان معلماً سليماً في كل شاشة، يسقط صرفُ كل حصةٍ له وحده.</p>
     */
    @Test
    void anUnknownCommissionTypeIsRefusedAtSaveTime() {
        assertThatThrownBy(() -> teacherService.saveTeacher(
                new TeacherDraft(null, "أ/ سامي", "لغات", "HALF_OF_WHATEVER", BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(I18n.format("error.teacher.unknownCommission", "HALF_OF_WHATEVER"));

        assertThat(teacherRepository.count()).isZero();
    }

    /**
     * وكلُّ نوعٍ معروف يُحفظ <b>ويُحسب</b>.
     *
     * <p>وهذا ما يُبقي {@code CommissionTypes.KNOWN} وفروعَ {@code calculatePayout} في
     * خطوةٍ واحدة: نوعٌ يُضاف إلى القائمة بلا فرعٍ في الحساب يسقط هنا، لا عند العميل.</p>
     */
    @Test
    void everyKnownCommissionTypeIsAcceptedAndPriced() {
        assertThat(CommissionTypes.KNOWN).isNotEmpty();

        CommissionTypes.KNOWN.forEach(type ->
                assertThatCode(() -> teacherService.saveTeacher(
                        new TeacherDraft(null, "أ/ " + type, "مادة", type, new BigDecimal("10"))))
                        .as("نوعُ عمولةٍ معروف يجب أن يُحفظ: " + type)
                        .doesNotThrowAnyException());

        assertThat(teacherRepository.findAll())
                .extracting(Teacher::getCommissionType)
                .containsExactlyInAnyOrderElementsOf(CommissionTypes.KNOWN);
    }
}
