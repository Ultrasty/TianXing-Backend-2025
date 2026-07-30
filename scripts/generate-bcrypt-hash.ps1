$ErrorActionPreference = 'Stop'
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
}
