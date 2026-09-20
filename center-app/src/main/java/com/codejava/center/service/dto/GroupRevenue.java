package com.codejava.center.service.dto;

import java.math.BigDecimal;

/**
 * إجمالي إيراد مجموعة دراسية خلال فترة (لمخطط الإيرادات في لوحة القيادة)
 */
public record GroupRevenue(String groupName, BigDecimal total) {
}
