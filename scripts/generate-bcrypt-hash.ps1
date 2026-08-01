$ErrorActionPreference = 'Stop'
$passwordWasPrompted = $false
if ([string]::IsNullOrWhiteSpace($env:ADMIN_PASSWORD)) {
    $firstSecure = Read-Host '请输入本地演示管理员密码（至少12位）' -AsSecureString
    $secondSecure = Read-Host '请再次输入管理员密码' -AsSecureString
    $firstPlain = [System.Net.NetworkCredential]::new('', $firstSecure).Password
    $secondPlain = [System.Net.NetworkCredential]::new('', $secondSecure).Password
    if ($firstPlain.Length -lt 12) {
        throw '管理员密码至少需要12位'
    }
    if ($firstPlain -cne $secondPlain) {
        throw '两次输入的管理员密码不一致'
    }
    $env:ADMIN_PASSWORD = $firstPlain
    $passwordWasPrompted = $true
}

$backendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\demo_backend')).Path
Push-Location -LiteralPath $backendRoot
try {
    & .\mvnw.cmd -q "-Dexec.mainClass=com.tongji.enso.mybatisdemo.admin.auth.AdminPasswordHashTool" `
        "org.codehaus.mojo:exec-maven-plugin:3.1.0:java"
    if ($LASTEXITCODE -ne 0) {
        throw "BCrypt hash generation failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
    if ($passwordWasPrompted) {
        Remove-Item Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
        $firstPlain = $null
        $secondPlain = $null
    }
}
