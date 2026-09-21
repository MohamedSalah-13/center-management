package com.codejava.center.service.dto;

import com.codejava.center.domain.enums.SchoolLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * ما يُطلب حفظه من مجموعة دراسية.
 *
 * <p>نفس قاعدة {@link UserDraft} و{@link StudentDraft}: كيانُ {@code CourseGroup}
 * مدخلاً لـ HTTP هو mass assignment ينتظر رابطَ طلب. وهنا للقاعدة وجهٌ ثانٍ أوضح:
 * <b>{@code teacherId} رقمٌ لا كيانَ {@code Teacher}</b>. كيانُ معلمٍ يصل من جسم
 * الطلب يحمل ما كتبه المُرسِل - اسماً ونوعَ عمولةٍ وقيمتها - و{@code save} على مجموعةٍ
 * تحمله يكتبه في صف المعلم بـ cascade أو يفشل بغموض. أما الرقم فلا يمكنه أن يقول إلا
 * "هذا المعلم"، والصفُّ يُقرأ من القاعدة.</p>
 *
 * <p>والاسم حقلٌ مشروط: {@code autoName} يعني أن النظام يشتقّه عند كل حفظ من (الصف +
 * المعلم + الأيام + الساعة)، فما يُرسَل في {@code name} عندئذٍ مُهمَل - لا مرفوض،
 * لأن الشاشة تعرضه معاينةً وتعيده كما هو. ومن أراد اسماً خاصاً أوقف الاشتقاق، وعندها
 * الاسم بيانٌ يكتبه المستخدم ولا يلمسه النظام.</p>
 *
 * <p>وقيودُ الأعمال - تعارضُ موعد المعلم، والسعرُ غير السالب، والنهايةُ بعد البداية -
 * تبقى في {@code CourseGroupService}: هي التي ترمي رسائل مترجَمة، والحدود المُعلنة هنا
 * هي ما لا يستطيع الرابطُ التلقائي إصلاحه.</p>
 *
 * @param id           المجموعة المُعدَّلة، أو {@code null} لمجموعة جديدة
 * @param teacherId    معلم المجموعة، رقماً
 * @param name         الاسم - يُقرأ حين {@code autoName} مُطفأ وحده
 * @param autoName     هل يُشتق الاسم عند كل حفظ؟
 * @param schoolLevel  الصف الذي تخدمه، وهو شرط قبول الطالب فيها
 * @param maxCapacity  سعة القاعة
 * @param sessionPrice سعر الحصة، يُخصم من رصيد الطالب عند حضوره
 * @param meetingDays  أيام الانعقاد في الأسبوع
 * @param startTime    ساعة البدء، مشتركة بين كل الأيام
 * @param endTime      ساعة الانتهاء
 */
public record CourseGroupDraft(
        Long id,

        @NotNull
        Long teacherId,

        @Size(max = 100)
        String name,

        boolean autoName,

        SchoolLevel schoolLevel,

        Integer maxCapacity,

        BigDecimal sessionPrice,

        Set<DayOfWeek> meetingDays,

        LocalTime startTime,

        LocalTime endTime) {

    public boolean isNew() {
        return id == null;
    }
}
