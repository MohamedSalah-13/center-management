<#
.SYNOPSIS
    يُجهّز قاعدة بيانات وبيانات اتصال جاهزة للبرنامج على جهاز العميل، بلا كتابة SQL يدوياً.

.DESCRIPTION
    يشغَّل مرة واحدة على جهاز العميل بعد تثبيت MySQL 8 وقبل أول تشغيل للبرنامج. ينشئ
    قاعدة center_db ومستخدماً مخصصاً لها بكلمة مرور عشوائية قوية، بصلاحيات مقصورة على
    هذه القاعدة وحدها (لا root، ولا صلاحية على مستوى الخادم) - راجع القسم 2 من
    docs/first-install.md لتفسير كل صلاحية.

    يبحث عن mysql.exe بنفس منطق util/MySqlLocator.java في PATH ثم مجلدات التركيب
    المعروفة، فلا حاجة لضبط أي مسار يدوياً في الحالة الشائعة.

    بعد الإنشاء يضبط DB_USERNAME وDB_PASSWORD عبر setx تلقائياً - لا حاجة لكتابتهما
    ولا لتذكّر كلمة المرور، لكن افتح طرفية/جلسة جديدة بعدها (نفس قيد setx نفسه).

.PARAMETER RootUser
    مستخدم MySQL الذي يملك صلاحية الإنشاء (افتراضياً root، يُطلب مرة واحدة فقط).

.PARAMETER DbHost
    عنوان خادم MySQL (افتراضياً localhost).

.PARAMETER Port
    منفذ MySQL (افتراضياً 3306).

.PARAMETER Database
    اسم قاعدة البيانات (افتراضياً center_db - غيّره فقط لو غيّرت application.properties أيضاً).

.PARAMETER AppUser
    اسم مستخدم البرنامج (افتراضياً center_app).

.PARAMETER ResetPassword
    لو كان المستخدم موجوداً بالفعل، يولّد كلمة مرور جديدة له ويحدّث setx بدل تركه كما هو.
    بلا هذا المفتاح: مستخدم موجود يُترك بكلمة مروره الحالية ولا يُلمس DB_PASSWORD.

.EXAMPLE
    .\packaging\setup-database.ps1
    .\packaging\setup-database.ps1 -DbHost 192.168.1.10 -ResetPassword
#>
param(
    [string]$RootUser = 'root',
    [string]$DbHost = 'localhost',
    [int]$Port = 3306,
    [string]$Database = 'center_db',
    [string]$AppUser = 'center_app',
    [switch]$ResetPassword
)

$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------- إيجاد mysql.exe

# نفس مجلدات التركيب المعروفة في util/MySqlLocator.java - غيّر هناك أيضاً لو أضفت مجلداً
$KnownRoots = @(
    'C:\Program Files\MySQL',
    'C:\Program Files (x86)\MySQL',
    'C:\xampp\mysql',
    'C:\wamp64\bin\mysql',
    'C:\wamp\bin\mysql'
)

function Resolve-MySqlExe {
    $onPath = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    foreach ($root in $KnownRoots) {
        if (-not (Test-Path $root)) { continue }

        $direct = Join-Path $root 'bin\mysql.exe'
        if (Test-Path $direct) { return $direct }

        # مجلد تركيب رسمي يحمل اسم الإصدار (MySQL Server 8.0) - نأخذ الأحدث أبجدياً
        $versioned = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\mysql.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($versioned) { return $versioned }
    }

    throw "لم يُعثر على mysql.exe. تأكد أن MySQL 8 مثبَّت، أو أضف مجلد bin الخاص به إلى PATH."
}

$mysqlExe = Resolve-MySqlExe
Write-Host "mysql: $mysqlExe" -ForegroundColor DarkGray

# ------------------------------------------------------------- كلمة مرور root

$securePassword = Read-Host "كلمة مرور مستخدم '$RootUser' على MySQL" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

# MYSQL_PWD بدل تمرير كلمة المرور في سطر الأوامر - قائمة العمليات على الجهاز تعرض
# سطر الأوامر لأي مستخدم آخر، ومتغير بيئة العملية الحالية لا يظهر هناك
function Invoke-MySql {
    param([string]$Sql, [string]$AsUser = $RootUser, [string]$AsPassword = $rootPassword)

    $env:MYSQL_PWD = $AsPassword
    try {
        $output = & $mysqlExe --host=$DbHost --port=$Port --user=$AsUser `
            --default-character-set=utf8mb4 --batch --skip-column-names `
            -e $Sql 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "فشل mysql: $output"
        }
        return $output
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

Write-Host "==> التحقق من الاتصال" -ForegroundColor Cyan
Invoke-MySql -Sql 'SELECT 1;' | Out-Null

# ------------------------------------------------------------- القاعدة والمستخدم

Write-Host "==> إنشاء قاعدة البيانات (لو لم تكن موجودة)" -ForegroundColor Cyan
Invoke-MySql -Sql @"
CREATE DATABASE IF NOT EXISTS ``$Database``
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
"@ | Out-Null

$userExists = (Invoke-MySql -Sql "SELECT COUNT(*) FROM mysql.user WHERE user='$AppUser' AND host='localhost';").Trim() -eq '1'

$shouldSetPassword = (-not $userExists) -or $ResetPassword
$appPassword = $null

if ($shouldSetPassword) {
    # 24 حرفاً من مجموعة تتجنب علامات الاقتباس والفاصلة العكسية حتى لا تكسر SQL لاحقاً
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%^*_+-='
    $appPassword = -join ((1..24) | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

if (-not $userExists) {
    Write-Host "==> إنشاء مستخدم '$AppUser'" -ForegroundColor Cyan
    Invoke-MySql -Sql "CREATE USER '$AppUser'@'localhost' IDENTIFIED BY '$appPassword';" | Out-Null
} elseif ($ResetPassword) {
    Write-Host "==> تحديث كلمة مرور '$AppUser' الموجود مسبقاً" -ForegroundColor Cyan
    Invoke-MySql -Sql "ALTER USER '$AppUser'@'localhost' IDENTIFIED BY '$appPassword';" | Out-Null
} else {
    Write-Host "المستخدم '$AppUser' موجود بالفعل - تُركت كلمة مروره كما هي. شغّل بـ -ResetPassword لتغييرها." -ForegroundColor Yellow
}

Write-Host "==> ضبط الصلاحيات على '$Database' وحدها" -ForegroundColor Cyan
Invoke-MySql -Sql @"
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON ``$Database``.* TO '$AppUser'@'localhost';
FLUSH PRIVILEGES;
"@ | Out-Null

# ------------------------------------------------------------- متغيرات البيئة

if ($shouldSetPassword) {
    Write-Host "==> ضبط DB_USERNAME وDB_PASSWORD لحساب المستخدم الحالي" -ForegroundColor Cyan
    setx DB_USERNAME $AppUser | Out-Null
    setx DB_PASSWORD $appPassword | Out-Null

    Write-Host ""
    Write-Host "تم." -ForegroundColor Green
    Write-Host "افتح طرفية جديدة (أو أعد تشغيل الجهاز) قبل تشغيل البرنامج - setx لا يصل" -ForegroundColor Yellow
    Write-Host "الطرفيات المفتوحة حالياً." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "تم ضبط القاعدة والصلاحيات. لم تُلمس DB_USERNAME/DB_PASSWORD لأن المستخدم" -ForegroundColor Green
    Write-Host "كان موجوداً مسبقاً بكلمة مرور غير معروفة لهذا السكربت." -ForegroundColor Yellow
}

Remove-Variable rootPassword, appPassword -ErrorAction SilentlyContinue
