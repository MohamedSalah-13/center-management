# نظام إدارة السنتر التعليمي (Educational Center Management System)

تطبيق سطح مكتب متكامل وبرمجي ذو أداء عالٍ مخصص لإدارة المراكز التعليمية (السنتر)، متابعة حضور وغياب الطلاب بالباركود، إدارة الخزينة والمالية، حساب مستحقات المعلمين، وإدارة المجموعات الدراسية.

---

## 🛠️ التقنيات المستخدمة (Tech Stack)

- **اللغة:** Java 17+
- **إطار عمل خلفي (Backend):** Spring Boot 3.x (Spring Data JPA / Hibernate)
- **الأمان والتشفير (Security):** Spring Security (BCryptPasswordEncoder)
- **واجهة المستخدم (UI):** JavaFX 17 + FXML
- **قاعدة البيانات (Database):** MySQL 8+ (مُعدّلة لتجاوز مشكلة Public Key Retrieval)
- **إدارة التبعيات والمكتبات:** Lombok, Maven
- **معالجة الواجهات المتزامنة (Asynchronous UI):** `CompletableFuture` & `Platform.runLater()`

---

## 🏛️ البنية المعمارية (Architecture & Integration Design)

تم دمج **Spring Boot** مع **JavaFX** باستخدام نمط مراقب الأحداث (`ApplicationListener` / `StageReadyEvent`).
تُدار جميع كائنات التحكم (`Controllers`) وخدمات البيانات (`Services`) بالكامل بوساطة **Spring IoC Container** عبر الخاصية:
`fxmlLoader.setControllerFactory(applicationContext::getBean)`

تم حل مشكلات التبعية الدائرية (Circular Dependency) بفصل إعدادات الأمان في كلاس `Configuration` مستقل.

