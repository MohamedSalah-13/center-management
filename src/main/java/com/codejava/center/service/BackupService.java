package com.codejava.center.service;

import com.codejava.center.domain.enums.AuditAction;
import com.codejava.center.domain.enums.Role;
import com.codejava.center.security.RequiresRole;
import com.codejava.center.util.BackupCrypto;
import com.codejava.center.util.BackupPreferences;
import com.codejava.center.util.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * أخذ نسخة احتياطية من قاعدة البيانات واستعادتها عبر {@code mysqldump} و{@code mysql}.
 *
 * <p><b>الخدمة ترمي استثناءً برسالة مترجمة عند الفشل ولا تعيد {@code false}.</b> كانت تعيد
 * قيمة منطقية وتطبع أثر الخطأ في الطرفية، وفي نسخة jpackage لا طرفية أصلاً: كان المستخدم
 * يرى "فشلت العملية" ولا سبيل لمعرفة هل السبب مجلد محذوف أم كلمة مرور خاطئة أم
 * {@code mysqldump} غير موجود. الآن يصل نصّ خطأ الأداة نفسه إلى الشاشة عبر
 * {@link com.codejava.center.util.FxAsync}.</p>
 *
 * <p>{@code executeBackup} <b>بلا حارس صلاحيات عن قصد</b>: المهمة المجدولة تعمل بلا جلسة
 * مستخدم، وإضافة {@code @RequiresRole} إليها تعني توقّف كل النسخ التلقائية. المقيَّد هو
 * {@code restoreBackup} وحده لأنه العملية المدمِّرة.</p>
 *
 * <p>مخرجات {@code mysqldump} تُقرأ من التيار ولا تُكتب بـ {@code -r}: هكذا يمكن تمريرها
 * على التشفير قبل أن تلمس القرص، فلا توجد لحظة يكون فيها ملف صريح بكل بيانات السنتر
 * على الجهاز. راجع {@link BackupCrypto}.</p>
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    /** الطابع الزمني كامل في اسم الملف: باسم يحمل التاريخ وحده كانت نسخة اليوم الثانية تمحو الأولى */
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String FILE_PREFIX = "backup_";
    private static final String SQL_SUFFIX = ".sql";

    /** حدّ أقصى يمنع عملية معلّقة من احتجاز خيط الجدولة إلى الأبد */
    private static final long TIMEOUT_MINUTES = 30;

    /** آخر ما يُعرض من رسائل الأداة عند الفشل؛ الأثر الكامل قد يكون آلاف الأسطر */
    private static final int ERROR_TAIL_CHARS = 600;

    /**
     * أرقام أخطاء MySQL التي تعني نقص صلاحية في مستخدم قاعدة البيانات:
     * 1044 (منع على القاعدة)، 1142 (منع أمر على جدول)، 1227 (صلاحية على مستوى الخادم).
     */
    private static final Pattern PRIVILEGE_ERROR = Pattern.compile("ERROR (1044|1142|1227)");

    /**
     * سجل المراقبة. النسخة المجدولة تعمل بلا جلسة مستخدم فتُنسب إلى النظام، وهو
     * المقصود: لا يصحّ نسبتها إلى آخر من سجّل دخوله على الجهاز.
     */
    private final AuditService auditService;

    // بيانات الاتصال تُقرأ من الإعدادات (متغيرات البيئة) بدلاً من كتابتها داخل الكود
    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    /**
     * مجلد أدوات MySQL حين لا تكون في {@code PATH}.
     * مثبِّت MySQL على ويندوز لا يضيفها إلى المسار افتراضياً، وكان ذلك يعني نسخاً
     * احتياطية لا تعمل عند العميل بلا سبب ظاهر.
     */
    @Value("${center.backup.mysql-bin-dir:}")
    private String mysqlBinDir;

    /**
     * يأخذ نسخة احتياطية ويعيد مسار الملف الناتج.
     *
     * @param retentionCount عدد النسخ المحتفظ بها في المجلد؛ {@code null} أو صفر = الكل
     * @throws IllegalStateException برسالة مترجمة عند أي فشل
     */
    public Path executeBackup(String targetDirectory, Integer retentionCount) {
        if (targetDirectory == null || targetDirectory.isBlank()) {
            throw new IllegalStateException(I18n.get("error.backup.noDirectory"));
        }

        Path directory = Path.of(targetDirectory);
        try {
            // المجلد قد يكون على قرص خارجي غير موصول: الإنشاء يفشل هنا برسالة واضحة
            // بدل أن يفشل mysqldump برسالة عن ملف لا يستطيع فتحه
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException(I18n.format("error.backup.directoryUnusable",
                    directory, messageOf(e)), e);
        }

        boolean encrypt = BackupPreferences.encryptionEnabled();
        char[] passphrase = encrypt ? BackupPreferences.passphrase() : null;
        if (encrypt && (passphrase == null || passphrase.length == 0)) {
            throw new IllegalStateException(I18n.get("error.backup.noPassphrase"));
        }

        Path target = directory.resolve(FILE_PREFIX + LocalDateTime.now().format(FILE_STAMP)
                + SQL_SUFFIX + (encrypt ? BackupCrypto.ENCRYPTED_SUFFIX : ""));

        try {
            dump(target, passphrase);
        } catch (RuntimeException e) {
            deleteQuietly(target); // ملف نصف مكتوب يبدو نسخة صالحة في قائمة المجلد
            // النسخة الفاشلة أهمّ من الناجحة في السجل: لا شيء آخر يُظهر ليلة بلا نسخة
            auditService.recordFailure(AuditAction.BACKUP_CREATED,
                    target.getFileName().toString(), messageOf(e));
            throw e;
        } finally {
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }

        prune(directory, retentionCount);

        auditService.record(AuditAction.BACKUP_CREATED, null, target.getFileName().toString(),
                "encrypted=" + encrypt + "; retention=" + retentionCount);
        return target;
    }

    /**
     * يستعيد نسخة احتياطية فوق قاعدة البيانات الحالية.
     *
     * @param passphrase كلمة مرور فكّ التشفير، تُتجاهل إن كان الملف غير مشفَّر
     */
    @RequiresRole(Role.ADMIN)
    public void restoreBackup(String sqlFilePath, char[] passphrase) {
        Path source = Path.of(sqlFilePath);
        if (!Files.isReadable(source)) {
            throw new IllegalStateException(I18n.format("error.backup.fileUnreadable", source));
        }

        Path plain = source;
        Path temporary = null;
        try {
            if (BackupCrypto.isEncrypted(source)) {
                if (passphrase == null || passphrase.length == 0) {
                    throw new IllegalStateException(I18n.get("error.backup.noPassphrase"));
                }
                temporary = Files.createTempFile("center-restore-", SQL_SUFFIX);
                BackupCrypto.decrypt(source, temporary, passphrase);
                plain = temporary;
            }

            run(processBuilder(mysqlTool("mysql")).redirectInput(plain.toFile()), null, null);

            // الاستعادة تكتب فوق كل شيء، بما فيه سجل المراقبة نفسه: السطر الذي يُكتب هنا
            // هو أول ما يبقى بعدها، وبه يُعرف أن ما قبله محتوى ملف لا تاريخ السنتر.
            //
            // ويُكتب خارج قاعدة الصف "ما لا يمكن تسجيله لا يحدث": القاعدة قد استُبدلت
            // للتوّ، وفشل الكتابة عليها لا يُلغي استعادة تمّت فعلاً - إعلان نجاحها فشلاً
            // يدفع من أمام الشاشة إلى تكرارها، وهي عملية مدمّرة
            try {
                auditService.record(AuditAction.BACKUP_RESTORED, null,
                        source.getFileName().toString(), "encrypted=" + (temporary != null));
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            auditService.recordFailure(AuditAction.BACKUP_RESTORED,
                    source.getFileName().toString(), messageOf(e));
            throw new IllegalStateException(I18n.format("error.backup.restoreFailed", messageOf(e)), e);
        } catch (RuntimeException e) {
            auditService.recordFailure(AuditAction.BACKUP_RESTORED,
                    source.getFileName().toString(), messageOf(e));
            throw e;
        } finally {
            // الملف الوسيط نسخة صريحة كاملة من قاعدة البيانات: لا يُترك على القرص
            deleteQuietly(temporary);
        }
    }

    /** ينفّذ {@code mysqldump} ويكتب مخرجاته - مشفَّرة أو صريحة - في {@code target} */
    private void dump(Path target, char[] passphrase) {
        List<String> command = new ArrayList<>(List.of(
                mysqlTool("mysqldump"),
                "--host=" + host(dbUrl),
                "--port=" + port(dbUrl),
                "--user=" + dbUsername,
                // بدونها يخرج الملف بترميز الأداة الافتراضي فتتحول أسماء الطلاب إلى علامات استفهام
                "--default-character-set=utf8mb4",
                // نسخة متسقة بلا قفل الجداول. ليست تحسيناً فقط: بدونها تلجأ الأداة إلى
                // LOCK TABLES وهي صلاحية لا يملكها مستخدم القاعدة المحدود الصلاحيات الذي
                // يصفه docs/first-install.md، فتفشل النسخة. وهي أيضاً ما يسمح بأخذ نسخة
                // يدوية والسنتر مفتوح والحضور يُسجَّل.
                "--single-transaction",
                // المخطط تديره Flyway ولا يحوي triggers ولا routines ولا events. وقراءتها
                // تتطلب صلاحيات TRIGGER و EVENT إضافية، فطلبها يعني فشل كل النسخ عند من
                // ركّب النظام بالصلاحيات الموصى بها - مقابل نسخ كائنات لا وجود لها.
                "--skip-triggers",
                // بدونها يكتب mysqldump في أول الملف - حين يكون GTID مفعَّلاً على الخادم،
                // وهو حال أغلب صور Docker - سطرَي SET @@SESSION.SQL_LOG_BIN و
                // SET @@GLOBAL.GTID_PURGED. تنفيذهما يتطلب SUPER، فتتوقف الاستعادة عند
                // أول سطر بالخطأ 1227 وقاعدة البيانات لم تُلمس بعد. وهما بيانات نسخ
                // تماثلي لا معنى لها لسنتر بخادم واحد.
                "--set-gtid-purged=OFF",
                // قراءة معلومات مساحات الجداول تتطلب صلاحية PROCESS وهي على مستوى الخادم
                // كله لا على قاعدة السنتر، ولا يملكها المستخدم المحدود الصلاحيات
                "--no-tablespaces",
                // --add-locks (المفعَّلة ضمن --opt افتراضياً) تكتب LOCK TABLES حول صفوف كل
                // جدول لتسريع الإدخال. وهي غير --single-transaction: تلك تخصّ لحظة أخذ
                // النسخة، وهذه أوامر تُكتب داخل الملف وتُنفَّذ وقت الاستعادة - فتحتاج صلاحية
                // LOCK TABLES التي لا يملكها المستخدم، وتتوقف الاستعادة بالخطأ 1044 عند
                // أول جدول. التسريع لا يعني شيئاً بحجم قاعدة سنتر.
                "--skip-add-locks",
                dbName(dbUrl)));

        run(processBuilder(command), target, passphrase);
    }

    /**
     * يشغّل العملية، ويكتب مخرجاتها في {@code target} إن طُلب ذلك.
     *
     * <p>تيار الخطأ يُقرأ على خيط مستقل: أنبوب النظام محدود السعة، وعملية تكتب فيه أكثر
     * منها بينما نحن ننتظر انتهاءها تتوقف هي وننتظر نحن إلى الأبد.</p>
     */
    private void run(ProcessBuilder processBuilder, Path target, char[] passphrase) {
        if (target == null) {
            // مخرجات mysql عند الاستعادة لا تهمّنا، لكن أنبوباً لا يقرؤه أحد يملأ فتتوقف العملية
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IllegalStateException(I18n.format("error.backup.toolMissing",
                    processBuilder.command().get(0), messageOf(e)), e);
        }

        try {
            // StringBuffer لا StringBuilder: يكتب فيه خيط القراءة ونقرؤه نحن، وقد ننتهي
            // من الانتظار قبل أن ينتهي هو
            StringBuffer errors = new StringBuffer();
            Thread drain = drainErrors(process, errors);

            if (target != null) {
                try (InputStream output = process.getInputStream()) {
                    if (passphrase != null) {
                        BackupCrypto.encrypt(output, target, passphrase);
                    } else {
                        Files.copy(output, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException(I18n.format("error.backup.timedOut", TIMEOUT_MINUTES));
            }
            drain.join(TimeUnit.SECONDS.toMillis(5));

            if (process.exitValue() != 0) {
                throw new IllegalStateException(I18n.format("error.backup.toolFailed",
                        process.exitValue(), tail(errors)) + privilegeHint(errors));
            }
            if (target != null && Files.size(target) == 0) {
                // خروج بصفر وملف فارغ: يحدث حين تنجح الأداة ولا تجد جداول تقرؤها
                throw new IllegalStateException(I18n.get("error.backup.emptyOutput"));
            }
        } catch (IOException e) {
            throw new IllegalStateException(I18n.format("error.backup.writeFailed", messageOf(e)), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(I18n.get("error.backup.interrupted"), e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Thread drainErrors(Process process, StringBuffer errors) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errors.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                // انتهت العملية وأُغلق الأنبوب؛ ما جُمع حتى الآن يكفي للتشخيص
            }
        }, "backup-stderr");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * كلمة المرور تُمرَّر عبر متغير البيئة {@code MYSQL_PWD} لا في سطر الأوامر،
     * حتى لا تظهر في قائمة العمليات لأي مستخدم على الجهاز.
     */
    private ProcessBuilder processBuilder(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (dbPassword != null && !dbPassword.isEmpty()) {
            processBuilder.environment().put("MYSQL_PWD", dbPassword);
        }
        return processBuilder;
    }

    private ProcessBuilder processBuilder(String tool) {
        return processBuilder(List.of(tool,
                "--host=" + host(dbUrl),
                "--port=" + port(dbUrl),
                "--user=" + dbUsername,
                "--default-character-set=utf8mb4",
                dbName(dbUrl)));
    }

    private String mysqlTool(String name) {
        return mysqlBinDir == null || mysqlBinDir.isBlank()
                ? name
                : Path.of(mysqlBinDir, name).toString();
    }

    /**
     * يحذف أقدم النسخ ويُبقي {@code keep} منها.
     *
     * <p>الترتيب بالاسم لا بتاريخ التعديل: الاسم يحمل الطابع الزمني ولا يتغيّر بنسخ
     * الملفات من مجلد إلى آخر، بينما تاريخ التعديل يتغيّر.</p>
     */
    private void prune(Path directory, Integer keep) {
        if (keep == null || keep <= 0) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(BackupService::isBackupFile)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .skip(keep)
                    .forEach(BackupService::deleteQuietly);
        } catch (IOException e) {
            // فشل الحذف لا يبطل نسخة نجحت للتو؛ أسوأ ما فيه امتلاء المجلد
        }
    }

    private static boolean isBackupFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(FILE_PREFIX)
                && (name.endsWith(SQL_SUFFIX) || name.endsWith(SQL_SUFFIX + BackupCrypto.ENCRYPTED_SUFFIX));
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // لا شيء مفيد يمكن فعله: الملف مفتوح في برنامج آخر أو المجلد للقراءة فقط
        }
    }

    /**
     * تلميح مكتوب بلغة الواجهة حين يكون سبب الفشل نقص صلاحية لا خطأً في الملف.
     *
     * <p>رسالة {@code mysql} تقول "DROP command denied" ولا تقول ماذا يفعل صاحب السنتر.
     * التمييز بالرقم لا بنصّ الرسالة: الأرقام ثابتة مهما كانت لغة الخادم أو نسخته.</p>
     */
    private String privilegeHint(StringBuffer errors) {
        return PRIVILEGE_ERROR.matcher(errors).find()
                ? System.lineSeparator() + System.lineSeparator() + I18n.get("error.backup.privilegeHint")
                : "";
    }

    private String tail(StringBuffer errors) {
        String text = errors.toString().trim();
        if (text.isEmpty()) {
            return I18n.get("error.backup.noToolOutput");
        }
        return text.length() <= ERROR_TAIL_CHARS ? text
                : "…" + text.substring(text.length() - ERROR_TAIL_CHARS);
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ------------------------------------------------------- تحليل رابط JDBC
    // الدوال الثلاث package-private لا private: هي ما يحدّد على أي قاعدة بيانات تعمل
    // النسخة، وخطؤها يعني نسخة سليمة الشكل من قاعدة أخرى. يغطّيها JdbcUrlParsingTest.

    /**
     * اسم قاعدة البيانات من رابط JDBC.
     * مثال: {@code jdbc:mysql://localhost:3306/center_db?useSSL=false} ← {@code center_db}
     */
    String dbName(String jdbcUrl) {
        String withoutParams = beforeParams(jdbcUrl);
        int lastSlash = withoutParams.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == withoutParams.length() - 1) {
            throw new IllegalStateException(I18n.format("error.backup.dbNameUnresolved", jdbcUrl));
        }
        return withoutParams.substring(lastSlash + 1);
    }

    /**
     * المضيف من الرابط.
     *
     * <p>كانت الأداة تُستدعى بلا {@code --host} ولا {@code --port} فتذهب إلى
     * {@code localhost:3306} دائماً. السنتر الذي يشغّل MySQL في Docker على منفذ آخر، أو
     * على جهاز الخادم في الشبكة، كان يأخذ نسخة من قاعدة أخرى - أو يفشل - بينما البرنامج
     * نفسه يعمل على القاعدة الصحيحة. راجع docs/first-install.md §7.</p>
     */
    String host(String jdbcUrl) {
        return authority(jdbcUrl)[0];
    }

    String port(String jdbcUrl) {
        String[] parts = authority(jdbcUrl);
        return parts.length > 1 ? parts[1] : "3306";
    }

    private String[] authority(String jdbcUrl) {
        String withoutParams = beforeParams(jdbcUrl);
        int start = withoutParams.indexOf("//");
        if (start < 0) {
            throw new IllegalStateException(I18n.format("error.backup.dbNameUnresolved", jdbcUrl));
        }
        String rest = withoutParams.substring(start + 2);
        int slash = rest.indexOf('/');
        String hostAndPort = slash < 0 ? rest : rest.substring(0, slash);
        return hostAndPort.isBlank() ? new String[]{"localhost"} : hostAndPort.split(":", 2);
    }

    private String beforeParams(String jdbcUrl) {
        return jdbcUrl.split("\\?", 2)[0];
    }
}
