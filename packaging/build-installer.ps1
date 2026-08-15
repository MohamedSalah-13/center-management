<#
.SYNOPSIS
    يبني نسخة قابلة للتثبيت من نظام إدارة السنتر عبر jpackage.

.DESCRIPTION
    ينتج حزمة تتضمّن Java نفسها، فلا يحتاج جهاز العميل تثبيت Java ولا Maven.

    نوعان:
      app-image  مجلد فيه ملف تنفيذي - لا يحتاج أي أدوات إضافية (الافتراضي)
      msi        مثبِّت ويندوز - يتطلب WiX Toolset 3.x مثبَّتاً مسبقاً

.PARAMETER Type
    app-image أو msi

.EXAMPLE
    .\packaging\build-installer.ps1
    .\packaging\build-installer.ps1 -Type msi
#>
param(
    [ValidateSet('app-image', 'msi')]
    [string]$Type = 'app-image'
)

$ErrorActionPreference = 'Stop'

$AppName = 'CenterManagement'
$AppVersion = '1.0.0'
$JarName = "center-management-$AppVersion.jar"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# jpackage جزء من JDK لكنه غالباً ليس في PATH، فنبحث عنه بجوار java
function Resolve-JPackage {
    $onPath = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') }

    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { $candidates += (Join-Path (Split-Path -Parent $java.Source) 'jpackage.exe') }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }

    throw "لم يُعثر على jpackage. يتطلب JDK 14 أو أحدث، وهذا المشروع يبنى على JDK 21. اضبط JAVA_HOME على مجلد JDK."
}

$jpackage = Resolve-JPackage
Write-Host "jpackage: $jpackage" -ForegroundColor DarkGray

Write-Host "==> بناء الحزمة" -ForegroundColor Cyan
& mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "فشل بناء Maven" }

$jar = Join-Path $root "target\$JarName"
if (-not (Test-Path $jar)) { throw "لم يُعثر على الحزمة: $jar" }

# jpackage ينسخ مجلد الإدخال كاملاً، فنعزل الحزمة وحدها
# وإلا نُسخت مخرجات الترجمة والاختبارات داخل التطبيق
$stage = Join-Path $root 'target\jpackage-input'
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item $jar $stage

$dest = Join-Path $root 'target\installer'
Remove-Item $dest -Recurse -Force -ErrorAction SilentlyContinue

$jpackageArgs = @(
    '--type', $Type,
    '--name', $AppName,
    '--app-version', $AppVersion,
    '--input', $stage,
    '--main-jar', $JarName,
    '--dest', $dest,
    '--description', 'Educational Center Management System',
    # الواجهة والبيانات بالعربية: بدون هذا تظهر الحروف مشوّهة على بعض الأنظمة
    '--java-options', '-Dfile.encoding=UTF-8'
)

$icon = Join-Path $PSScriptRoot 'app.ico'
if (Test-Path $icon) {
    $jpackageArgs += @('--icon', $icon)
} else {
    Write-Host "لا توجد أيقونة في packaging\app.ico — ستُستخدم أيقونة Java الافتراضية." -ForegroundColor DarkGray
    Write-Host "ولّدها بـ: java packaging\IconBuilder.java" -ForegroundColor DarkGray
}

if ($Type -eq 'msi') {
    $jpackageArgs += @(
        '--win-dir-chooser',      # يسمح للعميل باختيار مسار التثبيت
        '--win-menu',
        '--win-shortcut',
        '--win-menu-group', 'Center Management'
    )
}

Write-Host "==> jpackage ($Type)" -ForegroundColor Cyan
& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    if ($Type -eq 'msi') {
        Write-Host "فشل بناء MSI. النوع msi يتطلب WiX Toolset 3.x:" -ForegroundColor Yellow
        Write-Host "  https://github.com/wixtoolset/wix3/releases" -ForegroundColor Yellow
        Write-Host "أو استخدم النوع app-image الذي لا يحتاج أدوات إضافية." -ForegroundColor Yellow
    }
    throw "فشل jpackage"
}

Write-Host ""
Write-Host "تم. الناتج في: $dest" -ForegroundColor Green
Write-Host ""
Write-Host "قبل التشغيل على جهاز العميل يجب ضبط متغيرات الاتصال:" -ForegroundColor Yellow
Write-Host '  setx DB_USERNAME "center_app"'
Write-Host '  setx DB_PASSWORD "..."'
Write-Host ""
Write-Host "راجع docs/first-install.md للخطوات الكاملة." -ForegroundColor Yellow
