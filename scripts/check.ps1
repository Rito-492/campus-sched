# scripts/check.ps1 —— 统一检查入口（配置经理拥有）
# 用法：pwsh -File scripts/check.ps1 -Scope <foundation|activity|scheduling|change|ui|qa|all>
# 约定（03 契约 §3 / 04 协作规程 §4）：
#   * Surefire 跑 *Test，Failsafe 跑真实 PostgreSQL *IT（verify 不得跳过它们）；
#   * 缺测试文件、0 个测试、缺后端工程、退出码非 0 一律失败，不得吞掉退出码。
# 返回：全部通过退出 0；任一失败退出 1。

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('foundation', 'activity', 'scheduling', 'change', 'ui', 'qa', 'all')]
    [string]$Scope
)

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

$script:failCount = 0

function Fail([string]$m) { Write-Host "  [FAIL] $m" -ForegroundColor Red; $script:failCount++ }
function Ok([string]$m)    { Write-Host "  [OK]   $m" -ForegroundColor Green }

# 后端模块：验证工程就绪 + 存在测试文件 + 跑单元/集成测试并透传退出码
function Test-MavenModule {
    param([string]$Label, [string[]]$Packages)

    Write-Host ""
    Write-Host "== 后端模块：$Label ==" -ForegroundColor Cyan

    $pom  = Join-Path $root 'server\pom.xml'
    $mvnw = Join-Path $root 'server\mvnw.cmd'
    if (-not (Test-Path $pom) -or -not (Test-Path $mvnw)) {
        Fail "$Label：后端工程未就绪（缺 server/pom.xml 或 Maven Wrapper），依赖项目经理交付，无法编译/测试"
        return
    }

    $testRoot = Join-Path $root 'server\src\test\java\com\campus\events'
    $unitCount = 0; $itCount = 0
    foreach ($p in $Packages) {
        $dir = Join-Path $testRoot $p
        if (Test-Path $dir) {
            $unitCount += @(Get-ChildItem -Path $dir -Recurse -Filter '*Test.java' -File -ErrorAction SilentlyContinue).Count
            $itCount   += @(Get-ChildItem -Path $dir -Recurse -Filter '*IT.java'   -File -ErrorAction SilentlyContinue).Count
        }
    }
    if ($unitCount -eq 0 -and $itCount -eq 0) {
        Fail "$Label：无测试文件（缺 *Test/*IT），不得记为通过"
        return
    }

    # 单元测试（Surefire *Test）
    $unitPattern = ($Packages | ForEach-Object { "com.campus.events.$_.*Test" }) -join ','
    Write-Host "  mvnw -Dtest=$unitPattern -DfailIfNoTests=true test"
    & $mvnw -f server\pom.xml "-Dtest=$unitPattern" -DfailIfNoTests=true test
    if ($LASTEXITCODE -ne 0) { Fail "$Label：单元测试失败（退出码 $LASTEXITCODE）" } else { Ok "$Label 单元测试" }

    # 集成测试（Failsafe *IT，真实库）
    $itPattern = ($Packages | ForEach-Object { "com.campus.events.$_.*IT" }) -join ','
    Write-Host "  mvnw -Dit.test=$itPattern -DfailIfNoTests=true verify"
    & $mvnw -f server\pom.xml "-Dit.test=$itPattern" -DfailIfNoTests=true verify
    if ($LASTEXITCODE -ne 0) { Fail "$Label：集成测试失败（退出码 $LASTEXITCODE）" } else { Ok "$Label 集成测试" }
}

# 前端/QA 工程：验证 package.json 就绪 + npm ci + 逐项脚本并透传退出码
function Test-NpmProject {
    param([string]$Label, [string]$Dir, [string[]]$Scripts)

    Write-Host ""
    Write-Host "== 工程：$Label ==" -ForegroundColor Cyan

    $pkg = Join-Path $root "$Dir\package.json"
    if (-not (Test-Path $pkg)) {
        Fail "$Label：缺少 $Dir/package.json，工程未就绪"
        return
    }

    Write-Host "  npm --prefix $Dir ci"
    & npm --prefix $Dir ci
    if ($LASTEXITCODE -ne 0) { Fail "$Label：npm ci 失败（退出码 $LASTEXITCODE）"; return }

    foreach ($s in $Scripts) {
        Write-Host "  npm --prefix $Dir run $s"
        & npm --prefix $Dir run $s
        if ($LASTEXITCODE -ne 0) { Fail "$Label：npm run $s 失败（退出码 $LASTEXITCODE）" } else { Ok "$Label $s" }
    }
}

Write-Host "=== check.ps1 -Scope $Scope（$root）===" -ForegroundColor Cyan

$scopes = if ($Scope -eq 'all') {
    @('foundation', 'activity', 'scheduling', 'change', 'ui', 'qa')
} else { @($Scope) }

foreach ($s in $scopes) {
    switch ($s) {
        'foundation' { Test-MavenModule 'foundation（PM 公共工程/账号/契约）' @('foundation', 'auth', 'contract') }
        'activity'   { Test-MavenModule 'activity（AN 活动/报名）' @('activity', 'registration') }
        'scheduling' { Test-MavenModule 'scheduling（DS 资源/调度）' @('resource', 'scheduling') }
        'change'     { Test-MavenModule 'change（CM 改期/通知/审计）' @('change', 'notification', 'audit', 'dev') }
        'ui'         { Test-NpmProject 'ui（Vue 前端）' 'web' @('typecheck', 'test:unit', 'build') }
        'qa'         { Test-NpmProject 'qa（E2E/契约/评测）' 'qa' @('test:contract', 'test:e2e', 'evaluate') }
    }
}

Write-Host ""
if ($script:failCount -gt 0) {
    Write-Host "=== 结果：$($script:failCount) 项失败 ===" -ForegroundColor Red
    exit 1
} else {
    Write-Host "=== 结果：全部通过 ===" -ForegroundColor Green
    exit 0
}
