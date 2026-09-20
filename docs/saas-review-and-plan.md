# مراجعة المشروع وخطة التحول إلى SaaS + Desktop

تاريخ المراجعة: 2026-09-20 — على الـ commit `906df5b` (feat: establish multi-module SaaS foundation).

## 1. أين نقف الآن

**الحالة الفعلية للبناء:** `mvn -o clean test` يمر بنجاح: 325 اختباراً، 0 فشل. الاختبار الوحيد
المتخطّى محلياً هو `MySqlMigrationIntegrationTest` (يحتاج Docker) — وهو **سيفشل** في CI، انظر §2.

**ما أنجزه الـ commit فعلاً** (وليس أكثر منه):

- تقسيم Maven إلى `center-core` (4 ملفات: `ActorIdentity`, `CurrentActor`, `TenantContext`, `TenantId`)
  و`center-desktop` (كل شيء آخر: 23,500 سطر).
- الخدمات صارت تعتمد على `CurrentActor` بدل `UserSession` مباشرةً (`AuditService`,
  `UserService`, `AlertService`, `RoleEnforcementAspect`). هذه أفضل خطوة في الـ commit.
- جدولا `tenants` و`branches` (V16) مع صف واحد لكل منهما، غير مربوطين بأي جدول تشغيلي.
- `UserSession.currentTenant()` تعيد الثابت `TenantId.DESKTOP` دائماً.

**ما لم يُنجز بعد:** لا يوجد أي مستهلك لـ `TenantContext`. لا عمود `tenant_id` على أي كيان
تشغيلي. لا آلية فرض (Hibernate filter / multitenancy). لا وحدة ويب. أي أن الـ commit هو
"تحضير للأرض" وليس تحولاً؛ وهذا مقبول كخطوة أولى بشرط ألا نبني فوقه قبل حسم القرار المعماري في §3.

## 2. نتائج المراجعة

### 2.1 خلل يكسر CI الآن (أصلحه أولاً)

`MySqlMigrationIntegrationTest.java:60` يؤكد أن آخر إصدار Flyway هو `"15"`، وقد أضاف الـ commit
`V16`. الاختبار متخطّى محلياً لغياب Docker (`disabledWithoutDocker = true`) لكن
`.github/workflows/build.yml` يشترط تنفيذه فعلاً، فأول push سيفشل. الإصلاح الصحيح: استنتاج
الإصدار المتوقع من ملفات `db/migration` على الـ classpath بدل رقم حرفي، وإضافة فحص
`tenants`/`branches`/`uk_tenant_slug`/`uk_branch_tenant_code`/`fk_branch_tenant` للقيود التي يتحقق منها.

### 2.2 نموذج البيانات: ستة قيود فريدة تتصادم بين المؤسسات

| الجدول.العمود | النوع | الأثر عند تشارك قاعدة واحدة |
|---|---|---|
| `users.username` | فريد عالمياً | مؤسستان لا تستطيعان امتلاك `admin`؛ و`InitialSetupService` يفرض هذا الاسم |
| `alert_rules.type` | صف واحد لكل نوع في القاعدة كلها | سياسة التنبيهات لا يمكن أن تختلف بين مؤسستين |
| `center_settings` | `id = 1` ثابت في الكيان و`SettingsService.SETTINGS_ID` | إعدادات واحدة للجميع (العملة، مسار النسخ، قناة الرسائل…) |
| `students.barcode` | فريد عالمياً | كل مؤسسة تبدأ سلسلة باركود من جديد |
| `students.name` | فريد عالمياً (`Student.java:30`) | **غالباً غير مقصود أصلاً**: طالبان بنفس الاسم لا يُسجَّلان في نفس السنتر حتى اليوم |
| `alerts.dedupe_key` | فريد على `type:entity:window` | **فقدان صامت**: تنبيه `BACKUP_FAILED` لمؤسسة تُبتلعه المؤسسة التي كتبته قبلها في نفس النافذة |

