param(
    [Parameter(Mandatory=$true)][ValidateSet('server','peer')][string]$Role,
    [int]$WebPort = 0,
    [int]$TcpPort = 9001,
    [string]$DataDir = '',
    [string]$TrackerUrl = 'http://127.0.0.1:8080',
    [int]$UploadDelayMs = 0
)
$ErrorActionPreference = 'Stop'
if ($WebPort -eq 0) { $WebPort = if ($Role -eq 'server') { 8080 } else { 8081 } }
$module = if ($Role -eq 'server') { 'server' } else { 'client' }
$jar = Join-Path $PSScriptRoot "$module\target\$module-1.0.0.jar"
if (-not (Test-Path -LiteralPath $jar)) { throw 'Chua co JAR. Chay source/build.ps1 truoc.' }
if (-not $DataDir) { $DataDir = Join-Path $PSScriptRoot "data\$Role-$WebPort" }
$DataDir = [System.IO.Path]::GetFullPath($DataDir)
$temporary = Join-Path $DataDir 'temp'
New-Item -ItemType Directory -Force -Path $temporary | Out-Null
$javaArgs = @("-Djava.io.tmpdir=$temporary", '-jar', $jar, "--server.port=$WebPort")
if ($Role -eq 'server') {
    if (-not (Test-Path -LiteralPath (Join-Path $DataDir 'database.mv.db')) -and -not $env:ADMIN_PASSWORD) {
        throw 'Lan dau: dat bien moi truong ADMIN_PASSWORD (12-72 ky tu) truoc khi chay server.'
    }
    $javaArgs += "--spring.datasource.url=jdbc:h2:file:$DataDir/database"
} else {
    $javaArgs += @("--peer.tcp-port=$TcpPort", "--peer.data=$DataDir", "--peer.tracker=$TrackerUrl", "--peer.upload-delay-ms=$UploadDelayMs")
}
Write-Host "PeerMesh $Role : http://127.0.0.1:$WebPort (Ctrl+C to stop)"
& java @javaArgs
if ($LASTEXITCODE -ne 0) { throw "Java process stopped with code $LASTEXITCODE." }
