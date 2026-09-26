package com.codejava.center.core.catalog;
import java.util.Locale;
import java.util.Map;
/** مفتاح موحد يمنع تسجيل المادة نفسها بتهجئات واختصارات مختلفة. */
public final class SubjectNames {
    private SubjectNames() { }
    public static String normalized(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Z}\\u0640\\u064B-\\u065F\\u0670]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه');
    }
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("e", "english"), Map.entry("english", "english"),
            Map.entry("انجليزي", "english"), Map.entry("انجليزيه", "english"),
            Map.entry("الانجليزيه", "english"), Map.entry("لغهانجليزيه", "english"),
            Map.entry("اللغهالانجليزيه", "english"),
            Map.entry("عربي", "arabic"), Map.entry("عربيه", "arabic"),
            Map.entry("لغهعربيه", "arabic"), Map.entry("اللغهالعربيه", "arabic"),
            Map.entry("arabic", "arabic"),
            Map.entry("رياضيات", "math"), Map.entry("الرياضيات", "math"),
            Map.entry("math", "math"), Map.entry("maths", "math"), Map.entry("mathematics", "math"),
            Map.entry("علوم", "science"), Map.entry("العلوم", "science"), Map.entry("science", "science"));
    public static String key(String name) {
        String normalized = normalized(name);
        return ALIASES.getOrDefault(normalized, "custom:" + normalized);
    }
}
