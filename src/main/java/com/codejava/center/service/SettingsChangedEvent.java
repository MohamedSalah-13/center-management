package com.codejava.center.service;

import com.codejava.center.domain.CenterSettings;

/**
 * تُنشر بعد حفظ إعدادات السنتر بنجاح.
 *
 * <p>وُجدت من أجل {@link BackupScheduler}: موعد النسخ صار قابلاً للتعديل، وبلا إشعار كان
 * الموعد الجديد لا يسري إلا بعد إعادة تشغيل البرنامج - أو بعد أن تعمل المهمة بموعدها
 * القديم مرة أخيرة. البديل أن تستدعي شاشة الإعدادات المجدوِل مباشرةً، وهو ربط شاشة
 * بخدمة خلفية لا علاقة لها بها.</p>
 */
public record SettingsChangedEvent(CenterSettings settings) {
}
