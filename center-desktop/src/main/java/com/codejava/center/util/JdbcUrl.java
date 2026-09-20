package com.codejava.center.util;

/**
 * قراءة المضيف والمنفذ واسم القاعدة من رابط JDBC.
 *
 * <p>كان {@code BackupService} يحملها بنفسه، فكانت الخدمة تعرف أن قاعدتها "تلك التي في
 * رابط هذا التطبيق". صارت جواب Desktop وحده على {@code BackupTarget}: خادمٌ يخدم مئة
 * سنتر لا يشتقّ schema السنتر من رابطه هو بل من صفّ المؤسسة.</p>
 *
 * <p><b>ولماذا تُقرأ أصلاً:</b> كانت الأداة تُستدعى بلا {@code --host} ولا {@code --port}
 * فتذهب إلى {@code localhost:3306} مهما كان الرابط. السنتر الذي يشغّل MySQL في Docker على
 * منفذ آخر، أو على جهاز الخادم في الشبكة، كان يأخذ نسخة من قاعدة أخرى - أو يفشل - بينما
 * البرنامج نفسه يعمل على القاعدة الصحيحة. راجع {@code docs/first-install.md} §7.</p>
 *
 * <p>دوالّ خالصة يغطّيها {@code JdbcUrlParsingTest}: خطؤها لا يظهر خطأً بل نسخةً سليمة
 * الشكل من قاعدة ليست قاعدة السنتر.</p>
 */
public final class JdbcUrl {

    private static final String DEFAULT_PORT = "3306";

    private JdbcUrl() {
    }

    /**
     * اسم قاعدة البيانات.
     * مثال: {@code jdbc:mysql://localhost:3306/center_db?useSSL=false} ← {@code center_db}
     */
    public static String database(String jdbcUrl) {
        String withoutParams = beforeParams(jdbcUrl);
        int lastSlash = withoutParams.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == withoutParams.length() - 1) {
            throw new IllegalStateException(I18n.format("error.backup.dbNameUnresolved", jdbcUrl));
        }
        return withoutParams.substring(lastSlash + 1);
    }

    public static String host(String jdbcUrl) {
        return authority(jdbcUrl)[0];
    }

    public static String port(String jdbcUrl) {
        String[] parts = authority(jdbcUrl);
        return parts.length > 1 ? parts[1] : DEFAULT_PORT;
    }

    private static String[] authority(String jdbcUrl) {
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

    private static String beforeParams(String jdbcUrl) {
        return jdbcUrl.split("\\?", 2)[0];
    }
}
