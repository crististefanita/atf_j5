#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$WorkRoot,
    [string]$JsonLibrary
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($WorkRoot)
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourcePrefix = $checkout.TrimEnd([char[]]@('/', '\')) + [IO.Path]::DirectorySeparatorChar
if (($root.Equals($checkout, [StringComparison]::OrdinalIgnoreCase)) -or
        ($root.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase))) {
    throw 'Use an external build directory; generated Pages files do not belong in the source tree.'
}
$null = New-Item -ItemType Directory -Force -Path $root
$previousBrowserAudit = $env:RENTZ_BROWSER_AUDIT
$env:RENTZ_BROWSER_AUDIT = Join-Path $root 'component-comparisons'
try {
$jvm = & (Join-Path $PSScriptRoot 'Start-RentzBrowser.ps1') -WorkRoot $root -JsonLibrary $JsonLibrary -BuildOnly
if ($LASTEXITCODE -ne 0 -or !$jvm) { throw 'Rentz JVM sources did not compile.' }
$classpath = $jvm -join ''
$run = Split-Path ($classpath.Split([IO.Path]::PathSeparator)[0])
$library = Join-Path $PSScriptRoot 'lib/atf-ui-browser-0.2.22.jar'
$commonTests = Join-Path $PSScriptRoot 'lib/atf-ui-browser-0.2.22-tests.jar'
$commonTestsExpected = (Get-Content -LiteralPath "$commonTests.sha256" -Raw).Trim()
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $commonTests).Hash -ine $commonTestsExpected) {
    throw 'Shared browser component test checksum mismatch.'
}
$pages = Join-Path $PSScriptRoot 'pages'
$dependencySpec = Join-Path $pages 'teavm-dependencies.xml'
$toolClasspath = Join-Path $root 'teavm.classpath'
$toolClasspathHash = Join-Path $root 'teavm-dependencies.sha256'
$compilerLog = Join-Path $root 'teavm-dependencies.log'
$dependencyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dependencySpec).Hash
$resolvedHash = if (Test-Path -LiteralPath $toolClasspathHash) {
    (Get-Content -LiteralPath $toolClasspathHash -Raw).Trim()
} else { '' }
if (!(Test-Path -LiteralPath $toolClasspath) -or $resolvedHash -ine $dependencyHash) {
    & mvn -B -ntp -f $dependencySpec `
        'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath' "-Dmdep.outputFile=$toolClasspath" *> $compilerLog
    if ($LASTEXITCODE -ne 0) { throw "Could not resolve TeaVM. See $compilerLog" }
    [IO.File]::WriteAllText($toolClasspathHash, $dependencyHash, [Text.UTF8Encoding]::new($false))
}
$classpath = @($classpath, (Get-Content -LiteralPath $toolClasspath -Raw).Trim()) -join [IO.Path]::PathSeparator
$classes = Join-Path $run 'rentz-pages-classes'
$site = Join-Path $run 'rentz-pages-site'
$javaTemp = Join-Path $root 'java-temp'
$null = New-Item -ItemType Directory -Force -Path $classes, $site, $javaTemp
$sources = Get-ChildItem -LiteralPath (Join-Path $pages 'src/main/java') -Filter '*.java' -File -Recurse |
    ForEach-Object FullName
& javac --release 17 -encoding UTF-8 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Rentz Pages adapter compilation failed.' }
$componentTests = @(
    (Join-Path $pages 'src/test/java/ro/cardtable/browser/RentzPageComponentTest.java'),
    (Join-Path $pages 'src/test/java/ro/cardtable/names/PlayerNamesParityTest.java'))
$testClasspath = @($classes, $classpath) -join [IO.Path]::PathSeparator
& javac --release 17 -encoding UTF-8 -cp $testClasspath -d $classes @componentTests
if ($LASTEXITCODE -ne 0) { throw 'Rentz Pages component test compilation failed.' }
& java '-Xmx256m' '-XX:ActiveProcessorCount=2' -cp $testClasspath 'ro.cardtable.browser.RentzPageComponentTest'
if ($LASTEXITCODE -ne 0) { throw 'Rentz Pages component checks failed.' }
$nativeNames = Join-Path $checkout 'rentz-lab-app/ui-library/ro/cardtable/card-table-names/0.1.0/card-table-names-0.1.0.jar'
$nativeClasses = $classpath.Split([IO.Path]::PathSeparator)[0]
& java '-Xmx256m' '-XX:ActiveProcessorCount=2' "-Drentz.names.nativeJar=$nativeNames" "-Drentz.native.appClasses=$nativeClasses" -cp $testClasspath 'ro.cardtable.names.PlayerNamesParityTest'
if ($LASTEXITCODE -ne 0) { throw 'WEB PlayerNames shim differs from the APK names contract.' }
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
    $compilerLog = Join-Path $run 'teavm.log'
    & java '-XX:ActiveProcessorCount=3' '-Xmx1g' -cp (@($classes, $classpath) -join [IO.Path]::PathSeparator) `
        (Join-Path $pages 'tools/src/main/java/ro/rentzlab/browser/compiler/CompileRentzPage.java') $site *> $compilerLog
} finally { $env:JAVA_TOOL_OPTIONS = $previousJavaOptions }
if ($LASTEXITCODE -ne 0) { throw "Rentz browser engine compilation failed. See $compilerLog" }

