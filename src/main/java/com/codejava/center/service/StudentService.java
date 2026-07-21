package com.codejava.center.service;

import com.codejava.center.domain.Student;
import com.codejava.center.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor // يقوم Lombok بإنشاء Constructor لحقن StudentRepository تلقائياً
public class StudentService {

    private final StudentRepository studentRepository;

    /**
     * حفظ طالب جديد أو تحديث بيانات طالب حالي
     */
    @Transactional
    public Student saveStudent(Student student) {
        // 1. التحقق من البيانات الأساسية
        if (student.getName() == null || student.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("اسم الطالب لا يمكن أن يكون فارغاً.");
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
                    throw new IllegalStateException("هذا الباركود مسجل بالفعل لطالب آخر.");
                }
            }
        }

        if (student.getId() == null && studentRepository.existsByName(student.getName())) {
            throw new IllegalStateException("هذا الاسم مسجل بالفعل لطالب آخر.");
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
                .orElseThrow(() -> new IllegalArgumentException("لم يتم العثور على طالب بهذا الباركود: " + barcode));
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
}