- `User` لا يحمل أي رابط بمؤسسة أو فرع، و`UserRepository.findByUsername` (مسار الدخول) غير محدود النطاق.
- نحو **40 من 69** دالة استعلام معلنة تجمع على الجدول كله بلا نطاق (`findStudentsInArrears`,
  `sumAmountByTypeAndDateRange` وهو رقم لوحة القيادة، `findDistinctActors` الذي يكشف أسماء موظفي
  المؤسسات الأخرى، `findHighestId` علامة استطلاع التنبيهات)، إضافة إلى ~10 استدعاءات `findAll()`
  في الخدمات. هذا هو حجم العمل لو اخترنا القاعدة المشتركة (§3 الخيار ب).
- الاختبارات العادية لا تنفّذ أي ملف ترحيل (H2 + `create-drop` + Flyway معطّل)؛ اختبار MySQL هو
  شبكة الأمان الوحيدة للترحيلات — لذلك §2.1 أولاً.
- الكيانان `Tenant`/`Branch` مطابقان لـ V16 عموداً بعمود. لا صياغة MariaDB في أي ترحيل.

### 2.3 اقتران طبقة الأعمال بجهاز سطح المكتب

هذه هي القائمة التي تمنع تشغيل نفس الخدمات على خادم مشترك، بغض النظر عن نموذج التعدد:

1. **حالتان ساكنتان على مستوى العملية:** `I18n` (اللغة، وتستدعي `Locale.setDefault`)
   و`MoneyUtils` (العملة، يملؤها `CurrencyInitializer` من الصف الوحيد). على خادم: المستخدم الذي
   يبدّل لغته يبدّلها لكل الطلبات، والمؤسسة التي تحفظ عملتها تغيّر عملة الجميع.
   الخدمات تستدعي `I18n` نحو 280 مرة في 27 صفاً (`ReportService` وحده 139)، و10 من الـ enums
   تترجم نفسها.
2. **تفضيلات الجهاز تُقرأ من داخل الخدمات:** `BackupService` (كلمة مرور التشفير من
   `BackupPreferences`)، `NotificationConfigProvider` (رمز WhatsApp من `NotificationPreferences`)،
   `ReportService` (الطابعة و`printsSheetsDirectly` من `PrintPreferences`). على خادم: رمز واحد
   يرسل رسائل كل المؤسسات، وطابعة الخادم هي "الطابعة".
3. **ملفات وعمليات على المضيف:** `ReportService` يفتح PDF بـ `java.awt.Desktop`، يعدّد طابعات
   المضيف، يكتب في `~/Desktop`؛ `BackupService` يشغّل `mysqldump`/`mysql` ويفترض مصدر بيانات
   واحداً؛ `WhatsAppLinkSender` يفتح متصفحاً أو `rundll32`.
4. **ثلاثة مجدولون مفردون** (`BackupScheduler`, `AlertScheduler`, `AlertFeed`) يفترضون صف
   إعدادات واحداً، قاعدة واحدة، وواجهة واحدة متصلة (`AlertFeed` يحمل `sink` واحداً و`lastSeenId`
   واحداً — آخر من يتصل يفوز).
5. **JavaFX داخل غير الواجهة:** `AlertFeed` يستورد `Platform` (الحقل قابل للاستبدال لكن
   الافتراضي يربط الـ bean بالـ toolkit)، و`StageReadyEvent`/`PrimaryStageInitializer` في `config`.
6. **الوقت:** 22 صفاً في الخدمات تستدعي `LocalDate.now()`/`ZoneId.systemDefault()` بلا منطقة
   زمنية للمؤسسة.
7. `SecurityConfig` تحتوي على `BCryptPasswordEncoder` فقط — لا سلسلة فلاتر HTTP على الإطلاق.

النقطة الإيجابية: `domain/`, `repository/`, `security/` خالية من JavaFX تماماً، ولا `UserSession` في
أي خدمة. الفصل قابل للتنفيذ.

