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
    Require ($requests.Count -eq 2 -and $requests[1][0] -ceq 'complete-exposure' -and $requests[1][2] -eq $developmentLedger) 'Capture must commit to the shared ledger'
    Require ($requests[1][3] -ceq $token -and $developmentExposures.Count -eq 1 -and $developmentExposures[0].sha256 -eq (Hash $journal)) 'Captured journal evidence differs'
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
    function Adb($Command,$Name) {
        if($Name-eq'dump'){
            $state.dumps++
            if($state.mode-eq'FATAL'){return @{Output='';Error='permission denied'}}
            if($state.mode-eq'MISSING'-or$state.dumps-le3){return @{Output='';Error='ERROR: null root node returned by UiTestAutomationBridge.'}}
            return @{Output="UI hierchary dumped to: $($Command[-1])";Error=''}
        }
        $state.reads++
        return @{Output='<hierarchy><node text="fresh"/></hierarchy>';Error=''}
    }
    $nodes=@(Ui)
    Require ($nodes.Count-eq1-and$nodes[0].text-ceq'fresh'-and$state.dumps-eq4-and$state.reads-eq1) 'Fourth fresh result must recover'
    foreach($mode in @('MISSING','FATAL')){
        $state.mode=$mode;$state.dumps=0;$state.reads=0;$rejected=$false
        try{Ui|Out-Null}catch{$rejected=$true}
        $expected=if($mode-eq'MISSING'){8}else{1}
        Require ($rejected-and$state.dumps-eq$expected-and$state.reads-eq0) 'No stale read and no retry of fatal errors'
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
$failed=@($results|Where-Object status -EQ FAIL)
[ordered]@{status=$(if($failed.Count){'FAIL'}else{'PASS'});tests=$results.Count;failed=$failed.Count;fixture=$fixture;productFixtures=$product;scope='Byte fixtures, injected Java recorder and extracted publication block; no real JVM body, game, full native launcher or device';results=$results}|ConvertTo-Json -Depth 7|Set-Content "$RunRoot/controls.json" -Encoding utf8NoBOM
$results|Format-Table name,status -AutoSize
if($failed.Count){throw 'Qualified runtime controls failed'}