$commonRoot = Join-Path $run 'common-web'
$null = New-Item -ItemType Directory -Force -Path $commonRoot
Push-Location $commonRoot
try { & jar xf $library 'ro/cardtable/browser/web' }
finally { Pop-Location }
$commonTestRoot = Join-Path $run 'common-component-tests'
$null = New-Item -ItemType Directory -Force -Path $commonTestRoot
Push-Location $commonTestRoot
try { & jar xf $commonTests 'hand-presentation.mjs' 'viewport-contract.mjs' 'menu-presentation.mjs' 'score-presentation.mjs' 'seat-presentation.mjs' 'card-typography.mjs' 'card-typography.tsv' }
finally { Pop-Location }
$commonWeb = Join-Path $commonRoot 'ro/cardtable/browser/web'
Get-ChildItem -LiteralPath $commonWeb -File | Copy-Item -Destination $site
Get-ChildItem -LiteralPath (Join-Path $pages 'web') -File | Copy-Item -Destination $site
foreach ($contract in @('hand-presentation.mjs', 'viewport-contract.mjs', 'menu-presentation.mjs', 'score-presentation.mjs', 'seat-presentation.mjs', 'card-typography.mjs', 'card-typography.tsv')) {
    if (!(Test-Path -LiteralPath (Join-Path $commonTestRoot $contract))) {
        throw "The shared Card Table Browser component contract is missing: $contract"
    }
}
& node (Join-Path $commonTestRoot 'viewport-contract.mjs') (Join-Path $commonRoot 'ro/cardtable/browser/web/viewport.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Shared browser viewport component checks failed.' }

