package com.codejava.center.domain.enums;

import com.codejava.center.util.I18n;

public enum NotificationType {
    ABSENCE,
    ARREARS;

    /** الاسم المعروض بلغة الواجهة الحالية - المفتاح {@code notificationType.<NAME>} */
    public String getDisplayName() {
        return I18n.get("notificationType." + name());
    }
}
