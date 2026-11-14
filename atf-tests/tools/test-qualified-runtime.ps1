param([Parameter(Mandatory)][string]$RunRoot,[string]$NativePublicationScript='')
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
if(Test-Path -LiteralPath $RunRoot){throw 'Fresh runtime-control output required'}
New-Item -ItemType Directory $RunRoot|Out-Null
$fixture=New-AtfWorkspace -Kind fixture -ProjectRoot $project
$product=Join-Path (Get-AtfDataRoot -ProjectRoot $project) ('components/runtime-fixtures-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $product|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Require([bool]$Value,[string]$Reason){if(-not$Value){throw $Reason}}
function Case([string]$Name,[scriptblock]$Body){try{& $Body;$results.Add([pscustomobject]@{name=$Name;status='PASS'})}catch{$results.Add([pscustomobject]@{name=$Name;status='FAIL';error=$_.Exception.Message})}}
function Hash([string]$File){return (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash.ToLowerInvariant()}
function Json([string]$File,$Value){ConvertTo-Json -InputObject $Value -Depth 12|Set-Content -LiteralPath $File -Encoding utf8NoBOM}
function Context([string]$Name){
    $root=Join-Path $fixture $Name
    $stage=Join-Path $root 'snapshot';$component=Join-Path $root 'component';$classes=Join-Path $stage 'atf-tests/target/classes'
    New-Item -ItemType Directory "$classes/atf/rentz","$classes/model",$component -Force|Out-Null
    [IO.File]::WriteAllText("$classes/atf/rentz/OfflineArena.class",'main-fixture-v1')
    [IO.File]::WriteAllText("$classes/model/Helper.class",'helper-fixture-v1')
    [IO.File]::WriteAllText("$classes/model/rules.json",'resource-fixture-v1')
    [IO.File]::WriteAllText("$root/dependency.jar",'jar-fixture-v1')
    $token=[guid]::NewGuid().ToString('N')
    $files=@(foreach($entryName in @('atf/rentz/OfflineArena.class','model/Helper.class','model/rules.json')){[ordered]@{path=$entryName;sha256=(Hash (Join-Path $classes $entryName))}})
    $inventory=Join-Path $component 'classpath-inventory.json'
    Json $inventory ([ordered]@{schemaVersion=1;entries=@([ordered]@{path=$classes;kind='directory';files=$files},[ordered]@{path="$root/dependency.jar";kind='file';files=@([ordered]@{path='';sha256=(Hash "$root/dependency.jar")})})})
    $attestation=Join-Path $component 'runtime-attestation.json'
    Json $attestation ([ordered]@{schemaVersion=1;status='PASS';token=$token;inventorySha256=(Hash $inventory);classpath=($classes+[IO.Path]::PathSeparator+"$root/dependency.jar")})
    $descriptor=Join-Path $component 'arena-runtime.json'
    $entryClass=Join-Path $stage 'atf-tests/target/classes/atf/rentz/OfflineArena.class'
    $repo=Join-Path (Get-AtfInfrastructurePath -Area cache -ProjectRoot $project) ('runtime-fixture-'+[guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory $repo|Out-Null
    Json $descriptor ([ordered]@{mainClass='atf.rentz.OfflineArena';classpath=($classes+[IO.Path]::PathSeparator+"$root/dependency.jar");classFile=$entryClass;classSha256=(Hash "$classes/atf/rentz/OfflineArena.class");inventory=$inventory;inventorySha256=(Hash $inventory);attestation=$attestation;attestationSha256=(Hash $attestation);qualificationToken=$token})
    Json "$component/result.json" ([ordered]@{status='PASS';stage=$stage;arenaRuntime=$descriptor;arenaRuntimeSha256=(Hash $descriptor);repository=$repo})
    '[]'|Set-Content "$component/source-identity.json"
    Json "$root/protocol.json" ([ordered]@{id='runtime-fixture';phase='DEVELOPMENT';players=3;seeds=@(31);opponents=@('NORMAL');candidate='HARD';worlds=1;retainPerFamily=0})
    $run=Join-Path $product $Name;$spy=Join-Path $root 'JavaSpy.cmd'
    @('@echo off',('echo invoked > "'+$root+'\invoked.txt"'),('mkdir "'+$run+'"'),'exit /b 0')|Set-Content $spy -Encoding ascii
    return [pscustomobject]@{root=$root;stage=$stage;classes=$classes;component=$component;inventory=$inventory;attestation=$attestation;descriptor=$descriptor;run=$run;spy=$spy}
}
function Launch($c, [string]$Registry){
    $failed=$false
    $registryOption=@{}
    if($Registry){$registryOption.Registry=$Registry}
    try{& (Join-Path $project 'atf-tests/tools/run-arena.ps1') -ComponentRun $c.component -Protocol "$($c.root)/protocol.json" -RunRoot $c.run -Java $c.spy @registryOption *> "$($c.root)/launcher.log"}catch{$failed=$true;$_.Exception.Message|Add-Content "$($c.root)/launcher.log"}
    return [pscustomobject]@{failed=$failed;invoked=(Test-Path -LiteralPath "$($c.root)/invoked.txt")}
}
function FailedRagContext([string]$Name){
    $c=Context $Name
    $c.component=Join-Path $product ($Name+'-component')
    New-Item -ItemType Directory -Path $c.component|Out-Null
    $entry=Join-Path $c.classes 'atf/rentz/distribution/ReleaseReadinessRag.class'
    New-Item -ItemType Directory -Path (Split-Path -Parent $entry) -Force|Out-Null
    [IO.File]::WriteAllText($entry,'fixture RAG class')
    $gameCore=Join-Path $c.stage 'rentz-lab-app/game-core/target/classes'
    New-Item -ItemType Directory -Path $gameCore -Force|Out-Null
    $rows=@(
        [ordered]@{module='rentz-lab-app/game-core';name='sample.CoreTest'}
        [ordered]@{module='atf-tests';name='sample.FirstTest'}
        [ordered]@{module='atf-tests';name='sample.SecondTest'}
    )
    for($i=0;$i-lt$rows.Count;$i++){
        $row=$rows[$i]
        $directory=Join-Path $c.component ($row.module.Replace('/','-'))
        New-Item -ItemType Directory -Path $directory -Force|Out-Null
        $path=if($i-eq0){$gameCore}else{$c.classes}
        if($i-eq2-and$Name-ceq'rag-disagreement'){$path+=[IO.Path]::PathSeparator+"$($c.root)/dependency.jar"}
        if($i-gt0){$path+=[IO.Path]::PathSeparator}
        $escaped=[Security.SecurityElement]::Escape($path)
        $content='<testsuite><properties><property name="java.class.path" value="'+$escaped+'"/></properties></testsuite>'
        [IO.File]::WriteAllText((Join-Path $directory ('TEST-'+$row.name+'.xml')),$content)
    }
    if($Name-ceq'rag-no-atf'){$rows=@($rows[0])}
    Json "$($c.component)/result.json" ([ordered]@{status='FAIL';collection=@{name='regression'};source=$project;stage=$c.stage;results=$rows})
    @('@echo off',('echo invoked > "'+$c.root+'\invoked.txt"'),
        ('echo %RENTZ_DATA_ROOT% > "'+$c.root+'\data-root-argument.txt"'),
        ('if not "%~3"=="'+$c.classes+'" exit /b 19'),'exit /b 0')|Set-Content $c.spy -Encoding ascii
    return $c
}
function Launch-FailedRag($c){
    $failed=$false
    try{& (Join-Path $project 'scripts/run-release-rag.ps1') -Question 'APK build release' -ComponentRun $c.component -RunRoot $c.run -Java $c.spy *> "$($c.root)/launcher.log"}
    catch{$failed=$true;$_.Exception.Message|Add-Content "$($c.root)/launcher.log"}
    [pscustomobject]@{failed=$failed;invoked=(Test-Path -LiteralPath "$($c.root)/invoked.txt")}
}
Case 'Failed RAG uses the ATF classpath when game-core comes first' {
    $c=FailedRagContext 'rag-module-order'
    $r=Launch-FailedRag $c
    Require (-not$r.failed-and$r.invoked) 'The failed RAG did not launch with the ATF classpath'
}
Case 'Release RAG passes the canonical external data root to its Java evaluator' {
    $previousRoot=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT','Process')
    $c=FailedRagContext 'rag-data-root'
    $expected=Get-AtfDataRoot -ProjectRoot $project
    $r=Launch-FailedRag $c
    $actual=Get-Content -Raw -LiteralPath "$($c.root)/data-root-argument.txt" -ErrorAction SilentlyContinue
    $restoredRoot=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT','Process')
    Require (-not$r.failed-and$r.invoked-and$actual.Trim()-ceq$expected-and$restoredRoot-ceq$previousRoot) 'RAG Java did not receive the canonical external artifact root or leaked it to the caller'
}
Case 'Failed RAG refuses conflicting ATF classpaths before Java' {
    $c=FailedRagContext 'rag-disagreement'
    $r=Launch-FailedRag $c
    Require ($r.failed-and-not$r.invoked) 'Conflicting ATF evidence reached Java'
}
Case 'Failed RAG refuses game-core as the sole classpath' {
    $c=FailedRagContext 'rag-no-atf'
    $r=Launch-FailedRag $c
    Require ($r.failed-and-not$r.invoked) 'A game-core-only receipt reached Java'
}

Case 'RAG removes only its Java scratch after success and failure' {
    foreach($exitCode in @(0,19)){
        $c=FailedRagContext "rag-scratch-$exitCode"
        $c.spy=Join-Path $c.root 'ScratchSpy.ps1'
        @'
$property=$args[3]
if($property -cnotmatch '^-Djava\.io\.tmpdir=(.+)$'){exit 27}
$scratch=$Matches[1]
if(-not(Test-Path -LiteralPath $scratch -PathType Container)){exit 28}
'invoked'|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'invoked.txt')
$scratch|Set-Content -LiteralPath (Join-Path $PSScriptRoot 'scratch.txt')
New-Item -ItemType Directory -Path (Join-Path $scratch 'target')|Out-Null
'generated'|Set-Content -LiteralPath (Join-Path $scratch 'target/transient.txt')
exit __EXIT_CODE__
'@.Replace('__EXIT_CODE__',[string]$exitCode)|Set-Content -LiteralPath $c.spy -Encoding utf8NoBOM
        $r=Launch-FailedRag $c
        Require $r.invoked 'The recorder did not receive a dedicated Java scratch directory'
        $scratch=(Get-Content -LiteralPath "$($c.root)/scratch.txt" -Raw).Trim()
        Require ((Split-Path -Parent $scratch)-ceq$c.run) 'Scratch escaped the RAG run'
        Require (-not(Test-Path -LiteralPath $scratch)) 'Java scratch was retained'
        Require (Test-Path -LiteralPath "$($c.component)/result.json") 'The input receipt was removed'
        $cleanup=Get-Content -Raw -LiteralPath "$($c.run)/scratch-cleanup.json"|ConvertFrom-Json
        Require ($cleanup.status-ceq'PASS'-and$cleanup.path-ceq$scratch) 'Cleanup receipt did not identify the removed scratch'
        Require ($r.failed-eq($exitCode-ne0)) 'Cleanup changed the evaluator verdict'
    }
}

Case 'Unchanged qualified runtime reaches only the injected recorder' {
    $c=Context valid;$r=Launch $c
    Require (-not$r.failed-and$r.invoked) 'Valid neighboring runtime was rejected'
}
Case 'Another exposure registry is refused before Java' {
    $c=Context wrongRegistry
    $r=Launch $c (Join-Path $product 'registry.json')
    Require ($r.failed-and-not$r.invoked) 'An alternate registry reached the JVM'
    Require ((Get-Content "$($c.root)/launcher.log" -Raw).Contains('installation exposure registry')) 'Wrong refusal cause'
}
foreach($kind in @('helper','resource','jar','addition','deletion','order','empty-entry','manifest','attestation')){
    $mutation=$kind
    Case ("Changed runtime is refused before Java: "+$mutation) {
        $c=Context $mutation
        switch($mutation){
            helper{[IO.File]::WriteAllText("$($c.classes)/model/Helper.class",'changed-helper')}
            resource{[IO.File]::WriteAllText("$($c.classes)/model/rules.json",'changed-resource')}
            jar{[IO.File]::WriteAllText("$($c.root)/dependency.jar",'changed-jar')}
            addition{[IO.File]::WriteAllText("$($c.classes)/model/Extra.class",'extra')}
            deletion{Remove-Item -LiteralPath "$($c.classes)/model/Helper.class"}
            order{$d=Get-Content -Raw $c.descriptor|ConvertFrom-Json;$d.classpath="$($c.root)/dependency.jar"+[IO.Path]::PathSeparator+$c.classes;Json $c.descriptor $d}
            empty-entry{$d=Get-Content -Raw $c.descriptor|ConvertFrom-Json;$d.classpath+=[IO.Path]::PathSeparator;Json $c.descriptor $d}
            manifest{Add-Content -LiteralPath $c.inventory -Value ' '}
            attestation{Add-Content -LiteralPath $c.attestation -Value ' '}
        }
        if($mutation-in@('order','empty-entry')){$r=Get-Content -Raw "$($c.component)/result.json"|ConvertFrom-Json;$r.arenaRuntimeSha256=Hash $c.descriptor;Json "$($c.component)/result.json" $r}
        $r=Launch $c
        Require ($r.failed-and-not$r.invoked) 'Unqualified bytes or classpath reached the JVM command'
    }
}
Case 'Changed class during a command invalidates the result after Java' {
    $c=Context during
    @('@echo off',('echo invoked > "'+$c.root+'\invoked.txt"'),('echo changed > "'+$c.classes+'\model\Helper.class"'),('mkdir "'+$c.run+'"'),'exit /b 0')|Set-Content $c.spy -Encoding ascii
    $r=Launch $c
    Require ($r.failed-and$r.invoked) 'Postflight failed to invalidate changed class bytes'
}
Case 'A recorded linked subtree is refused without traversing its target' {
    $c=Context linked
    $target=Join-Path $c.root 'external';New-Item -ItemType Directory $target|Out-Null
    [IO.File]::WriteAllText((Join-Path $target 'Extra.class'),'external')
    $link=Join-Path $c.classes 'alias'
    New-Item -ItemType Junction -Path $link -Target $target|Out-Null
    try{
        $manifest=Get-Content -Raw $c.inventory|ConvertFrom-Json
        $manifest.entries[0].files+=@([pscustomobject]@{path='alias/Extra.class';sha256=(Hash (Join-Path $target 'Extra.class'))})
        Json $c.inventory $manifest
        $proof=Get-Content -Raw $c.attestation|ConvertFrom-Json;$proof.inventorySha256=Hash $c.inventory;Json $c.attestation $proof
        $descriptor=Get-Content -Raw $c.descriptor|ConvertFrom-Json;$descriptor.inventorySha256=Hash $c.inventory;$descriptor.attestationSha256=Hash $c.attestation;Json $c.descriptor $descriptor
        $receipt=Get-Content -Raw "$($c.component)/result.json"|ConvertFrom-Json;$receipt.arenaRuntimeSha256=Hash $c.descriptor;Json "$($c.component)/result.json" $receipt
        $r=Launch $c;Require ($r.failed-and-not$r.invoked) 'Linked classpath content was accepted'
    }finally{
        $resolved=[IO.Path]::GetFullPath($link)
        if(-not$resolved.StartsWith([IO.Path]::GetFullPath($c.root)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Owned link escaped fixture'}
        Remove-Item -LiteralPath $link
    }
}
Case 'Shared native exposure completes at capture without starting a report' {
    $tokens=$null;$errors=$null
    $shared=Join-Path $project 'atf-tests/tools/run-shared-ui-sanity.ps1'
    $ast=[Management.Automation.Language.Parser]::ParseFile($shared,[ref]$tokens,[ref]$errors)
    Require (-not $errors) 'Shared launcher does not parse'
    $names=@('Start-DevelopmentExposure','Complete-DevelopmentExposure')
    $functions=@($ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -in $names
    },$true))
    Require ($functions.Count -eq 2) 'Expected the shared exposure helpers'
    $calls=@($ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -in $names
    },$true))
    Require (($calls.GetCommandName() -join ',') -ceq (($names+$names+$names) -join ',')) 'Each native journal path must have its own begin and completion'
    $requests=[Collections.Generic.List[object]]::new()
    $developmentExposures=[Collections.Generic.List[object]]::new()
    $developmentLedger=Join-Path $fixture 'shared-exposure/learning/exposure.json'
    function Invoke-DevelopmentExposure([string[]]$Arguments){$requests.Add($Arguments)}
    foreach($helper in $functions){. ([scriptblock]::Create($helper.Extent.Text))}
    $token=Start-DevelopmentExposure
    Require ($requests.Count -eq 1 -and $requests[0][0] -ceq 'begin-exposure' -and $developmentExposures.Count -eq 0) 'An interrupted capture must remain pending'
    $journal=Join-Path $fixture 'shared-development-journal.json'
    '{"players":3,"seed":487}'|Set-Content -LiteralPath $journal -Encoding utf8NoBOM
    Complete-DevelopmentExposure $journal $token
    $sha256=Hash $journal
    Require ($requests.Count -eq 3 -and $requests[1][0] -ceq 'bind-exposure' -and $requests[2][0] -ceq 'complete-exposure') 'Capture must bind before completing'
    Require ($requests[1][1] -eq $developmentLedger -and $requests[1][2] -ceq $token -and $requests[1][3] -eq $sha256) 'Reservation must bind the captured bytes'
    Require ($requests[2][1] -eq $journal -and $requests[2][2] -eq $developmentLedger -and $requests[2][3] -ceq $token) 'Completion must use the bound reservation'
    Require ($developmentExposures.Count -eq 1 -and $developmentExposures[0].sha256 -eq $sha256) 'Captured journal evidence differs'
}
if(-not$NativePublicationScript){$NativePublicationScript=Join-Path $project 'atf-tests/tools/run-android-sanity.ps1'}
$publicationTokens=$null;$publicationErrors=$null
$nativeAst=[Management.Automation.Language.Parser]::ParseFile($NativePublicationScript,[ref]$publicationTokens,[ref]$publicationErrors)
if($publicationErrors){throw 'Native publication source has invalid syntax'}
Case 'Native exposure uses the configured installation ledger' {
    $commands=@($nativeAst.FindAll({
        param($node)
        $node -is [Management.Automation.Language.CommandAst] -and $node.Extent.Text.Contains('$mainClass expose')
    },$true))
    Require ($commands.Count -eq 1) 'Expected one native exposure command'
    $expose=[scriptblock]::Create($commands[0].Extent.Text)
    $previousRoot=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT','Process')
    $configuredRoot=Join-Path $fixture 'configured-data'
    $run=Join-Path $fixture 'native-exposure'
    $classpath='injected'
    $mainClass='atf.rentz.android.RentzNativeFlow'
    $invocations=[Collections.Generic.List[object]]::new()
    $Java={ $invocations.Add([string[]]$args) }.GetNewClosure()
    try {
        [Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$configuredRoot,'Process')
        & $expose | Out-Null
        $expected=Join-Path $configuredRoot 'learning/exposure.json'
        Require ($invocations.Count -eq 1) 'Expected one call to the injected Java adapter'
        $actual=$invocations[0][-1]
        Require ($actual -eq $expected) "Exposure registry mismatch. Expected $expected, received $actual"
    } finally {
        [Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$previousRoot,'Process')
    }
}
$publicationTry=@($nativeAst.FindAll({param($node) $node-is[Management.Automation.Language.TryStatementAst]-and$node.Extent.Text.Contains('Native report publication failed')},$true))
if($publicationTry.Count-ne1){throw 'One native publication boundary expected'}
$publication=[scriptblock]::Create($publicationTry[0].Extent.Text)
foreach($mode in @('stable','mutate')){
    $publicationMode=$mode
    Case ('Native publication boundary '+$publicationMode+' is checked without starting a device') {
        Import-Module (Join-Path $project 'scripts/RuntimeQualification.psm1') -Force
        $c=Context ('publication-'+$publicationMode)
        $descriptor=Get-Content -Raw $c.descriptor|ConvertFrom-Json
        $descriptor.mainClass='atf.rentz.android.RentzNativeFlow';Json $c.descriptor $descriptor
        $component=[pscustomobject]@{nativeRuntime=$c.descriptor;nativeRuntimeSha256=(Hash $c.descriptor);stage=$c.stage}
        $run=$c.run;$Java=$c.spy;$classpath=$descriptor.classpath;$mainClass=$descriptor.mainClass
        New-Item -ItemType Directory $run|Out-Null
        Json (Join-Path $run 'ui-result.json') ([ordered]@{status='PASS';failure=''})
        if($publicationMode-eq'mutate'){
            @('@echo off',('echo invoked > "'+$c.root+'\invoked.txt"'),('echo changed > "'+$c.classes+'\model\Helper.class"'),'exit /b 0')|Set-Content $c.spy -Encoding ascii
        }
        $publishLease=[pscustomobject]@{disposed=$false}
        $publishLease|Add-Member -MemberType ScriptMethod -Name Dispose -Value {$this.disposed=$true}
        $failedPublication=$false
        try{& $publication *> (Join-Path $c.root 'publication.log')}catch{$failedPublication=$true}
        $published=Get-Content -Raw (Join-Path $run 'ui-result.json')|ConvertFrom-Json
        Require (Test-Path (Join-Path $c.root 'invoked.txt')) 'Native publication fixture did not reach its injected Java recorder'
        Require $publishLease.disposed 'Native publication lease was not released'
        if($publicationMode-eq'stable'){
            Require (-not$failedPublication-and$published.status-eq'PASS') 'Healthy native publication was rejected'
        }else{
            Require ($failedPublication-and$published.status-eq'FAIL') 'Changed publication runtime retained a successful receipt'
        }
    }
}
Case 'Seat geometry agrees between raw UIAutomator nodes and normalized evidence' {
    $uiTokens=$null;$uiErrors=$null
    $uiSource=Join-Path $project 'atf-tests/tools/run-shared-ui-sanity.ps1'
    $uiAst=[Management.Automation.Language.Parser]::ParseFile($uiSource,[ref]$uiTokens,[ref]$uiErrors)
    Require (-not$uiErrors) 'Shared UI runner does not parse'
    $names=@('Bounds','SeatGeometry')
    $functions=@($uiAst.FindAll({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-in$names},$true))
    Require ($functions.Count-eq2) 'Expected geometry functions in the native runner'
    foreach($function in $functions){. ([scriptblock]::Create($function.Extent.Text))}
    [xml]$document='<hierarchy><node content-desc="" bounds="[0,0][1,1]"/><node content-desc="Ana. Points: 0. Total: 0" bounds="[1,2][11,12]"/><node content-desc="Mara. Points: 0. Total: 0" bounds="[20,2][30,12]"/><node content-desc="Nora. Points: 0. Total: 0" bounds="[40,2][50,12]"/><node content-desc="Iris. Points: 0. Total: 0" bounds="[60,2][70,12]"/></hierarchy>'
    $state=[pscustomobject]@{opponents=@('Mara','Nora','Iris');view=[pscustomobject]@{players=4}}
    $raw=$document.SelectNodes('//node')
    $normalized=@($raw | ForEach-Object {
        [pscustomobject]@{description=$_.GetAttribute('content-desc');bounds=$_.GetAttribute('bounds')}
    })
    $expected='0=[1,2][11,12];1=[20,2][30,12];2=[40,2][50,12];3=[60,2][70,12]'
    Require ((SeatGeometry $raw $state)-ceq$expected) 'Raw native seat rectangles differ'
    Require ((SeatGeometry $normalized $state)-ceq$expected) 'Normalized native seat rectangles differ'
    $rejected=$false
    try { SeatGeometry ($normalized+@($normalized[1])) $state | Out-Null }
    catch { $rejected=$_.Exception.Message -like 'One visible frame required for seat 0*' }
    Require $rejected 'A duplicate seat frame must fail closed'
}
Case 'Hand order ignores played Rentz board cells but detects a reversed hand' {
    $uiTokens=$null;$uiErrors=$null
    $uiSource=Join-Path $project 'atf-tests/tools/run-shared-ui-sanity.ps1'
    $uiAst=[Management.Automation.Language.Parser]::ParseFile($uiSource,[ref]$uiTokens,[ref]$uiErrors)
    Require (-not$uiErrors) 'Shared UI runner does not parse'
    $names=@('Bounds','Check','Text','TableChecks')
    $functions=@($uiAst.FindAll({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-in$names},$true))
    Require ($functions.Count-eq$names.Count) 'Expected the native table checks'
    foreach($function in $functions){. ([scriptblock]::Create($function.Extent.Text))}
    $state=[pscustomobject]@{opponents=@();view=[pscustomobject]@{players=1;hand=@(
        [pscustomobject]@{rank=11;suit='HEARTS'},[pscustomobject]@{rank=13;suit='CLUBS'})}}
    $nodes=@(
        [pscustomobject]@{description='Ana. Points: 0. Total: 0';bounds='[0,0][20,10]'},
        [pscustomobject]@{description='Your hand: 2';bounds='[0,30][60,50]'},
        [pscustomobject]@{description='Play J♥';bounds='[0,30][20,50]'},
        [pscustomobject]@{description='K♣. Unavailable';bounds='[20,30][40,50]'},
        [pscustomobject]@{description='10♦';bounds='[10,15][30,25]'})
    $checks=@(TableChecks $nodes $state)
    Require (@($checks|Where-Object{-not$_.passed}).Count-eq0) 'A public board card polluted the hand order'
    $nodes[2].bounds='[20,30][40,50]';$nodes[3].bounds='[0,30][20,50]'
    $reversed=@(TableChecks $nodes $state|Where-Object name -CEQ 'Actual left-to-right hand order')
    Require ($reversed.Count-eq1-and-not$reversed[0].passed) 'A reversed own hand must still fail'
}
Case 'First human move evidence requires one successful Boolean assertion' {
    $tokens=$null;$errors=$null
    $path=Join-Path $project 'atf-tests/tools/run-shared-ui-sanity.ps1'
    $ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
    Require (-not$errors) 'Shared UI runner does not parse'
    $definition=$ast.Find({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-eq'ConfirmNativeCheck'},$true)
    . ([scriptblock]::Create($definition.Extent.Text))
    $title='Subgame 1 first human move remains visible'
    $check='The same seat geometry survives a card play'
    $valid=[pscustomobject]@{title=$title;checks=@([pscustomobject]@{name=$check;passed=$true})}
    $value=ConfirmNativeCheck @($valid) $title $check
    Require ($value-is[bool]-and$value) 'The receipt requires a JSON Boolean true'
    foreach($invalid in @($null,$false,'true')){
        $bad=[pscustomobject]@{title=$title;checks=@([pscustomobject]@{name=$check;passed=$invalid})}
        $rejected=$false
        try { ConfirmNativeCheck @($bad) $title $check | Out-Null } catch { $rejected=$true }
        Require $rejected 'Null, false and string values cannot qualify the first move'
    }
    foreach($count in @(0,2)){
        $rejected=$false
        try { ConfirmNativeCheck (@($valid)*$count) $title $check | Out-Null } catch { $rejected=$true }
        Require $rejected 'Missing or duplicate checkpoints cannot qualify the first move'
    }
}
Case 'Transient Android root loss retries but never reads a stale hierarchy' {
    $tokens=$null;$errors=$null
    $path=Join-Path $project 'atf-tests/tools/run-shared-ui-sanity.ps1'
    $ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
    Require (-not$errors) 'Shared UI runner does not parse'
    $definition=$ast.Find({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq'Ui'},$true)
    . ([scriptblock]::Create($definition.Extent.Text))
    $leaseId='fixture'
    $state=@{dumps=0;reads=0;mode='RECOVER'}
    function Start-Sleep { param([int]$Milliseconds) }
    function Recover-SystemUi($Document,$Hierarchy) { return $false }
    function Adb($Command,$Name) {
        if($Name-eq'dump'){
            $state.dumps++
            if($state.mode-eq'FATAL'){return @{Output='';Error='permission denied'}}
            if($state.mode-eq'KILLED'-and$state.dumps-le3){return @{ExitCode=137;Output="UI hierarchy dumped to: $($Command[-1])";Error=''}}
            if($state.mode-eq'MISSING'-or$state.dumps-le3){return @{Output='';Error='ERROR: null root node returned by UiTestAutomationBridge.'}}
            return @{Output="UI hierchary dumped to: $($Command[-1])";Error=''}
        }
        $state.reads++
        return @{Output='<hierarchy><node text="fresh"/></hierarchy>';Error=''}
    }
    $nodes=@(Ui)
    Require ($nodes.Count-eq1-and$nodes[0].text-ceq'fresh'-and$state.dumps-eq4-and$state.reads-eq1) 'Fourth fresh result must recover'
    $state.mode='KILLED';$state.dumps=0;$state.reads=0
    $nodes=@(Ui)
    Require ($nodes.Count-eq1-and$state.dumps-eq4-and$state.reads-eq1) 'A killed dump must not authorize a stale read'
    foreach($mode in @('MISSING','FATAL')){
        $state.mode=$mode;$state.dumps=0;$state.reads=0;$rejected=$false
        try{Ui|Out-Null}catch{$rejected=$true}
        $expected=if($mode-eq'MISSING'){8}else{1}
        Require ($rejected-and$state.dumps-eq$expected-and$state.reads-eq0) 'No stale read and no retry of fatal errors'
    }
}
Case 'Achievement query retries corrupted native input and requires an exact focused value' {
    $tokens=$null;$errors=$null
    $ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $project 'atf-tests/tools/run-shared-ui-sanity.ps1'),[ref]$tokens,[ref]$errors)
    Require (-not$errors) 'Shared UI runner does not parse'
    foreach($name in @('AchievementKeyboardShown','EnterAchievementSearch')){
        $definition=$ast.Find({param($node) $node-is [Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq$name},$true)
        Require ($null-ne$definition) "Missing native input function $name"
        . ([scriptblock]::Create($definition.Extent.Text))
    }
    $state=@{writes=0;clears=0;stubborn=$false;focused=$true;keyboard=$true;back=0;stuckKeyboard=$false}
    function Tap($Label,$Class) {Require ($Label-ceq'Search achievements'-and$Class-ceq'android.widget.EditText') 'Wrong input target'}
    function Adb($Command,$Name) {
        if($Name-ceq'achievement-search-rule'){
            Require ($Command[-1]-ceq'chose%syourself') 'Query encoding changed';$state.writes++
        }elseif($Name-ceq'achievement-search-keyboard'){
            Require (($Command-join' ')-ceq'shell dumpsys input_method') 'Keyboard visibility must be observed'
            return [pscustomobject]@{Output='mInputShown='+$state.keyboard.ToString().ToLowerInvariant()}
        }elseif($Name-ceq'hide-achievement-search-keyboard'){
            Require ($state.keyboard-and$Command[-1]-ceq'KEYCODE_BACK') 'Back must only hide a visible keyboard'
            $state.back++
            if(!$state.stuckKeyboard){$state.keyboard=$false}
        }else{
            Require ($Command[3]-ceq'KEYCODE_MOVE_END'-and$Command[4]-ceq'KEYCODE_DEL') 'Retry must clear the observed input';$state.clears++
        }
    }
    function Ui { [xml]$sample='<hierarchy><node/></hierarchy>';return $sample.DocumentElement.FirstChild }
    function Get-RentzTapTarget($Nodes,$Label,$Class) {
        [pscustomobject]@{focused=[string]$state.focused.ToString().ToLowerInvariant();text=$(if($state.stubborn-or$state.writes-eq1){'cchose yourse'}else{'chose yourself'})}
    }
    EnterAchievementSearch 'chose yourself'
    Require ($state.writes-eq2-and$state.clears-eq1-and$state.back-eq1-and!$state.keyboard) 'Confirm the full query and close the keyboard before Apply'
    $state.writes=0;$state.clears=0
    EnterAchievementSearch 'chose yourself'
    Require ($state.back-eq1) 'An already hidden keyboard must not receive Back'
    $state.keyboard=$true;$state.stuckKeyboard=$true;$blocked=$false
    try{EnterAchievementSearch 'chose yourself'}catch{$blocked=$_.Exception.Message-like'ANDROID_TEXT_INPUT:*remained visible*'}
    Require ($blocked-and$state.back-eq2) 'A keyboard that stays visible must block Apply'
    foreach($mode in @('stubborn','blurred')){
        $state.writes=0;$state.clears=0;$state.stubborn=$true;$state.focused=$mode-ne'blurred';$failed=$false
        try{EnterAchievementSearch 'chose yourself'}catch{$failed=$_.Exception.Message-like'ANDROID_TEXT_INPUT:*'}
        $expected=if($state.focused){3}else{1}
        Require ($failed-and$state.writes-eq$expected) 'Unconfirmed input must fail within the retry budget'
    }
}
Case 'Direct Release build rejects missing native evidence before staging' {
    $output=Join-Path $fixture 'release-without-native-evidence'
    $component=Join-Path $fixture 'component-not-read-before-preflight'
    $rejected=$false
    try {
        & (Join-Path $project 'scripts/build-rentz-android.ps1') -Configuration Release -ComponentRun $component -RunRoot $output | Out-Null
    } catch {
        $rejected=$_.Exception.Message -ceq 'Release requires UiBuildRun and NativeUiRun for the RAG prebuild gate.'
    }
    Require ($rejected-and-not(Test-Path -LiteralPath $output)) 'Release must require both native inputs before creating a build directory'
}
Case 'Restricted publication requires authorized bytes and explicit access' {
    $tokens=$null;$errors=$null
    $scriptPath=Join-Path $project 'scripts/verify-release-readme.ps1'
    $ast=[Management.Automation.Language.Parser]::ParseFile($scriptPath,[ref]$tokens,[ref]$errors)
    Require (-not$errors) 'README verifier does not parse'
    $definition=$ast.Find({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq'Confirm-RestrictedApk'},$true)
    . ([scriptblock]::Create($definition.Extent.Text))
    $file=Join-Path $fixture 'downloaded.apk'
    [IO.File]::WriteAllText($file,'Qualified artifact fixture')
    $digest=Hash $file
    $source='0123456789abcdef0123456789abcdef01234567'
    $proof=[ordered]@{
        status='PASS';scope='RESTRICTED_TEST_DISTRIBUTION';version='0.10.0';sourceCommit=$source
        driveFileId='Fixture1234';link='https://drive.google.com/file/d/Fixture1234/view'
        permissions=@(@{type='user';role='owner'})
        authenticated=@{provider='Google Drive';status='PASS';bytes=(Get-Item $file).Length;sha256=$digest}
    }
    $fixtureJson=ConvertTo-Json $proof -Depth 5
    Require ((Confirm-RestrictedApk (ConvertFrom-Json $fixtureJson) $file '0.10.0' $source $digest)-ceq$proof.link) 'Authenticated fixture did not qualify'
    foreach($mutation in @('anyone','wrong-file-id','wrong-commit','missing-auth','changed-bytes')){
        $bad=ConvertFrom-Json $fixtureJson
        switch($mutation){
            anyone {$bad.permissions+=@{type='anyone';role='reader'}}
            'wrong-file-id' {$bad.driveFileId='Different1234'}
            'wrong-commit' {$bad.sourceCommit='0123456789abcdef0123456789abcdef01234568'}
            'missing-auth' {$bad.authenticated=$null}
            'changed-bytes' {[IO.File]::WriteAllText($file,'Different artifact fixture')}
        }
        $rejected=$false
        try {Confirm-RestrictedApk $bad $file '0.10.0' $source $digest | Out-Null}catch{$rejected=$true}
        Require $rejected "Restricted gate admitted mutation: $mutation"
    }
}
$githubRestrictedCase = 'GitHub edition requires a private repository, exact tag and verified checksum'
Case $githubRestrictedCase {
    $tokens=$null;$errors=$null
    $scriptPath=Join-Path $project 'scripts/verify-release-readme.ps1'
    $ast=[Management.Automation.Language.Parser]::ParseFile($scriptPath,[ref]$tokens,[ref]$errors)
    Require (-not$errors) 'README verifier does not parse'
    $definition=$ast.Find({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq'Confirm-RestrictedApk'},$true)
    . ([scriptblock]::Create($definition.Extent.Text))
    $apk='RentzLab-0.11.0.apk'
    $file=Join-Path $fixture "authenticated-$apk"
    $checksum="$file.sha256"
    [IO.File]::WriteAllText($file,'Qualified GitHub artifact fixture')
    $digest=Hash $file
    $source='0123456789abcdef0123456789abcdef01234567'
    $sourceRepo='owner/source'
    $releaseRepo='owner/editions'
    $proof=[ordered]@{
        status='PASS';scope='RESTRICTED_TEST_DISTRIBUTION';version='0.11.0';sourceCommit=$source
        releaseRepository=$releaseRepo;repositoryPrivate=$true;repositoryVisibility='private'
        releaseId=42;assetId=43;releaseState='PUBLISHED';tag='v0.11.0';tagCommit=$source
        releaseUrl="https://github.com/$releaseRepo/releases/tag/v0.11.0"
        link="https://github.com/$releaseRepo/releases/download/v0.11.0/$apk"
        authenticated=@{provider='GitHub';status='PASS';http=200;bytes=(Get-Item $file).Length;sha256=$digest}
    }
    $fixtureJson=ConvertTo-Json $proof -Depth 5
    [IO.File]::WriteAllText($checksum,"$digest  $apk")
    Require ((Confirm-RestrictedApk (ConvertFrom-Json $fixtureJson) $file '0.11.0' $source $digest $sourceRepo)-ceq$proof.link) 'Private GitHub fixture did not qualify'
    foreach($mutation in @('public','wrong-visibility','draft','wrong-tag','wrong-link','wrong-owner','wrong-checksum','missing-checksum')){
        $bad=ConvertFrom-Json $fixtureJson
        [IO.File]::WriteAllText($checksum,"$digest  $apk")
        switch($mutation){
            public {$bad.repositoryPrivate=$false}
            'wrong-visibility' {$bad.repositoryVisibility='public'}
            draft {$bad.releaseState='DRAFT'}
            'wrong-tag' {$bad.tagCommit='0123456789abcdef0123456789abcdef01234568'}
            'wrong-link' {$bad.link=$bad.releaseUrl}
            'wrong-owner' {$bad.releaseRepository='another/editions'}
            'wrong-checksum' {[IO.File]::WriteAllText($checksum,"$digest  another.apk")}
            'missing-checksum' {Remove-Item -LiteralPath $checksum}
        }
        $rejected=$false
        try {Confirm-RestrictedApk $bad $file '0.11.0' $source $digest $sourceRepo | Out-Null}catch{$rejected=$true}
        Require $rejected "Private GitHub gate admitted mutation: $mutation"
    }
}
Case 'Pushed README requires one clean game section for each distribution channel' {
    $scriptPath = Join-Path $project 'scripts/verify-release-readme.ps1'
    $tokens = $null
    $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
    Require (-not $errors) 'README verifier does not parse'
    $definition = $ast.Find({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Confirm-ReadmeGameLinks'
    }, $true)
    Require ($null -ne $definition) 'README navigation validation is missing'
    . ([scriptblock]::Create($definition.Extent.Text))

    foreach ($repo in @('example/game', 'example/private-editions')) {
        $page = "https://github.com/$repo/releases/tag/v1.2.0"
        $asset = "https://github.com/$repo/releases/download/v1.2.0/RentzLab-1.2.0.apk"
        $listedPage = if ($repo -ceq 'example/game') { $page } else { 'release/1.2.0/README.md' }
        $releaseLine = "- [Rentz Lab]($listedPage)`n"
        $clean = "# Rentz`n`n## Jocul`n`n$releaseLine- [Descarcă APK-ul]($asset)`n`n## Documentație`n"
        Confirm-ReadmeGameLinks $clean $listedPage $asset
        $invalid = @(
            $clean.Replace('## Jocul', '## Ediție cu acces autorizat')
            $clean.Replace("## Jocul`n`n", "## Jocul`n`nAutentifică-te în GitHub.`n`n")
            $clean.Replace("($asset)", "($asset) · Android 8.0+")
            $clean.Replace('## Documentație', "## Jocul`n`n## Documentație")
            "$clean`n- [APK vechi](https://downloads.example.test/v1.1.0/RentzLab-1.1.0.apk)`n"
            $clean.Replace($asset, ($asset + '?redirect=1'))
            $clean.Replace('.apk)', '_sanity.apk)')
            $clean.Replace($releaseLine, '')
            $clean.Replace($listedPage, ($listedPage -replace '1\.2\.0', '1.1.0'))
            $clean.Replace($releaseLine, '').Replace("($asset)`n", "($asset)`n$releaseLine")
        )
        foreach ($candidate in $invalid) {
            $rejected = $false
            try { Confirm-ReadmeGameLinks $candidate $listedPage $asset } catch { $rejected = $true }
            Require $rejected "README verifier accepted invalid navigation for $repo"
        }
    }
}
Case 'Retirement reopens both qualified Extent files and rejects missing or foreign evidence' {
    $scriptPath = Join-Path $project 'scripts/verify-release-readme.ps1'
    $tokens = $null
    $errors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
    Require (-not $errors) 'README verifier does not parse'
    $definition = $ast.Find({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Confirm-RetirementReports'
    }, $true)
    Require ($null -ne $definition) 'The retirement report verifier is missing'
    . ([scriptblock]::Create($definition.Extent.Text))

    $source = 'a' * 40
    $component = Join-Path $product 'component-suite-extent.html'
    $native = Join-Path $product 'android-extent.html'
    [IO.File]::WriteAllText($component,'<html>component</html>')
    [IO.File]::WriteAllText($native,'<html>native pass</html>')
    $claims = @(
        [pscustomobject]@{id='component-extent-report';status='PASS';receipt='component-suite-extent.html';receiptSha256=(Hash $component);artifactBytes=(Get-Item -LiteralPath $component).Length;artifactSourceCommit=$source},
        [pscustomobject]@{id='native-ui-report';status='PASS';receipt='android-extent.html';receiptSha256=(Hash $native);artifactBytes=(Get-Item -LiteralPath $native).Length;artifactSourceCommit=$source}
    )
    $gate = $ast.Find({ param($node)
        $node -is [Management.Automation.Language.AssignmentStatementAst] -and $node.Left.Extent.Text -ceq '$retirementAllowed'
    }, $true)
    Require ($null -ne $gate) 'The retirement authorization assignment is missing'
    $authorize = [scriptblock]::Create($gate.Extent.Text)
    $latestAuthenticated = $true
    $extentEvidence = @(Confirm-RetirementReports $claims $source $component $native $project)
    . $authorize
    Require ($retirementAllowed -and $extentEvidence.Count -eq 2) 'Both exact Extent artifacts should permit retirement'
    $missing = @(Confirm-RetirementReports $claims $source $component '' $project)
    Require ($missing[1].status -ceq 'BLOCKED' -and $missing[1].reason -ceq 'Missing report file.') 'Missing report allowed retirement'
    [IO.File]::WriteAllText($native,'<html>native fail</html>')
    $modified = @(Confirm-RetirementReports $claims $source $component $native $project)
    $extentEvidence = $modified
    . $authorize
    Require (!$retirementAllowed -and $modified[0].status -ceq 'PASS' -and $modified[1].status -ceq 'BLOCKED' -and
        $modified[1].reason -cmatch 'SHA-256') 'Same-length replacement did not block retirement'
    [IO.File]::WriteAllText($native,'<html>native pass</html>')
    $claims[1].artifactSourceCommit = 'b' * 40
    $foreign = @(Confirm-RetirementReports $claims $source $component $native $project)
    Require ($foreign[1].status -ceq 'BLOCKED' -and $foreign[1].reason -cmatch 'identity') 'Report from another candidate allowed retirement'
}
$failed=@($results|Where-Object status -EQ FAIL)
[ordered]@{status=$(if($failed.Count){'FAIL'}else{'PASS'});tests=$results.Count;failed=$failed.Count;fixture=$fixture;productFixtures=$product;scope='Byte fixtures, injected Java recorder and extracted publication block; no real JVM body, game, full native launcher or device';results=$results}|ConvertTo-Json -Depth 7|Set-Content "$RunRoot/controls.json" -Encoding utf8NoBOM
$results|Format-Table name,status -AutoSize
if($failed.Count){throw 'Qualified runtime controls failed'}