$tableScript = Join-Path $site 'table.mjs'
$markup = [IO.File]::ReadAllText($tableScript).Replace("`r`n", "`n")
$importAnchor = 'import { historyGrid } from "./records.mjs";'
$renderAnchor = '  const visible = element("div", "", "cards-on-table");' + "`n" + '  if (!match.header) for (const face of match.center) visible.append(card(face));'
$contentAnchor = '  const content = element("section", "", match.header ? "match whist-match" : "match");'
$centerAnchor = '  const center = element("div", "", "center");'
$automationAnchor = '    input.checked = automation.selected();'
$rentzHeaderAnchor = '  const title = element("h1", match.header.title);' + "`n" + '  top.append(navigation, title);'
$rentzDetailsAnchor = '  details.append(status, trump);'
$issueAnchor = '    state = result;' + "`n" + '    transport.remember?.(state.profile);'
$choiceAnchor = @(
  '  for (const move of match.actions.filter(move => !move.key.startsWith("bid:") && !(match.header && ["auto", "auto-step"].includes(move.key)))) {'
  '    actions.append(control(move));'
  '  }'
) -join "`n"
foreach ($anchor in @($importAnchor, $renderAnchor, $contentAnchor, $centerAnchor, $automationAnchor, $rentzHeaderAnchor, $rentzDetailsAnchor, $issueAnchor, $choiceAnchor)) {
    $count = ([regex]::Matches($markup, [regex]::Escape($anchor))).Count
    if ($count -ne 1) { throw "Card Table Browser 0.2.22 adapter anchor changed; expected one match, found $count." }
}
$markup = $markup.Replace($importAnchor, $importAnchor + "`n" + 'import { presentRentzArrival, presentRentzPlayback, rentzCenterInsets, rentzChoiceDropdown, rentzSequence, refreshRentzSequence, rentzMenuRoutes } from "./rentz-ui.mjs";' + "`n" + 'import { rentzHistoryGrid, rentzScoreTable, rentzScoreRules } from "./rentz-score.mjs";')
$preparedLayoutAnchor = '    await placeTable(content);'
if (([regex]::Matches($markup, [regex]::Escape($preparedLayoutAnchor))).Count -ne 1) {
    throw 'The shared prepared-table layout anchor changed; sequence measurement cannot be bound safely.'
}
$markup = $markup.Replace($preparedLayoutAnchor, $preparedLayoutAnchor + "`n" +
    '    refreshRentzSequence(content.querySelector(".rentz-sequence"));')
$scoreAnchor = 'scoreGrid(match.score.table, true)'
$historyAnchor = 'historyGrid(data.grid, element)'
$layoutAnchor = 'width, height, mode: state.view.header?.mode ?? "", seatHeight:'
foreach ($anchor in @($scoreAnchor, $historyAnchor, $layoutAnchor)) {
    if (([regex]::Matches($markup, [regex]::Escape($anchor))).Count -ne 1) {
        throw 'The shared score renderer anchor changed; Rentz P/T adaptation cannot be applied safely.'
    }
}
$markup = $markup.Replace($scoreAnchor, '(match.score.table.grid ? rentzScoreTable(match.score.table, element, scoreGrid) : scoreGrid(match.score.table, true))')
$menuRoutesAnchor = '  for (const route of routes) {'
if (([regex]::Matches($markup, [regex]::Escape($menuRoutesAnchor))).Count -ne 1) {
    throw 'The shared menu renderer changed; native Rentz route availability cannot be bound safely.'
}
$markup = $markup.Replace($menuRoutesAnchor, '  for (const route of rentzMenuRoutes(routes, match)) {')
$awardsAnchor = '  if (details.awards) page.append(element("p", details.awards, "whist-score-awards"));'
$rulesAnchor = 'profileDialogs.message(copy("scoringDetails", "Calculul punctajului"), details.rules, "game-score-rules")'
foreach ($anchor in @($awardsAnchor, $rulesAnchor)) {
    if (([regex]::Matches($markup, [regex]::Escape($anchor))).Count -ne 1) {
        throw 'The shared settlement details anchor changed; quiet score adaptation cannot be applied safely.'
    }
}
$markup = $markup.Replace($awardsAnchor, '')
$markup = $markup.Replace($rulesAnchor, 'profileDialogs.message(copy("scoringDetails", "Calculul punctajului"), rentzScoreRules(details), "game-score-rules")')
$markup = $markup.Replace($historyAnchor, 'rentzHistoryGrid(data.grid, element, historyGrid)')
$markup = $markup.Replace($layoutAnchor, 'width, height, rentzSequence: !!board.querySelector(".rentz-sequence"), mode: state.view.header?.mode ?? "", seatHeight:')
$viewportAnchor = '  if (width <= 0 || height <= 0) return;'
if (([regex]::Matches($markup, [regex]::Escape($viewportAnchor))).Count -ne 1) {
    throw 'The shared viewport resize anchor changed; visible-height adaptation cannot be applied safely.'
}
$markup = $markup.Replace($viewportAnchor, $viewportAnchor + "`n" +
    '  viewport.style.minHeight = (visual?.height ?? innerHeight) + "px";')
