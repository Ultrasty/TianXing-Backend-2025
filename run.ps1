# 一键启动脚本：自动读取 .vscode/launch.json 环境变量并运行后端服务

$launchJsonPath = "$PSScriptRoot\.vscode\launch.json"

if (Test-Path $launchJsonPath) {
    Write-Host "[INFO] 正在读取 .vscode/launch.json 配置的环境变量..." -ForegroundColor Green
    try {
        $jsonContent = Get-Content $launchJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $config = $jsonContent.configurations | Where-Object { $_.env -ne $null } | Select-Object -First 1
        if ($config -and $config.env) {
            foreach ($prop in $config.env.PSObject.Properties) {
                [System.Environment]::SetEnvironmentVariable($prop.Name, $prop.Value, "Process")
                Write-Host "  -> 设置环境变量: $($prop.Name)" -ForegroundColor Gray
            }
        }
    } catch {
        Write-Host "[WARN] 解析 .vscode/launch.json 失败: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "[INFO] 未找到 .vscode/launch.json，将使用全局/系统默认环境变量启动。" -ForegroundColor Yellow
}



# 设置默认 JAVA_HOME (如果当前环境没有设置)
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\Users\Lenovo\.vscode\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64") {
        $env:JAVA_HOME = "C:\Users\Lenovo\.vscode\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64"
    } elseif (Test-Path "C:\Program Files\Java\jdk-21.0.11") {
        $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
    }
}
$backendDir = "$PSScriptRoot\demo_backend"
if (Test-Path $backendDir) {
    Set-Location $backendDir
    Write-Host "[INFO] 正在启动 Spring Boot 后端服务..." -ForegroundColor Cyan
    .\mvnw.cmd spring-boot:run
} else {
    Write-Host "[ERROR] 未找到 demo_backend 目录！" -ForegroundColor Red
}
