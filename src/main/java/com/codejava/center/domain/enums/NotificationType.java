package com.codejava.center.domain.enums;

public enum NotificationType {
    ABSENCE("إشعار غياب"),
    ARREARS("تذكير بمتأخرات");

    private final String arabicName;

    NotificationType(String arabicName) {
        this.arabicName = arabicName;
    }

    public String getArabicName() {
        return arabicName;
    }
}