### هيكل المجلدات الرئيسي (Project Structure):
```text
src/
└── main/
    ├── java/com/codejava/center/
    │   ├── CenterApplication.java       # نقطة تشغيل Spring Boot وتوليد مستخدم افتراضي (CommandLineRunner)
    │   ├── JavaFxApplication.java       # دورة حياة JavaFX
    │   ├── config/                      # إعدادات الشاشات والأمان (SecurityConfig, StageReadyEvent)
    │   ├── domain/                      # كيانات قاعدة البيانات (Entities & Enums)
    │   ├── repository/                  # واجهات التعامل مع قاعدة البيانات (Spring Data JPA)
    │   ├── service/                     # طبقة الأعمال والخدمات (Services & DTOs)
    │   ├── util/                        # فئات المساعدة (UserSession لإدارة الجلسات والصلاحيات)
    │   └── controller/                  # متحكمات واجهات المستخدم (JavaFX Controllers)
    └── resources/
        ├── application.properties       # إعدادات الاتصال بالداتابيز MySQL
        └── fxml/                        # ملفات تصميم الواجهات FXML

✅ ما تم إنجازه بالكامل (Completed Features)
1. طبقة البيانات والنماذج (Domain Entities & Repositories)
User: حسابات النظام (تشفير كلمات المرور بـ BCrypt، وتحديد الصلاحيات Role).

Student: بيانات الطالب والباركود الفريد وحالة الحساب.

Teacher: بيانات المعلم ونظام العمولات.

CourseGroup: المجموعات الدراسية، السعة، والأسعار.

StudentGroup: ربط الطلاب بالمجموعات.

Session: الحصص الدراسية وتتبع حالة الصرف.

Attendance: سجلات الحضور والانصراف اللحظية.

Transaction: الحركة المالية الشاملة للخزينة.

2. طبقة الخدمات (Services & Business Logic)
AuthService: التحقق الآمن من بيانات الدخول بمقارنة التشفير (Password Matching).

StudentService: إدارة بيانات الطلاب والباركود (إضافة، تعديل، حذف).

CourseGroupService & TeacherService: إدارة الحلقات الدراسية والمعلمين وحساب تصفية المستحقات (إضافة، تعديل، حذف).

SessionService: فتح الحصص الجديدة وإغلاق الحصص النشطة للتحكم في عمليات الحضور.

AttendanceService: التحقق اللحظي من حالة الطالب المادية واشتراكه في المجموعة والحصة النشطة.

TransactionService: تسجيل الواردات، وإكمال تسجيل مستحقات المعلمين (recordTeacherPayout) تمهيداً لجرد الخزينة.

3. الواجهات والمتحكمات (UI & Controllers)
Login / Dashboard:

نظام تسجيل دخول آمن يُنشئ الجلسة (UserSession).

تطبيق الصلاحيات (RBAC) لإخفاء/إظهار أزرار (الخزينة، المجموعات) بناءً على دور المستخدم (سكرتارية / إدارة).

SessionManagement:

شاشة تحكم لفتح (Active) وإنهاء الحصص وربطها بالمجموعات الدراسية والتاريخ.

AttendanceScreen:

شاشة تعمل بالباركود بخلفية (Async) وتعتمد على حالة الحصة النشطة.

تنبيهات صوتية (Beep) للنجاح أو الرفض.

تفريغ الشاشة آلياً بعد 3 ثوانٍ باستخدام PauseTransition لتستعد للطالب التالي.

شاشات الإدارة الشاملة (CRUD):

StudentRegistration & GroupManagement & TeacherManagement: إدخال وإدارة الكيانات الأساسية بالكامل، مع تطبيق نمط (تحديد الصف من الجدول TableView) لتعبئة الحقول وتفعيل أزرار (التعديل/الحذف) ديناميكياً.

CashierScreen: دفع الاشتراكات آلياً بمجرد تمرير باركود الطالب.

🚀 الخارطة المتبقية للتكملة (Remaining Roadmap)
~~Phase 1 (متبقي من الأساسيات): إدارة الحصص وتعديل البيانات~~ ✅ (تم الإنجاز)

[x] بناء شاشة SessionManagement.fxml لفتح حصة (Active Session) قبل بدء استقبال الطلاب في شاشة الحضور.

[x] إضافة إمكانية التعديل (Edit) والحذف (Delete) في شاشات الإدارة (المعلمين، الطلاب، المجموعات).

Phase 2: التقارير، الطباعة (Printing & PDFs)
[ ] دمج مكتبة طباعة إيصالات سريعة عند دفع اشتراك في شاشة الخزينة (JavaFX Printing API).

[ ] استخراج كشوفات PDF لحضور وغياب طلاب مجموعة معينة.

[ ] طباعة كشف حساب/إيصال استلام نقدية عند صرف مستحقات المعلم.

Phase 3: تقفيل الوردية والمالية التفصيلية
[ ] واجهة تقفيل الخزينة وتصفية الوردية (Shift Closing UI) تعتمد على تجميع حركات (الدخل - المنصرف).

[ ] واجهة تسجيل المصروفات النثرية الصادرة من السنتر (إيجار، كهرباء، صيانة).

Phase 4: لوحة القيادة التفاعلية (Dashboard Analytics)
[ ] تصميم بطاقات إحصائية حية للشاشة الرئيسية (إجمالي حضور اليوم، الإيرادات اللحظية، الحصص المفتوحة).

[ ] رسوم بيانية (JavaFX Charts) لإيرادات السنتر وتوزيع الطلاب.

Phase 5: التغليف والإنتاج (Deployment & Backup)
[ ] إنشاء سكربت مهام مجدولة لأتمتة النسخ الاحتياطي التلقائي (Database Backup).

[ ] حزم التطبيق بصيغة تنفيذية مستقلة .exe للتثبيت المباشر عند العملاء.

🤖 توجيه للذكاء الاصطناعي (Prompt Template for AI Conversations)
عند فتح محادثة جديدة مع الذكاء الاصطناعي لاستكمال أي مرحلة، يمكنك نسخ الفقرة التالية:

نص التوجيه (Prompt):
"أعمل على مشروع إدارة السنتر التعليمي المكتوب بلغة Java 17 + Spring Boot + JavaFX + MySQL. التطبيق يستخدم Spring Security لتشفير كلمات المرور، ونمط Asynchronous للواجهات، ويدير الجلسات عبر UserSession.
يرجى قراءة ملف README الخاص بالمشروع لفهم هيكل الكود، الكيانات المسجلة، وما تم إنجازه (تم الانتهاء من Phase 1 بالكامل).
أريد الآن البدء في تنفيذ الميزة التالية: [Phase 2 - التقارير والطباعة] مع الالتزام بنفس أسلوب المعمارية."