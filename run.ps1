# 一键启动脚本：自动读取 .vscode/launch.json 环境变量，编译打包并运行后端服务

$launchJsonPath = "$PSScriptRoot\.vscode\launch.json"

# 1. 设置环境变量
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

# 2. 设置默认 JAVA_HOME（如果未设置）
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\Users\Lenovo\.vscode\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64") {
        $env:JAVA_HOME = "C:\Users\Lenovo\.vscode\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64"
    } elseif (Test-Path "C:\Program Files\Java\jdk-21.0.11") {
        $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
    }
}

$backendDir = "$PSScriptRoot\demo_backend"
if (-not (Test-Path $backendDir)) {
    Write-Host "[ERROR] 未找到 demo_backend 目录！" -ForegroundColor Red
    exit 1
}

Set-Location $backendDir

# 3. 清理并编译打包（跳过测试，确保没有旧 class 残留）
Write-Host "[INFO] 正在清理并编译打包（跳过测试）..." -ForegroundColor Cyan
.\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Maven 打包失败，请检查代码！" -ForegroundColor Red
    exit 1
}

# 4. 运行打包好的 JAR
$jarFile = Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notmatch "sources|javadoc" } | Select-Object -First 1
if (-not $jarFile) {
    Write-Host "[ERROR] 未找到生成的 JAR 包！" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] 正在启动 Spring Boot 后端服务（JAR 包: $($jarFile.Name)）..." -ForegroundColor Cyan
java -jar $jarFile.FullName