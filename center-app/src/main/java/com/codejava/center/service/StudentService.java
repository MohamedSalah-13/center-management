package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.service.dto.StudentDraft;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.util.I18n;
import com.codejava.center.util.PersistenceErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor // يقوم Lombok بإنشاء Constructor لحقن StudentRepository تلقائياً
public class StudentService {

    private final StudentRepository studentRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;

    /**
     * حفظ طالب جديد أو تحديث بيانات طالب قائم.
     *
     * <p>المدخل {@link StudentDraft} لا الكيان - انظر تعليقه - وأثرُ ذلك أن الصفَّ
     * القائم <b>يُقرأ من القاعدة ثم يُطبَّق عليه ما في المسودة</b>، فما ليس فيها لا
     * يتغير: حالةُ الأرشفة أولاً، وهي التي كانت الشاشةُ تحرسها بسطرٍ فيها.</p>
     *
     * <p>وباركودٌ فارغ في تعديلٍ يعني "اتركه"، لا "أصدِر غيره". كان يعني الثاني -
     * الشرط لم يكن يميّز الجديد من القائم - فمسحُ الحقل ثم الحفظ يُبطل بطاقةً مطبوعة
     * بيد صاحبها ولا شيء على الشاشة يقول ذلك.</p>
     *
     * <p>والتكرار يُفحص حين تتغير القيمة، لا حين يكون الطالب جديداً وحده: التعديل
     * صار يستطيع أن يأخذ اسم طالبٍ آخر أو باركوده، وقيدُ القاعدة وحده يردّه برسالة
     * إنجليزية فيها اسم الجدول والعمود.</p>
     */
    @Transactional
    @RequiresRole({Role.ADMIN, Role.SECRETARY})
    public Student saveStudent(StudentDraft draft) {
        String name = draft.trimmedName();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(I18n.get("error.student.nameRequired"));
        }

        // التمييز يقع قبل الحفظ: بعده يكون المعرّف قد وُلد فيبدو كل حفظ تعديلاً
        boolean isNew = draft.isNew();
        Student target = isNew
                ? Student.builder().build()
                : studentRepository.findById(draft.id())
                        .orElseThrow(() -> new IllegalStateException(I18n.get("error.student.notFound")));

        String barcode = draft.trimmedBarcode();
        if (barcode == null) {
            barcode = isNew ? generateUniqueBarcode() : target.getBarcode();
        }

        rejectTakenBarcode(draft.id(), target.getBarcode(), barcode);
        rejectTakenName(draft.id(), target.getName(), name);

        target.setBarcode(barcode);
        target.setName(name);
        target.setPhone(draft.phone());
        target.setParentPhone(draft.parentPhone());
        target.setSchoolLevel(draft.schoolLevel());

        Student saved = saveTranslatingConflicts(target);

        // المرحلة تُدوَّن لأنها العمود الوحيد الذي يُمحى بلا أثر: ترقية الطالب سنةً
        // تكتب فوق قيمته السابقة، فلا يبقى في القاعدة ما يقول في أي صف كان ومتى.
        // بالاسم لا بالترجمة، كبقية تفاصيل السجل، و"-" لمن لا مرحلة له
        auditService.record(isNew ? AuditAction.STUDENT_CREATED : AuditAction.STUDENT_UPDATED,
                saved.getId(), saved.getName(),
                "barcode=" + saved.getBarcode() + "; level="
                        + (saved.getSchoolLevel() == null ? "-" : saved.getSchoolLevel().name()));