$markup = $markup.Replace($rentzHeaderAnchor,
  '  const title = element("h1", match.header.title);' + "`n" +
  '  title.hidden = match.header.mode === "RENTZ";' + "`n" +
  '  top.append(navigation, title);')
$markup = $markup.Replace($rentzDetailsAnchor,
  '  if (match.header.mode === "RENTZ") {' + "`n" +
  '    top.classList.add("rentz-title-row"); status.classList.add("rentz-title-lines"); top.insertBefore(status, title); details.hidden = true;' + "`n" +
  '  }' + "`n" +
  '  else details.append(status, trump);')
$rentzRender = $renderAnchor + "`n" + (@(
  '  if (match.rentzSequence?.length) {'
  '    visible.dataset.rentzSequence = "true";'
  '    visible.append(rentzSequence(match.rentzSequence, element, language, match.rentzArrival));'
  '  }'
) -join "`n")
$markup = $markup.Replace($renderAnchor, $rentzRender)
$markup = $markup.Replace($contentAnchor, $contentAnchor + "`n" +
  '  if (match.rentzArrival) content.dataset.rentzArrival = "true";' + "`n" +
  '  if (match.rentzSequence?.length) content.dataset.rentzSequence = "true";')
$rentzCenterRender = $centerAnchor + "`n" + (@(
  '  if (match.header?.mode === "RENTZ") {'
  '    const insets = rentzCenterInsets(match.seats.length);'
  '    center.style.setProperty("--rentz-center-top", `${insets.top}px`);'
  '    center.style.setProperty("--rentz-center-bottom", `${insets.bottom}px`);'
  '  }'
) -join "`n")
$markup = $markup.Replace($centerAnchor, $rentzCenterRender)
$markup = $markup.Replace($automationAnchor, $automationAnchor + "`n" + '    input.disabled = !!match.rentzArrival;')
$rentzIssue = @(
  '    await presentRentzPlayback(result, {'
  '      autoStepAction: state.view?.actions?.find(move => move.key === "auto-step"),'
  '      render: next => { state = next; return render(); },'
  '      nextFrame: () => new Promise(resolve => requestAnimationFrame(resolve)),'
  '      wait: duration => new Promise(resolve => setTimeout(resolve, duration)),'
  '    });'
  '    const arrivalPresented = await presentRentzArrival(result, language, {'
  '      render: next => { state = next; return render(); },'
  '      wait: duration => new Promise(resolve => setTimeout(resolve, duration)),'
  '      autoStepAction: state.view?.actions?.find(move => move.key === "auto-step"),'
  '      stopAutomation: () => automation.set(false),'
  '    });'
  '    state = result;'
  '    transport.remember?.(state.profile);'
) -join "`n"
$markup = $markup.Replace($issueAnchor, $rentzIssue)
$rentzChoiceRender = @(
  '  const rentzChoices = match.actions.filter(move => move.key.startsWith("choose:") || move.key.startsWith("blind:"));'
  '  const rentzPicker = match.header ? rentzChoiceDropdown(rentzChoices, element, action, language) : null;'
  '  if (rentzPicker) actions.append(rentzPicker);'
  '  for (const move of match.actions.filter(move => !move.key.startsWith("bid:") && !(match.header && ["auto", "auto-step"].includes(move.key))'
  '      && !move.key.startsWith("choose:") && !move.key.startsWith("blind:"))) {'
  '    actions.append(control(move));'
  '  }'
) -join "`n"
$markup = $markup.Replace($choiceAnchor, $rentzChoiceRender)
$renderBranch = '    else await render();'
if (([regex]::Matches($markup, [regex]::Escape($renderBranch))).Count -ne 1) {
    throw 'Card Table Browser render completion anchor changed; expected one match.'
}
$markup = $markup.Replace($renderBranch, '    else if (!arrivalPresented) await render();')
[IO.File]::WriteAllText($tableScript, $markup, [Text.UTF8Encoding]::new($false))