### 2.4 الأمان عند التعرض لـ HTTP

ما هو جيد فعلاً: BCrypt، لا استعلام SQL مبني بالنص (كل شيء `@Query` بمعاملات مسماة)،
`ProcessBuilder` بقائمة وسائط لا بسلسلة shell، كلمة مرور MySQL عبر `MYSQL_PWD`، `BackupCrypto`
سليم (PBKDF2 بـ 210,000 دورة، AES-GCM، التحقق من الوسم قبل تشغيل `mysql`)، حماية آخر مدير
تحت قفل تشاؤمي، سياسة السجلات مفروضة باختبار، لا كلمات مرور افتراضية، `InitialSetup` محمي بـ
`count() == 0` داخل المعاملة.

ما يجب إغلاقه قبل أي منفذ HTTP، بالترتيب:

1. `UserSession` singleton = هوية واحدة لكل العملية → تسرب هوية بين الطلبات. يحتاج `CurrentActor`
   بنطاق الطلب.
2. **الافتراضي اليوم هو السماح:** `AttendanceService`, `EnrollmentService`, `SessionService`,
   `SettingsService`, `ReportService`, `DayScheduleService` بلا أي `@RequiresRole` (~41 دالة عامة)،
   منها ما يكتب مالاً (`chargeSession`) وما يعيد تعريف كل الأرصدة (`SettingsService.save` يضبط
   `ledgerStartDate`). `AuditService.recordAs` تسمح بتزوير سطور السجل لو عُرضت.
3. لا قفل ولا تحديد معدل على `AuthService.authenticate`؛ `FailedLoginBurstDetector` يبلّغ فقط
   وبعدّاد عالمي لا لكل مستخدم.
4. **SSRF حقيقي:** `notificationApiUrl` نص حر يُرسل إليه رمز `Bearer` بلا قائمة سماح للمخطط أو
   المضيف (`HttpGatewaySender`, `WhatsAppCloudApiSender`)، ويعود جسم الاستجابة في رسالة الخطأ.
   و`{token}` في قالب الـ URL يضع الرمز في query string.
5. `restoreBackup` يقبل أي مسار قابل للقراءة على المضيف، و`executeBackup` يكتب dump كاملاً في
   أي مجلد يُسمّى. على MySQL مشترك، ملف استعادة مصنوع يكتب في schema مؤسسة أخرى.
6. لا Bean Validation إطلاقاً؛ الكيانات JPA تُمرَّر مباشرة كمدخلات كتابة (`saveUser(User)`,
   `save(CenterSettings)`) → mass assignment على HTTP.
7. `MachineSecret` مفتاحه `SHA-256(purpose|user.name|os.name)` بلا KDF — تعتيم لا تشفير، والوثيقة
   تقول ذلك بصدق؛ على خادم يلزم مخزن أسرار حقيقي.
8. تفاصيل: `DB_PASSWORD` افتراضه سلسلة فارغة لا فشل؛ `useSSL=false` في الـ URL الافتراضي؛
   `PhoneNumbers` مثبّت على `+20`؛ `createInitialAdmin` نقطة غير مصادقة دفاعها الوحيد جدول فارغ
   (في SaaS تحتاج رمز دعوة موقّعاً).

### 2.5 البناء والتوثيق (كلها صغيرة)

- كل أوامر `mvn -pl center-desktop …` في `README.md:33,160`، `CLAUDE.md`/`AGENTS.md:17,28,29,1075`،
  `docs/first-install.md:437` تفشل على `~/.m2` نظيف بدون `-am`. سكربت التغليف صحيح.
- `build.yml:54` يرفع تقارير `center-desktop` فقط؛ فشل في `center-core` لا يظهر له تقرير.
- `CLAUDE.md`/`AGENTS.md` لا يذكران `center-core` ولا حدود الوحدات ولا نية SaaS (README يذكرها).
- `sqlite-jdbc` و`hibernate-community-dialects` في `center-desktop/pom.xml:54-62` غير مستخدمين.
- `Center_System_Roadmap.pdf` لا يحتوي أي مرحلة SaaS؛ خارطة الطريق والكود لا يتطابقان.

