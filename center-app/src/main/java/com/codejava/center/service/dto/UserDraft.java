package com.codejava.center.service.dto;

import com.codejava.center.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ما يُطلب حفظه من حساب مستخدم: ثلاثة حقول، ولا شيء غيرها.
 *
 * <p>كان مدخل {@code UserService.saveUser} كيانَ {@code User} نفسه. على الجهاز لا فرق -
 * الشاشة هي من يبنيه - وعلى منفذ HTTP هو mass assignment بعينه: الربط التلقائي يملأ من
 * جسم الطلب <b>كل</b> ما يجد له اسماً، فطلبٌ وُضع ليغيّر كلمة مرور يحمل معه
 * {@code role=ADMIN} فيُكتب. والمصمّم لم يقصده ولا شيء في الكود يمنعه.</p>
 *
 * <p>والحماية ليست في أن هذا سجلّ بدل كيان، بل في أنه <b>يحمل ما يُسمح بتغييره فقط</b>:
 * كلمةُ المرور ليست فيه - تصل نصّاً صريحاً وتُبصم في الخدمة، فلا سبيل إلى تمرير بصمةٍ
 * جاهزة - ولا شيء يمرّ منه إلى الصفّ المحفوظ إلا هذه الثلاثة.</p>
 *
 * <p>والقيود مُعلنة هنا لا مفحوصةً في الشاشة: شاشةٌ تفحص تحمي نفسها وحدها، وحدٌّ على
 * النوع يحمي كل من يكتب - بما فيه ما لم يُكتب بعد.</p>
 *
 * @param id       الحساب المُعدَّل، أو {@code null} لحساب جديد
 * @param username اسم الدخول، فريدٌ ويُقارَن بلا حساسية لحالة الحرف
 * @param role     الصلاحية
 */
public record UserDraft(
        Long id,

        @NotBlank
        @Size(max = 50)
        String username,

        @NotNull
        Role role) {

    /** الاسم منزوعَ المسافات: مسافةٌ في الطرف تجعل حسابين مختلفين بالاسم نفسه */
    public String trimmedUsername() {
        return username == null ? null : username.trim();
    }

    public boolean isNew() {
        return id == null;
    }
}