$index = Join-Path $site 'index.html'
$html = [IO.File]::ReadAllText($index)
$tableStylePattern = '<link rel="stylesheet" href="\./table\.css(?:\?[^\"]*)?">'
$tableStyleMatches = [regex]::Matches($html, $tableStylePattern)
if ($tableStyleMatches.Count -ne 1) {
    throw "Card Table Browser page stylesheet anchor changed; expected one table.css link, found $($tableStyleMatches.Count)."
}
$rentzStyleLink = '<link rel="stylesheet" href="./rentz.css">'
if ($html.Contains($rentzStyleLink)) { throw 'Rentz stylesheet is already linked in the common page template.' }
$html = [regex]::Replace($html, $tableStylePattern, [Text.RegularExpressions.MatchEvaluator]{
    param($match)
    return $match.Value + "`n  " + $rentzStyleLink
})
$html = $html.Replace('<html lang="ro">', '<html lang="ro" data-engine="whist" data-game="rentz">')
$html = $html.Replace('<title>Card Table</title>', '<title>Rentz Lab</title>')
$policy = "default-src 'self'; connect-src 'none'; style-src 'self' 'unsafe-inline'; worker-src 'self'; base-uri 'none'"
$html = $html.Replace('<meta charset="utf-8">', '<meta charset="utf-8">' + "`n" + "  <meta http-equiv=`"Content-Security-Policy`" content=`"$policy`">")
$rentzStyleLinks = ([regex]::Matches($html, '<link rel="stylesheet" href="\./rentz\.css">')).Count
if ($rentzStyleLinks -ne 1) { throw "Rentz page must load its adapter stylesheet exactly once, found $rentzStyleLinks." }
[IO.File]::WriteAllText($index, $html, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $site '.nojekyll'), '', [Text.UTF8Encoding]::new($false))

foreach ($required in @('index.html', 'rentz-engine.mjs', 'engine-worker.mjs', 'local-transport.mjs', 'rentz-ui.mjs', 'rentz-score.mjs', 'rentz.css', 'table.mjs', 'hand-feedback.mjs', 'card-suits.woff2', 'card-suits-OFL.txt')) {
    if (!(Test-Path -LiteralPath (Join-Path $site $required))) { throw "Rentz Pages output is missing $required" }
}
foreach ($script in @('rentz-ui.mjs', 'table.mjs', 'engine-worker.mjs', 'local-transport.mjs')) {
    & node --check (Join-Path $site $script)
    if ($LASTEXITCODE -ne 0) { throw "Generated browser script is invalid: $script" }
}
& node (Join-Path $pages 'verify-trick-playback.mjs') $tableScript
if ($LASTEXITCODE -ne 0) { throw 'Shared trick playback component checks failed.' }
& node (Join-Path $pages 'verify-trick-contracts.mjs') $site
if ($LASTEXITCODE -ne 0) { throw 'Complete trick-contract presentation checks failed.' }
& node (Join-Path $pages 'verify-rentz-engine.mjs') (Join-Path $site 'rentz-engine.mjs') `
    (Join-Path $commonTestRoot 'hand-presentation.mjs') (Join-Path $site 'hand-feedback.mjs') `
    (Join-Path $site 'rentz-ui.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Generated Rentz engine component checks failed.' }