## 3. القرار الذي يحدد شكل كل ما بعده: كيف تُعزل المؤسسات؟

| | **أ. Schema لكل مؤسسة** (موصى به) | **ب. قاعدة مشتركة + `tenant_id`** |
|---|---|---|
| تغييرات الاستعلامات | **صفر** — Hibernate يوجّه الاتصال إلى schema المؤسسة | ~40 استعلاماً + 10 `findAll` + كل استعلام مستقبلي |
| القيود الفريدة الستة | تبقى كما هي | تُوسَّع كلها إلى `(tenant_id, …)` ومفتاح `dedupe_key` يعاد تركيبه |
| `center_settings id=1`, `alert_rules` | تبقى كما هي (صف واحد لكل schema) | إعادة تصميم |
| النسخ الاحتياطي/الاستعادة | `mysqldump` لكل schema — الكود الحالي يعمل تقريباً كما هو | تصدير مُرشَّح لكل مؤسسة: إعادة كتابة كاملة |
| خطر تسرب بيانات بين المؤسسات | استعلام منسي لا يستطيع رؤية schema أخرى | استعلام منسي واحد = تسرب |
| Desktop | نفس الكود، schema واحد، لا تغيير | نفس الكود لكن مع `tenant_id = 1` في كل مكان |
| الكلفة التشغيلية | Flyway يُشغَّل على N schema عند الترقية؛ تقارير عبر المؤسسات صعبة | قاعدة واحدة، تقارير المنصة سهلة |
| يناسب | عشرات إلى مئات السناتر، كل واحد بياناته مستقلة (وهذا هو الواقع) | آلاف المستأجرين الصغار مع تحليلات عابرة |

**التوصية: (أ).** الكود الحالي مبني كله على "مؤسسة واحدة في قاعدة واحدة" — الإعدادات المفردة،
مفاتيح التنبيهات، النسخ الاحتياطي بـ `mysqldump`، سجل المراقبة بلا نطاق. الخيار (ب) يعيد كتابة
كل ذلك ويترك خطراً دائماً؛ الخيار (أ) يحوّل المشكلة إلى طبقة اتصال واحدة (`CurrentTenantIdentifierResolver`
+ `MultiTenantConnectionProvider`) يقرأها Hibernate من `TenantContext` الذي أُضيف بالفعل.

نتيجة هذا القرار على V16: جدول `tenants` (ومعه حسابات دخول المنصة) ينتمي إلى **قاعدة المنصة**
(`center_platform`) لا إلى schema كل مؤسسة؛ `branches` ينتمي فعلاً إلى schema المؤسسة. لا تعدّل
V16 (قد تكون طُبّقت)، لكن لا تبنِ فوق `tenants` داخل schema المؤسسة.

## 4. الخطة

الترتيب مقصود: كل مرحلة تُبقي الـ Desktop شغّالاً وقابلاً للإصدار، ولا تبدأ مرحلة قبل تمام سابقتها.

### المرحلة 0 — تثبيت الأرض (أيام)

- [x] إصلاح `MySqlMigrationIntegrationTest` (§2.1) وتشغيله محلياً بـ Docker مرة واحدة —
      تم في worktree `claude/wonderful-morse-944198` (غير مُلتزم بعد): الإصدار المتوقع يُشتق من
      ملفات `db/migration`، وأُضيف فحص `tenants`/`branches`/`fk_branch_tenant`. نجح على MySQL 8
      الحقيقي: 3 اختبارات، 0 فشل.
