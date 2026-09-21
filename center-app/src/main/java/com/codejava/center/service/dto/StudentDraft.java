package com.codejava.center.service.dto;

import com.codejava.center.domain.enums.SchoolLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ما يُطلب حفظه من بيانات طالب: ستة حقول، ولا شيء غيرها.
 *
 * <p>كان مدخل {@code StudentService.saveStudent} كيانَ {@code Student} نفسه، وهو على
 * منفذ HTTP بابُ mass assignment: الربط التلقائي يملأ من جسم الطلب <b>كل</b> ما يجد
 * له اسماً. ونفس قاعدة {@link UserDraft}: الحماية ليست في كونه سجلاً بدل كيان، بل في
 * أنه يحمل ما يُسمح بتغييره فقط.</p>
 *
 * <p><b>و{@code isActive} ليس فيه، وهذا أهم ما فيه.</b> الأرشفة قرارٌ له بابه
 * ({@code setArchived}) وسطرُه في سجل المراقبة ({@code STUDENT_ARCHIVED}) - وهي
 * المخرج الوحيد لطالب انقطع، إذ يمنع حذفَه ما له من حضور وحركات. فحفظٌ يستطيع قلبها
 * يعني نموذجاً يُعيد المؤرشف إلى بوابة الحضور بتعديل رقم هاتفه ولا يُكتب في السجل
 * شيء. الشاشة تحمي من ذلك اليوم بسطرٍ فيها ({@code if (isNew) setActive(true)})،
 * وقاعدةٌ تعيش في شاشة هي قاعدة تنساها الشاشة التالية.</p>
 *
 * <p>والصف الدراسي يقبل {@code null}: يُسجَّل الطالب أولاً ويُستكمل صفه بعد، وبوابةُ
 * الاشتراك هي التي ترفضه حتى يُحدَّد - وهو المقصود، لا ثغرة.</p>
 *
 * @param id           الطالب المُعدَّل، أو {@code null} لطالب جديد
 * @param barcode      باركود البطاقة؛ فارغاً يُولَّد للجديد ويبقى على حاله للقائم
 * @param name         الاسم، فريدٌ في السنتر
 * @param phone        هاتف الطالب
 * @param parentPhone  هاتف ولي الأمر - إليه تذهب رسائل الغياب والمتأخرات
 * @param schoolLevel  الصف الدراسي، وهو شرط قبوله في المجموعات
 */
public record StudentDraft(
        Long id,

        @Size(max = 50)
        String barcode,

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 15)
        String phone,

        @Size(max = 15)
        String parentPhone,

        SchoolLevel schoolLevel) {

    /** الاسم منزوعَ المسافات: مسافةٌ في الطرف تجعل طالبين مختلفين بالاسم نفسه */
    public String trimmedName() {
        return name == null ? null : name.trim();
    }

    /** الباركود منزوعَ المسافات، أو {@code null} إن لم يُكتب - والفراغ هنا ليس قيمة */
    public String trimmedBarcode() {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return barcode.trim();
    }

    public boolean isNew() {
        return id == null;
    }
}