$nativeUiAar = Join-Path $checkout 'rentz-lab-app/ui-library/ro/cardtable/card-table-ui/0.1.21/card-table-ui-0.1.21.aar'
$nativeUiProvenance = Get-Content -LiteralPath (Join-Path $checkout 'rentz-lab-app/ui-library/provenance.json') -Raw | ConvertFrom-Json
$nativeUiPin = @($nativeUiProvenance.files | Where-Object { $_.path -eq 'ro/cardtable/card-table-ui/0.1.21/card-table-ui-0.1.21.aar' })
if ($nativeUiPin.Count -ne 1 -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $nativeUiAar).Hash -ine $nativeUiPin[0].sha256) {
    throw 'The native Rentz layout reference does not match the pinned Android UI AAR.'
}
$nativeUiClasses = Join-Path $run 'card-table-ui-0.1.21-classes.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($nativeUiAar)
try {
    $entry = $archive.GetEntry('classes.jar')
    if (!$entry) { throw 'The pinned Android UI AAR has no classes.jar.' }
    $input = $entry.Open()
    $output = [IO.File]::Create($nativeUiClasses)
    try { $input.CopyTo($output) }
    finally { $output.Dispose(); $input.Dispose() }
} finally { $archive.Dispose() }
$nativeReferenceClasses = Join-Path $run 'rentz-native-layout-reference'
$null = New-Item -ItemType Directory -Force -Path $nativeReferenceClasses
$nativeCardSpace = Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/table/RentzCardSpace.java'
$nativeReferenceSource = Join-Path $PSScriptRoot 'tests/ro/rentzlab/app/ui/table/RentzNativeSequenceMetricsReference.java'
$nativePresentationSource = Join-Path $PSScriptRoot 'tests/ro/rentzlab/app/ui/table/RentzNativePresentationReference.java'
$nativeReferenceClasspath = @($classpath, $nativeUiClasses) -join [IO.Path]::PathSeparator
& javac --release 17 -encoding UTF-8 -cp $nativeReferenceClasspath -d $nativeReferenceClasses $nativeCardSpace $nativeReferenceSource $nativePresentationSource
if ($LASTEXITCODE -ne 0) { throw 'Could not compile the native Rentz sequence layout reference.' }
$nativeMetrics = Join-Path $run 'rentz-native-sequence-metrics.tsv'
$nativeReferenceRuntime = @($nativeReferenceClasses, $nativeUiClasses) -join [IO.Path]::PathSeparator
& java -cp $nativeReferenceRuntime 'ro.rentzlab.app.ui.table.RentzNativeSequenceMetricsReference' | Set-Content -LiteralPath $nativeMetrics -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Could not emit native Rentz sequence layout metrics.' }
$nativePresentation = Join-Path $run 'rentz-native-presentation.json'
& java -cp (@($nativeReferenceRuntime, $classpath) -join [IO.Path]::PathSeparator) `
    'ro.rentzlab.app.ui.table.RentzNativePresentationReference' | Set-Content -LiteralPath $nativePresentation -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Could not read the pinned Android UI presentation reference.' }
& node (Join-Path $pages 'verify-shared-ui.mjs') $site $commonTestRoot $nativePresentation
if ($LASTEXITCODE -ne 0) { throw 'Shared native/browser UI adoption checks failed.' }
& node (Join-Path $pages 'verify-shared-automation.mjs') $site
if ($LASTEXITCODE -ne 0) { throw 'Shared AutoPlay component checks failed.' }
& node (Join-Path $pages 'verify-rentz-ui.mjs') $nativeMetrics $tableScript `
    (Join-Path $site 'table.css') (Join-Path $site 'rentz.css') (Join-Path $site 'index.html')
if ($LASTEXITCODE -ne 0) { throw 'Rentz sequence renderer component verification failed.' }
& node (Join-Path $pages 'verify-rentz-transport.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Rentz transient event transport verification failed.' }
& node (Join-Path $pages 'package-assets.mjs') $site
if ($LASTEXITCODE -ne 0) { throw 'Browser asset graph versioning failed.' }
& node (Join-Path $pages 'verify-pages-assets.mjs') $site
if ($LASTEXITCODE -ne 0) { throw 'Published browser asset graph verification failed.' }
& node (Join-Path $pages 'verify-rentz-ui.mjs') $nativeMetrics $tableScript `
    (Join-Path $site 'table.css') (Join-Path $site 'rentz.css') (Join-Path $site 'index.html')
if ($LASTEXITCODE -ne 0) { throw 'Final packaged browser renderer verification failed.' }
$site
} finally { $env:RENTZ_BROWSER_AUDIT = $previousBrowserAudit }
