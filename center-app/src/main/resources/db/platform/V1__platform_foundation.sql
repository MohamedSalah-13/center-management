-- سجلّ المنصة: من هي المؤسسات، وأين قاعدة كلٍّ منها، وما حال اشتراكها.
--
-- قاعدةٌ مستقلة (center_platform) لا schema داخل مؤسسة: هذا الجدول يقرّر من تُخدَم،
-- فوضعُه داخل إحدى المؤسسات يجعل مؤسسةً تملك قائمة جاراتها. ولهذا أيضاً ترحيلاته
-- منفصلة عن ترحيلات المؤسسة: الاثنان يترقّيان في مواعيد مختلفة وبمعدّل مختلف.
--
-- وجدول tenants الذي أنشأه V16 داخل schema المؤسسة يبقى كما هو ولا يُبنى عليه:
-- هو سجلّ محلّي لسنترٍ واحد على جهازه، لا سجلّ المنصة. راجع docs/saas-review-and-plan.md §3.

CREATE TABLE tenants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    -- اسم قاعدة بيانات المؤسسة. 48 حرفاً لا 64: أدوات التشغيل تلحق به لواحق
    schema_name VARCHAR(48) NOT NULL,
    -- ACTIVE تُخدَم، SUSPENDED اشتراك متوقف فلا عمل تلقائي، CLOSED انتهت والبيانات محفوظة
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_platform_tenant_slug UNIQUE (slug),
    -- قاعدتان لمؤسستين لا تتشاركان اسماً: القيد في القاعدة لا في الكود، لأن
    -- فحصاً ثم إدراجاً يترك فجوةً يدخل منها طلبا تزويد متزامنان
    CONSTRAINT uk_platform_tenant_schema UNIQUE (schema_name)
) engine=InnoDB;

-- رمز الدعوة: ما يثبت أن من يفتح حساب المدير الأول هو من طُلبت له المؤسسة.
--
-- على جهازٍ في سنتر كان الدليل أن جدول المستخدمين فارغ - ومن يجلس أمام الجهاز الذي
-- يحمل القاعدة هو صاحبها. على خادمٍ يكفي أن يعرف أحدهم اسم المؤسسة ليصل إلى قاعدةٍ
-- فارغة، ففراغُها لم يعد دليلاً على شيء.
CREATE TABLE tenant_invites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    -- بصمة الرمز لا الرمز: من يقرأ هذا الجدول يصير مديراً لكل مؤسسة لم تُهيَّأ بعد.
    -- والبصمة SHA-256 لا BCrypt عن قصد: الرمز عشوائي بمئة وستين بتاً لا كلمة يختارها
    -- إنسان، فلا شيء يُخمَّن، والبحث عنه يحتاج فهرساً - وBCrypt لا يُفهرَس
    code_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    redeemed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_platform_invite_code UNIQUE (code_hash),
    CONSTRAINT fk_platform_invite_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) engine=InnoDB;
