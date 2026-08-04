package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;
import com.codejava.center.domain.Student;
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

        // 3. الحفظ في قاعدة البيانات
        return studentRepository.save(student);
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
     * جلب جميع الطلاب
     */
    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    /**
     * دالة مساعدة لتوليد باركود فريد (مثال: STU-12345678)
     */
    private String generateUniqueBarcode() {
        // يمكن استخدام أرقام عشوائية أو UUID، هنا نستخدم جزء من UUID ليكون قصيراً
        String uniqueShortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "STU-" + uniqueShortId;
    }

    @Transactional
    public void deleteStudent(Long studentId) {
        studentRepository.deleteById(studentId);
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