package com.codejava.center.core.tenant;

import java.util.Locale;
import java.util.Set;

/**
 * اسم قاعدة بيانات المؤسسة، مُتحقَّقاً منه.
 *
 * <p><b>هذا هو الموضع الوحيد في البرنامج الذي يصير فيه نصٌّ من الخارج جزءاً من أمر DDL.</b>
 * إنشاء مؤسسة يكتب {@code CREATE DATABASE <الاسم>}، وتوجيه الاتصال يكتب {@code USE <الاسم>}،
 * وكلاهما لا يقبل معاملاً مُعلَّماً ({@code ?}) - أسماء الكيانات في SQL لا تُمرَّر كقيم. فما
 * لم يُتحقَّق من الاسم هنا، يصير اسمُ مؤسسةٍ يكتبه إنسان جملةً تنفّذها القاعدة.</p>
 *
 * <p>ولهذا القبول <b>بقائمة سماح لا بقائمة منع</b>: حرفٌ صغير ثم حروف وأرقام وشرطة سفلية،
 * لا شيء غيرها. قائمة المنع تُكتب بعدد ما يخطر للكاتب من حيل، وقائمة السماح تُكتب بعدد ما
 * يحتاجه الاسم فعلاً.</p>
 *
 * <p>وهي نقية في النواة مع اختبارها للسبب الذي جعل {@code BackupRetention} كذلك: قرارٌ
 * خطؤه صامت. اسمٌ يمرّ وهو لا يجب أن يمرّ لا يُسقط بناءً ولا يُرى في شاشة.</p>
 */
public record SchemaName(String value) {

    /**
     * الحدّ الأقصى 64 حرفاً في MySQL لاسم قاعدة، ونقف عند 48 لأن أدوات التشغيل تلحق
     * بالاسم لواحق: نسخةٌ للاستعادة، وقاعدةٌ مؤقتة أثناء الترقية.
     */
    private static final int MAX_LENGTH = 48;

    private static final int MIN_LENGTH = 3;

    /** حرفٌ صغير أولاً: اسمٌ يبدأ برقم يحتاج اقتباساً في كل موضع يُكتب فيه */
    private static final String ALLOWED = "abcdefghijklmnopqrstuvwxyz0123456789_";

    /**
     * أسماء لا تُمنح لمؤسسة.
     *
     * <p>الثلاثة الأولى قواعد يملكها خادم MySQL نفسه. و{@code center_platform} هي سجلّ
     * المنصة: مؤسسةٌ تأخذ اسمها تكتب في الجدول الذي يقرّر من هي المؤسسات.</p>
     */
    private static final Set<String> RESERVED =
            Set.of("mysql", "information_schema", "performance_schema", "sys", "center_platform");

    public SchemaName {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("schema name length must be "
                    + MIN_LENGTH + ".." + MAX_LENGTH);
        }
        if (!Character.isLetter(value.charAt(0)) || !Character.isLowerCase(value.charAt(0))) {
            throw new IllegalArgumentException("schema name must start with a lowercase letter");
        }
        for (int index = 0; index < value.length(); index++) {
            if (ALLOWED.indexOf(value.charAt(index)) < 0) {
                throw new IllegalArgumentException("schema name may hold only a-z, 0-9 and _");
            }
        }
        if (RESERVED.contains(value)) {
            throw new IllegalArgumentException("schema name is reserved: " + value);
        }
    }

    /**
     * يقبل ما كُتب بحروف كبيرة أو بمسافات حوله ويرفض ما سوى ذلك.
     *
     * <p>التطبيع محصورٌ في هذين: MySQL على ويندوز لا يفرّق بين حالتَي الحرف في أسماء
     * القواعد وعلى لينكس يفرّق، فمؤسسةٌ باسم {@code Cairo} تصير قاعدتين مختلفتين على
     * خادمين - وهو خطأٌ يظهر يوم الترحيل لا يوم الإنشاء. وما عدا ذلك يُرفض ولا
     * "يُصلَح": اسمٌ صُحّح بصمت يعني مؤسسةً تحمل غير ما طُلب لها.</p>
     */
    public static SchemaName of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("schema name is required");
        }
        return new SchemaName(raw.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
