package com.codejava.center.security;

/**
 * يُرمى عند محاولة استدعاء دالة لا يملك المستخدم الحالي صلاحيتها.
 * الرسالة بالعربية لأنها تُعرض مباشرة للمستخدم في الواجهة.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
