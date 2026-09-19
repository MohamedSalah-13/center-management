package com.codejava.center.service.dto;

import com.codejava.center.domain.Alert;

import java.util.List;

/**
 * صفحة من صندوق التنبيهات، ومعها عدد ما طابق الشروط كاملاً.
 *
 * <p>العدد الكامل ليس زينة: شاشةٌ تعرض ألف سطر من عشرة آلاف دون أن تقول ذلك تجعل من
 * ينظر إليها يستنتج أن الباقي لم يقع - وهي نفس القاعدة المتّبعة في {@link AuditPage}.</p>
 */
public record AlertPage(List<Alert> rows, long totalMatching) {

    public boolean isTruncated() {
        return totalMatching > rows.size();
    }
}
