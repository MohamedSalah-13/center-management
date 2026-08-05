package com.codejava.center.service.alert;

import java.util.List;

/**
 * حصيلة فحص واحد.
 *
 * @param raised   تنبيهات كُتبت فعلاً في الصندوق (المكرَّر لا يُعدّ)
 * @param messaged رسائل وصلت أولياء الأمور فعلاً
 * @param failures أسباب ما فشل، بنصّها المترجَم، لتعرضها الشاشة كما هي
 */
public record AlertScanResult(int raised, int messaged, List<String> failures) {

    public static AlertScanResult empty() {
        return new AlertScanResult(0, 0, List.of());
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}
