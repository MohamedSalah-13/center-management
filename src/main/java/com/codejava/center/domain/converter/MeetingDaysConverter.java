package com.codejava.center.domain.converter;

import com.codejava.center.util.WeekDays;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * أيام المجموعة في عمود نصي واحد: {@code "SATURDAY,TUESDAY"}.
 *
 * <p>جدول فرعي للأيام كان أنظف على الورق وأخطر في التشغيل: العلاقة الجانبية كسولة
 * افتراضياً، وشاشات هذا البرنامج تعرض المجموعات <b>خارج</b> المعاملة، فكان كل عرض
 * لجدول المجموعات يسقط بـ {@code LazyInitializationException} أو يحتاج
 * {@code JOIN FETCH} في كل استعلام يمسّ مجموعة.</p>
 *
 * <p>تُخزَّن الأسماء لا الأرقام: قيمة {@code "1,3"} في قاعدة البيانات لا تقول شيئاً
 * لمن يقرأ نسخة احتياطية بعد سنة، وتغيّر معناها لو تغيّر ترتيب الأيام.</p>
 */
@Converter
public class MeetingDaysConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        return days.stream()
                .sorted(WeekDays.weekOrder())
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(DayOfWeek::valueOf)
                .sorted(WeekDays.weekOrder())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
