package com.codejava.center.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * يبحث عن مجلد أدوات MySQL ({@code mysqldump}/{@code mysql}) حين لا تكون في {@code PATH}
 * ولا في {@code CENTER_BACKUP_MYSQL_BIN_DIR}، بدل ترك العميل يبحث عنها يدوياً كما في
 * {@code docs/first-install.md}.
 *
 * <p>مثبِّت MySQL الرسمي على ويندوز لا يضيف الأدوات إلى {@code PATH} افتراضياً — وهو نفس
 * السبب الذي جعل النسخ الاحتياطية تفشل صامتة عند عملاء كثيرين قبل أن يُكتب هذا الفحص.
 * البحث هنا فحصُ مجلدات تركيب معروفة على القرص فقط، ولا يستدعي أي عملية خارجية ولا يحتاج
 * صلاحيات إضافية.</p>
 *
 * <p><b>التصريح الصريح ({@code CENTER_BACKUP_MYSQL_BIN_DIR}) يبقى الأولوية دائماً</b> —
 * هذا الصنف لا يُستشار إلا حين يكون ذلك المتغيّر فارغاً؛ راجع
 * {@link com.codejava.center.service.BackupService}.</p>
 *
 * <p>النتيجة تُخزَّن لكل جهاز عبر {@link Preferences} حتى لا يُعاد فحص القرص عند كل نسخة
 * احتياطية. المسار نفسه ليس سرّاً فلا حاجة لتعمية {@link MachineSecret} كما في كلمة مرور
 * النسخ.</p>
 */
public final class MySqlLocator {

    private static final Logger log = LoggerFactory.getLogger(MySqlLocator.class);

    private static final String CACHE_KEY = "mysql.binDir";
    private static final String TOOL = "mysqldump.exe";

    /** مجلدات التركيب الرسمية والشائعة على ويندوز، بترتيب الأولوية */
    private static final List<Path> SEARCH_ROOTS = List.of(
            Path.of("C:\\Program Files\\MySQL"),
            Path.of("C:\\Program Files (x86)\\MySQL"),
            Path.of("C:\\xampp\\mysql"),
            Path.of("C:\\wamp64\\bin\\mysql"),
            Path.of("C:\\wamp\\bin\\mysql")
    );

    private MySqlLocator() {
    }

    /**
     * يعيد مجلد أدوات MySQL، أو {@code null} لو كانت الأدوات في {@code PATH} أصلاً أو
     * تعذّر إيجادها — عندئذٍ يبقى السلوك كما كان: محاولة تشغيل الأداة باسمها المجرّد،
     * ورسالة الفشل من الأداة نفسها هي ما يصل المستخدم.
     */
    public static synchronized String resolve() {
        if (isOnPath()) {
            return null;
        }

        String cached = readCache();
        if (cached != null && Files.exists(Path.of(cached, TOOL))) {
            return cached;
        }

        String found = search();
        if (found != null) {
            log.info("Auto-detected MySQL tools at {}", found);
            writeCache(found);
        }
        return found;
    }

    private static boolean isOnPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isBlank() && Files.exists(Path.of(dir, TOOL))) {
                return true;
            }
        }
        return false;
    }

    private static String search() {
        for (Path root : SEARCH_ROOTS) {
            String found = searchUnder(root);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * يبحث تحت مجلد تركيب عن الأدوات مباشرة ({@code root/bin}، حال XAMPP وWAMP)، وإلا في
     * أحدث مجلد إصدار بداخله ({@code root/MySQL Server 8.0/bin}، حال المثبِّت الرسمي) —
     * الأحدث لأن الاسم يحمل رقم الإصدار ولا سبيل لتخمينه.
     *
     * <p>حزمي لا خاص عمداً: هو الجزء القابل للاختبار بلا مسارات {@code C:\...} حقيقية —
     * راجع {@code MySqlLocatorTest}.</p>
     */
    static String searchUnder(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }

        Path directBin = root.resolve("bin");
        if (Files.exists(directBin.resolve(TOOL))) {
            return directBin.toString();
        }

        try (var children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .map(dir -> dir.resolve("bin"))
                    .filter(bin -> Files.exists(bin.resolve(TOOL)))
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Unable to scan {} for MySQL tools", root, e);
            return null;
        }
    }

    private static String readCache() {
        try {
            return prefs().get(CACHE_KEY, null);
        } catch (SecurityException e) {
            return null;
        }
    }

    private static void writeCache(String binDir) {
        try {
            prefs().put(CACHE_KEY, binDir);
        } catch (RuntimeException e) {
            log.debug("Unable to cache MySQL bin dir", e);
        }
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(MySqlLocator.class);
    }
}
