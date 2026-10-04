param([ValidateSet('build', 'test', 'clienttest', 'run')][string]$Task = 'build')
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Users\Guail\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$javaExecutable = Join-Path $env:JAVA_HOME 'bin\java.exe'
$javacExecutable = Join-Path $env:JAVA_HOME 'bin\javac.exe'
if (!(Test-Path -LiteralPath $javaExecutable) -or !(Test-Path -LiteralPath $javacExecutable)) { throw 'JDK 25 未找到，请按 AGENTS.md 检查 JAVA_HOME。' }
& $javaExecutable -version
& $javacExecutable -version
if ((& $javacExecutable -version 2>&1 | Out-String) -notmatch 'javac 25\.') { throw 'Minecraft 26.3 需要 JDK 25。' }
$gradleTask = @{ build = 'build'; test = 'runGametest'; clienttest = 'runClienttest'; run = 'runClient' }[$Task]
Push-Location $PSScriptRoot
try { & .\gradlew.bat $gradleTask --console=plain; if ($LASTEXITCODE -ne 0) { throw "Gradle 执行失败：$LASTEXITCODE" } }
finally { Pop-Location }
