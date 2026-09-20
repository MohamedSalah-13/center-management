-- الأساس المشترك لإصداري Desktop وSaaS.
-- لا تُربط الجداول التشغيلية بالمؤسسة في هذا الترحيل: إبقاء الخطوتين منفصلتين
-- يسمح بترقية قواعد العملاء الحالية أولاً ثم إضافة tenant_id مع backfill واضح.

CREATE TABLE tenants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_slug UNIQUE (slug)
) engine=InnoDB;

INSERT INTO tenants (id, name, slug, active)
VALUES (
    1,
    COALESCE((SELECT center_name FROM center_settings WHERE id = 1), 'Desktop Center'),
    'desktop',
    b'1'
);

CREATE TABLE branches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_branch_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) engine=InnoDB;

INSERT INTO branches (id, tenant_id, name, code, active)
VALUES (1, 1, 'Main Branch', 'MAIN', b'1');
