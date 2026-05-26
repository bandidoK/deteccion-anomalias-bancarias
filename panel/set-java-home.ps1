$javaHome = 'C:\Program Files\Java\jdk-11.0.30'
if (-Not (Test-Path $javaHome)) {
    Write-Error "No se encontró la ruta de JAVA_HOME: $javaHome"
    return
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME configurado en: $env:JAVA_HOME"
Write-Host "PATH actualizado con: $env:JAVA_HOME\bin" 