- [ ] إضافة `-am` إلى كل أمر `-pl center-desktop` في الوثائق الأربع.
- [ ] `build.yml`: رفع `center-core/target/surefire-reports/` أيضاً.
- [ ] `CLAUDE.md`/`AGENTS.md`: فقرة عن حدود الوحدات (ما يدخل `center-core`، وقاعدة "لا `javafx` خارج الواجهة").
- [ ] حذف `sqlite-jdbc` و`hibernate-community-dialects`.
- [ ] حسم §3 كتابةً في `docs/` (ADR قصير) — كل ما بعده يعتمد عليه.
- [ ] قرار بشأن `students.name` الفريد: إبقاؤه أم إزالته بترحيل V17 (توصية: إزالته؛ الباركود هو الهوية).

### المرحلة 1 — فصل وحدة الأعمال عن JavaFX (الأثقل، وهي المكسب الحقيقي)

الهدف: وحدة `center-app` (Spring + JPA + الخدمات، **بلا اعتماد JavaFX في الـ pom**)، بحيث يفشل
التجميع نفسه لو تسرب `javafx.*` إلى الأعمال. هذا هو الفرض الآلي الذي يفتقده التقسيم الحالي.

1. [ ] إنشاء `center-app` ونقل `domain/`, `repository/`, `service/`, `security/`, و`config/` غير
   الجافافكسية إليها. ما يبقى في `center-desktop`: `controller/`, `util/` الواجهية، `JavaFxApplication`,
   `PrimaryStageInitializer`, `StageReadyEvent`, الـ FXML/CSS، وتنفيذات الواجهات أدناه.
2. [ ] `I18n`: اجعل مصدر اللغة قابلاً للتبديل (`LocaleProvider` في `center-core`). Desktop يركّب
   المصدر الساكن الحالي؛ الخادم يركّب مصدراً من الطلب. الـ 280 استدعاء لا تتغير — يتغير ما خلفها فقط.
   أزل `Locale.setDefault` من المسار المشترك (يبقى في Desktop لتعريب `DatePicker`).
3. [ ] `MoneyUtils`: نفس النمط — `CurrencyProvider` يقرأ إعدادات المؤسسة الحالية (بذاكرة مؤقتة
   لكل مؤسسة). `CurrencyInitializer` يصبح تنفيذ Desktop.
4. [ ] الأسرار والتفضيلات خلف واجهات في `center-core`: `BackupSecretStore`, `MessagingSecretStore`,
   `PrintTargetResolver`. Desktop = `java.util.prefs` كما هو؛ الخادم = جدول مشفر أو Vault.
5. [ ] `ReportService`: فصل "التعبئة" (تعيد `byte[]` PDF أو `JasperPrint`) عن "التسليم" (فتح ملف /
   طابعة) — التسليم ينتقل إلى Desktop.
6. [ ] `WhatsAppLinkSender`: يعيد الـ URI فقط؛ فتحه مسؤولية الواجهة.
7. [ ] `AlertFeed`: حقل `Platform::runLater` يصبح حقناً من Desktop؛ الخادم لاحقاً يستبدله بـ SSE.
8. [ ] `BackupService`: مسار الأدوات ومصدر البيانات عبر واجهة `BackupTarget` (Desktop: JDBC URL
   الحالي؛ الخادم: schema المؤسسة).
9. [ ] الكلاسات النقية تنتقل إلى `center-core` مع اختباراتها: `BackupSchedule`, `BackupRetention`,
   `GroupSchedule`, `AlertSchedule`, `PhoneNumbers` (مع بلد قابل للضبط), `PasswordPolicy`,
   جزء `MoneyUtils` الحسابي.
10. [ ] المنطقة الزمنية: `Clock`/`ZoneId` يُحقنان بدل `now()` المباشر (22 صفاً) — تدريجياً، ابدأ
    بالمجدولين و`TransactionService`.

معيار الانتهاء: `center-app` يُجمَّع ويُختبر بلا `javafx-*` في شجرة اعتمادياته، وكل الـ 325 اختباراً
ما زالت خضراء، والـ Desktop يعمل كما كان.

### المرحلة 2 — التعدد في البيانات (بحسب قرار §3، مكتوبة للخيار أ)

