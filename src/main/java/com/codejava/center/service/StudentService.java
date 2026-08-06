package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.Student;
import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.repository.CenterSettingsRepository;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.service.dto.StudentBalance;
import com.codejava.center.repository.StudentRepository;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor // يقوم Lombok بإنشاء Constructor لحقن StudentRepository تلقائياً
public class StudentService {

    private final StudentRepository studentRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;

    /**
     * حفظ طالب جديد أو تحديث بيانات طالب حالي
     */
    @Transactional
    public Student saveStudent(Student student) {
        // 1. التحقق من البيانات الأساسية
        if (student.getName() == null || student.getName().trim().isEmpty()) {
            throw new IllegalArgumentException(I18n.get("error.student.nameRequired"));
        }

        // 2. معالجة الباركود
        if (student.getBarcode() == null || student.getBarcode().trim().isEmpty()) {
            // توليد باركود تلقائي إذا لم يقم المستخدم بإدخاله
            student.setBarcode(generateUniqueBarcode());
        } else {
            // إذا تم إدخال باركود يدوياً، يجب التأكد أنه غير مستخدم مسبقاً (في حالة الطالب الجديد)
            if (student.getId() == null) {
                Optional<Student> existingStudent = studentRepository.findByBarcode(student.getBarcode());
                if (existingStudent.isPresent()) {
                    throw new IllegalStateException(I18n.get("error.student.barcodeTaken"));
                }
            }
        }

        if (student.getId() == null && studentRepository.existsByName(student.getName())) {
            throw new IllegalStateException(I18n.get("error.student.nameTaken"));
        }

        // الإضافة والتعديل يمرّان من هنا معاً، والتمييز بينهما يجب أن يقع قبل الحفظ:
        // بعده يكون المعرّف قد وُلد فيبدو كل حفظ تعديلاً
        boolean isNew = student.getId() == null;

        // 3. الحفظ في قاعدة البيانات
        Student saved = studentRepository.save(student);

        auditService.record(isNew ? AuditAction.STUDENT_CREATED : AuditAction.STUDENT_UPDATED,
                saved.getId(), saved.getName(), "barcode=" + saved.getBarcode());

        return saved;
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
     */
    @Transactional
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
    public void deleteStudent(Long studentId) {
        String name = studentRepository.findById(studentId).map(Student::getName).orElse(null);

        studentRepository.deleteById(studentId);
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