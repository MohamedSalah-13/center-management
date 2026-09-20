package com.codejava.center.core.backup;

import java.nio.file.Path;

/**
 * مسارٌ يبقى داخل ما سُمح به.
 *
 * <p>مجلد النسخ الاحتياطي يكتبه صاحب السنتر في شاشة الإعدادات، وملفُّ الاستعادة
 * يختاره من قرصه. على جهازه هذا هو الصواب: قرصُه قرصُه، ومن يجلس أمامه يستطيع أصلاً
 * أن يكتب حيث شاء بلا برنامجنا.</p>
 *
 * <p>وعلى خادمٍ يخدم مئة سنتر ينقلب المعنى: نصٌّ في شاشة أحدهم يصير مسار كتابةٍ على
 * قرص الخادم - {@code dump} كامل بكل بيانات سنترٍ في مجلدٍ يقرؤه غيره، أو ملفُ
 * استعادةٍ يُقرأ من مجلد سنترٍ آخر فتُكتب بياناته فوق بياناتك. ولذلك يُحدّ المسار
 * بجذرٍ يملكه النشر لا العميل.</p>
 *
 * <p><b>والجذر {@code null} يعني بلا حدّ</b>، وهو جواب سطح المكتب. ليس ثغرةً مفتوحة
 * بل وصفٌ للواقع: حدُّ المسار على جهازٍ يجلس أمامه صاحبه لا يمنع شيئاً ويمنع مجلداً
 * على فلاشة أو على شبكة السنتر - وهو أشيع موضع يُطلب فيه حفظ النسخ.</p>
 *
 * <p>والفحص بعد {@code normalize} لا قبله: {@code /srv/a/../../etc} يبدأ بالجذر نصّاً
 * ولا ينتهي داخله. وبـ {@code toAbsolutePath} كذلك، لأن مساراً نسبياً يُقاس من مجلد
 * تشغيل الخادم لا من الجذر المسموح.</p>
 */
public final class ContainedPath {

    private ContainedPath() {
    }

    /**
     * @param root      الجذر المسموح، أو {@code null} لبلا حدّ
     * @param requested ما طلبه المستخدم
     * @return المسار مطبَّعاً ومطلقاً
     * @throws IllegalArgumentException إن خرج عن الجذر
     */
    public static Path resolve(Path root, Path requested) {
        if (requested == null) {
            throw new IllegalArgumentException("path is required");
        }
        Path absolute = requested.toAbsolutePath().normalize();
        if (root == null) {
            return absolute;
        }

        Path allowed = root.toAbsolutePath().normalize();
        if (!absolute.startsWith(allowed)) {
            throw new IllegalArgumentException(
                    "path escapes the folder this deployment allows: " + absolute);
        }
        return absolute;
    }

    /** أيقع هذا المسار داخل الجذر؟ لشاشةٍ تريد أن تقول لا قبل الحفظ */
    public static boolean isInside(Path root, Path requested) {
        try {
            resolve(root, requested);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