- [ ] قاعدة المنصة `center_platform`: `tenants`, `platform_users` (أو ربط `users` بـ tenant)،
  حالة الاشتراك. ترحيلات Flyway مستقلة لها.
- [ ] `TenantSchemaRouter`: `MultiTenantConnectionProvider` + `CurrentTenantIdentifierResolver`
  يقرآن `TenantContext`. Desktop يبقى بـ `TenantId.DESKTOP` وschema واحد — نفس الكود.
- [ ] تزويد مؤسسة جديدة: إنشاء schema + تشغيل Flyway عليه + صف `center_settings` + المدير الأول
  عبر رمز دعوة (يستبدل شرط `count() == 0`).
- [ ] ترقية: حلقة Flyway على كل schema عند الإقلاع، مع تسجيل ما فشل.
- [ ] المجدولون يدورون على المؤسسات النشطة ويضبطون `TenantContext` لكل دورة؛ `AlertFeed` يصبح
  مفتاحاً بالمؤسسة والجلسة.
- [ ] النسخ الاحتياطي لكل schema، والاستعادة مقيدة بـ `--one-database` واسم schema المؤسسة.
- [ ] اختبار تكامل بـ Testcontainers: مؤسستان، عملية في الأولى لا تظهر في الثانية (المجدول والتنبيهات
  والتقارير).

### المرحلة 3 — الأمان قبل أي منفذ HTTP (§2.4 بالترتيب)

- [ ] `CurrentActor` بنطاق الطلب فوق Spring Security (`SecurityContext` + `DelegatingSecurityContextExecutor`)؛
  Desktop يحتفظ بـ `UserSession`.
- [ ] الافتراضي منع: `@RequiresRole` على كل دالة تكتب في الخدمات الست غير المحمية (أو
  `@EnableMethodSecurity` على الخادم)، و`AuditService.recordAs/recordFailure` تصبح غير عامة أو داخلية.
- [ ] قفل الدخول لكل مستخدم/عنوان + تأخير ثابت يخفي فرق التوقيت بين "مستخدم مجهول" و"كلمة خاطئة".
- [ ] `notificationApiUrl`: قائمة سماح `https` فقط، منع الشبكات الخاصة، الرمز في الترويسة فقط.
- [ ] احتواء مسارات النسخ والاستعادة داخل مجلد المؤسسة.
- [ ] DTOs + `spring-boot-starter-validation` للكتابة؛ لا كيان JPA كمدخل HTTP.
- [ ] مخزن أسرار الخادم بدل `MachineSecret`؛ `DB_PASSWORD` بلا افتراض؛ `useSSL=true` على الشبكة.

### المرحلة 4 — وحدة `center-web`

- [ ] REST API فوق `center-app` (نفس الخدمات، لا منطق جديد) + مصادقة JWT/جلسة.
- [ ] واجهة ويب لأدوار السنتر (البداية: الدخول، الطلاب، الحضور، الخزينة، التقارير كـ PDF).
- [ ] SSE للتنبيهات بدل `AlertFeed` المحلي.
- [ ] الـ Desktop يبقى عميلاً محلياً لقاعدة محلية؛ جعله عميلاً للـ API قرار لاحق مستقل.

### المرحلة 5 — التشغيل

- [ ] نسخ احتياطي سحابي لكل مؤسسة (خارطة الطريق تذكره أصلاً)، مراقبة، سجلات لكل مؤسسة، فوترة الاشتراك.
- [ ] تحديث `Center_System_Roadmap.pdf` بمرحلة SaaS حتى يتطابق مع الكود.

## 5. ما يمكن عمله فوراً بلا انتظار أي قرار

كل بنود المرحلة 0، والبنود 4 و5 و6 و9 من المرحلة 1 (واجهات الأسرار، فصل التعبئة عن التسليم،
`WhatsAppLinkSender`، نقل الكلاسات النقية) — كلها تحسّن الـ Desktop نفسه ولا تعتمد على §3.
