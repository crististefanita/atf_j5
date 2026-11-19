#requires -Version 7.0
param(
    [string]$BrowserRoot,
    [Parameter(Mandatory)][string]$WorkRoot,
    [string]$JsonLibrary,
    [switch]$PrepareAchievements,
    [ValidateRange(0,65535)][int]$Port = 8715,
    [string]$ListenAddress = '127.0.0.1',
    [switch]$BuildOnly
)
$root = [IO.Path]::GetFullPath($WorkRoot)
$null = New-Item -ItemType Directory -Force -Path $root
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourcePrefix = $checkout.TrimEnd([char[]]@('/', '\')) + [IO.Path]::DirectorySeparatorChar
if (($root.Equals($checkout, [StringComparison]::OrdinalIgnoreCase)) -or
        ($root.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase))) {
    throw 'Use an external browser work directory.'
}
if (!$JsonLibrary) {
    $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $jsonCache = Join-Path $gradleHome 'caches/modules-2/files-2.1/org.json/json/20240205'
    if (Test-Path -LiteralPath $jsonCache) {
        $JsonLibrary = Get-ChildItem -LiteralPath $jsonCache -Filter 'json-20240205.jar' -Recurse -File |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$JsonLibrary) { throw 'Supply -JsonLibrary with org.json:json:20240205, the Android JVM dependency.' }
}
$JsonLibrary = (Resolve-Path -LiteralPath $JsonLibrary).ProviderPath
$sources = @(
    Join-Path $PSScriptRoot 'src/main/java'
    Join-Path $checkout 'rentz-lab-app/game-core/src/main/java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/GameSession.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementHand.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementTactics.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementCatalog.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementExpansion.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementFullCatalog.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementRateFloor.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementProgress.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/GameplayAchievementCatalog.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/profile/LocalProfile.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/profile/DisplayName.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/table/RentzCardSpace.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/table/RentzTableContent.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/collection/UiLists.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/copy/RentzText.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/score/RentzScoreContent.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/score/RentzScoreLayout.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/chrome/RentzGameMenu.java'
)
$gradleOutputRoot = Join-Path $root 'gradle-output'
$generatedAchievementCatalog = Join-Path $gradleOutputRoot 'app/generated/sources/rentzAchievements/java/ro/rentzlab/app/achievements/GameplayAchievementCatalogData.java'
if ($PrepareAchievements -or !(Test-Path -LiteralPath $generatedAchievementCatalog)) {
    $appRoot = Join-Path $checkout 'rentz-lab-app'
    $javaTemp = Join-Path $root 'java-temp'
    $null = New-Item -ItemType Directory -Force -Path $javaTemp, $gradleOutputRoot
    $initScript = Join-Path $root 'rentz-browser-gradle-init.gradle'
    @'
allprojects { project ->
    def base = new File(System.getenv('RENTZ_BROWSER_BUILD_ROOT'))
    def child = project.path == ':' ? '_root' : project.path.substring(1).replace(':', File.separator)
    project.buildDir = new File(base, child)
}
'@ | Set-Content -LiteralPath $initScript -Encoding utf8
    $previousJavaOptions = $env:JAVA_TOOL_OPTIONS
    $previousBuildRoot = $env:RENTZ_BROWSER_BUILD_ROOT
    try {
        $env:RENTZ_BROWSER_BUILD_ROOT = $gradleOutputRoot
        $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
        $catalogLog = Join-Path $root 'rentz-achievement-catalog.log'
        & (Join-Path $appRoot 'gradlew.bat') -p $appRoot --offline --no-daemon -I $initScript ':app:generateRentzAchievementCatalog' *> $catalogLog
        if ($LASTEXITCODE -ne 0) { throw 'Could not generate the source-backed Rentz achievement catalogue.' }
    } finally {
        $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
        $env:RENTZ_BROWSER_BUILD_ROOT = $previousBuildRoot
    }
}
if (!(Test-Path -LiteralPath $generatedAchievementCatalog)) { throw 'Generated Rentz achievement catalogue is missing.' }
$sources += $generatedAchievementCatalog
$main = 'ro.rentzlab.browser.RentzBrowser'
if ($BrowserRoot) {
    & (Join-Path $BrowserRoot 'Start-CardTableBrowser.ps1') -WorkRoot $WorkRoot -GameRoot $checkout `
        -JavaInputs $sources -Libraries @($JsonLibrary) -MainClass $main -Port $Port -ListenAddress $ListenAddress -BuildOnly:$BuildOnly
    return
}
$library = & (Join-Path $PSScriptRoot 'Verify-CardTableBrowser.ps1') -Version '0.2.21' `
    -SourceCommit 'a04aa2dce56b2c65b011df84200882b31b9caa22'
$build = Join-Path $root ('browser-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$javaTemp = Join-Path $root 'java-temp'
$null = New-Item -ItemType Directory -Force -Path $classes, $javaTemp
$sourceFiles = foreach ($inputPath in $sources) {
    $item = Get-Item -LiteralPath $inputPath
    if ($item.PSIsContainer) {
        Get-ChildItem -LiteralPath $item.FullName -Recurse -Filter '*.java' -File | ForEach-Object FullName
    } else { $item.FullName }
}
$quote = { param([string]$value) '"' + $value.Replace('\', '/') + '"' }
$argFile = Join-Path $build 'javac.args'
$javacArgs = @('--release', '17', '-encoding', 'UTF-8', '-d', (& $quote $classes), '-cp', (& $quote (@($library, $JsonLibrary) -join [IO.Path]::PathSeparator)))
$javacArgs += $sourceFiles | Sort-Object -Unique | ForEach-Object { & $quote $_ }
[IO.File]::WriteAllLines($argFile, [string[]]$javacArgs, [Text.UTF8Encoding]::new($false))
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
    & javac "@$argFile"
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
if ($LASTEXITCODE -ne 0) { throw 'Browser adapter compilation failed.' }
$runtime = @($classes, $library, $JsonLibrary) -join [IO.Path]::PathSeparator
if ($BuildOnly) { $runtime; return }
try {
    $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
    & java -cp $runtime $main '-' $ListenAddress $Port
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
if ($LASTEXITCODE -ne 0) { throw 'Browser server stopped with an error.' }