        return saved;
    }

    /** الباركود لا يُفحص إلا إن تغيّر: فحصُ ما لم يتغير يجعل الطالب يتعارض مع نسخته المحفوظة */
    private void rejectTakenBarcode(Long id, String stored, String wanted) {
        if (wanted.equals(stored)) {
            return;
        }
        studentRepository.findByBarcode(wanted)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException(I18n.get("error.student.barcodeTaken"));
                });
    }

    private void rejectTakenName(Long id, String stored, String wanted) {
        if (wanted.equals(stored)) {
            return;
        }
        if (studentRepository.existsByName(wanted)) {
            throw new IllegalStateException(I18n.get("error.student.nameTaken"));
        }
    }

    /**
     * الحارس النهائي لسباق جهازين يسجّلان الاسم نفسه في اللحظة نفسها.
     *
     * <p>{@code saveAndFlush} حتى يقع الخطأ هنا لا عند الـ commit حيث لا تبقى نقطةٌ
     * تُترجَم فيها. والقيدان الفريدان على هذا الجدول اثنان لا ثالث لهما - الباركود
     * باسمٍ صريح، والاسم باسمٍ مولَّد لا يصلح للمقارنة - فما ليس الأول هو الثاني.</p>
     */
    private Student saveTranslatingConflicts(Student target) {
        try {
            return studentRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException error) {
            throw new IllegalStateException(I18n.get(
                    PersistenceErrors.isConstraint(error, "idx_student_barcode")
                            ? "error.student.barcodeTaken"
                            : "error.student.nameTaken"), error);
        }
    }

    /**
     * طالبٌ بمعرّفه.
     *
     * <p>لازمةٌ لمن يصل إليه برقمٍ لا بكائن - أي كلُّ حافة HTTP: شاشةُ سطح المكتب
     * تحمل الصفَّ الذي اختاره المستخدم من قائمةٍ قرأتها توّاً، والطلبُ لا يحمل إلا
     * رقماً في مساره. و{@code Student} لا علاقة كسولة فيه، فلا حاجة إلى
     * {@code JOIN FETCH} كالذي في {@code CourseGroupService.findById}.</p>
     */
    @Transactional(readOnly = true)
    public Student findById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.student.notFound")));
    }

    /**
     * البحث عن طالب باستخدام الباركود
     */
    @Transactional(readOnly = true) // للقراءة فقط، تسرع الأداء
    public Student findByBarcode(String barcode) {
        return studentRepository.findByBarcode(barcode)
                .orElseThrow(() -> new IllegalArgumentException(
                        I18n.format("error.student.barcodeNotFound", barcode)));
    }

    /**
     * طلاب شاشة التسجيل: المسجَّلون حالياً وحدهم، أو معهم المؤرشفون.
     *
     * <p>لا {@code findAll()}: راجع {@link StudentRepository#findActive()} - الجدول
     * تراكمي ولا يُنظَّف، فقراءته كاملاً في كل فتح للشاشة تكبر بعمر السنتر.</p>
     */
    @Transactional(readOnly = true)
    public List<Student> getStudents(boolean includeArchived) {
        return includeArchived ? studentRepository.findAllOrdered() : studentRepository.findActive();
    }

    /** عدد المسجَّلين حالياً، للوحة المعلومات */
    @Transactional(readOnly = true)
    public long countActiveStudents() {
        return studentRepository.countActive();
    }

    /**
     * أرشفة طالب أو إعادته إلى المسجَّلين.
     *
     * <p>هذا هو المخرج المتاح لطالب تخرّج أو انقطع: الحذف تمنعه المفاتيح الأجنبية لكل
     * من له حضور أو حركة مالية، وهو صواب - محو صفوف الحضور والحركات معه يغيّر أرقام
     * أيام مضت أُقفلت خزينتها. فالأرشفة تُخرجه من الشاشة ومن بوابة الحضور
     * ({@code AttendanceService} يفحص {@code isActive}) ولا تمسّ سطراً واحداً من تاريخه.</p>
     *
     * <p>ولا تُنهي اشتراكاته: إنهاء الاشتراك يكتب تاريخ خروج تُحسب عليه حصص الطالب،
     * وهو قرار يُتخذ في جدول الاشتراكات لا أثرٌ جانبي لزرٍّ في شاشة أخرى - نفس القاعدة
     * المتّبعة في تغيير المرحلة الدراسية.</p>
     *
     * <p>الحارس {@code {ADMIN, SECRETARY}} كحارس الحفظ: شاشةُ الطلاب شاشةُ الاستقبال،
     * لا تُخفى عن السكرتير في القائمة الجانبية، ومن يسجّل الطالب هو من يؤرشفه حين
     * ينقطع. حارسٌ أضيق يعني رفضاً في وجه من صُمّمت الشاشة له.</p>
     */
    @Transactional
    @RequiresRole({Role.ADMIN, Role.SECRETARY})
    public Student setArchived(Long studentId, boolean archived) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.get("error.student.notFound")));

        student.setActive(!archived);
        Student saved = studentRepository.save(student);

        auditService.record(archived ? AuditAction.STUDENT_ARCHIVED : AuditAction.STUDENT_RESTORED,
                saved.getId(), saved.getName(), "barcode=" + saved.getBarcode());

        return saved;
    }

    /**
     * دالة مساعدة لتوليد باركود فريد (مثال: STU-12345678)
     */
    private String generateUniqueBarcode() {
        // يمكن استخدام أرقام عشوائية أو UUID، هنا نستخدم جزء من UUID ليكون قصيراً
        String uniqueShortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "STU-" + uniqueShortId;
    }

    /**
     * حذف طالب.
     * يُقرأ الصف قبل حذفه لا لأجل الحذف بل لأجل السجل: بعد {@code deleteById} لا يبقى
     * اسم يُكتب، وسطر "حُذف الطالب رقم 412" لا يفيد من يراجع السجل بعد شهر.
     */
    @Transactional
    @RequiresRole({Role.ADMIN, Role.SECRETARY})
    public void deleteStudent(Long studentId) {
        String name = studentRepository.findById(studentId).map(Student::getName).orElse(null);

        try {
            studentRepository.deleteById(studentId);
            // المؤجَّل إلى commit لا يبقى له موضعٌ يُترجَم فيه؛ flush يُوقعه داخل هذا الحارس
            studentRepository.flush();
        } catch (DataIntegrityViolationException error) {
            // من له حضور أو حركة مالية لا يُحذف، وهو صواب: محوُ صفوفه معه يغيّر أرقام
            // أيامٍ مضت أُقفلت خزينتها. والمخرج هو الأرشفة، فالرسالة تقولها
            throw new IllegalStateException(I18n.get("student.deleteBlocked"), error);
        }
        auditService.record(AuditAction.STUDENT_DELETED, studentId, name);
    }

    /**
     * الطلاب الذين عليهم متأخرات، مرتّبين من الأكثر مديونية.
     * يستبعد ما قبل تاريخ بداية دفتر الحسابات تماماً كحساب رصيد الطالب الفردي.
     */
    @Transactional(readOnly = true)
    @RequiresRole(Role.ADMIN)
    public List<StudentBalance> getStudentsInArrears() {
        LocalDateTime ledgerStart = java.util.Optional.ofNullable(settingsService.getSettings().getLedgerStartDate())
                .map(LocalDate::atStartOfDay)
                .orElse(null);

        return studentRepository.findStudentsInArrears(ledgerStart);
    }
}