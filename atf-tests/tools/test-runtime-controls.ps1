param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $project 'rentz-reference/tools/RentzAndroid.psm1') -Force
Import-Module (Join-Path $project 'scripts/TestCollections.psm1') -Force
$allowed=Get-AtfInfrastructurePath -Area governance -ProjectRoot $project
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
Assert-AtfPhysicalPath -Path $RunRoot
if(-not$RunRoot.StartsWith($allowed+'\',[StringComparison]::OrdinalIgnoreCase)-or(Test-Path -LiteralPath $RunRoot)){throw 'Fresh registered control output required'}
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$fixture=New-AtfWorkspace -Kind fixture -ProjectRoot $project
$results=[Collections.Generic.List[object]]::new()
function Require([bool]$Value,[string]$Reason){if(-not$Value){throw $Reason}}
function Case([string]$Name,[scriptblock]$Body){try{& $Body;$results.Add([pscustomobject]@{name=$Name;status='PASS'})}catch{$results.Add([pscustomobject]@{name=$Name;status='FAIL';error=$_.Exception.Message})}}
function Context([string]$Name){
    $run=Join-Path $fixture $Name;New-Item -ItemType Directory -Path $run|Out-Null
    $lease=Join-Path $run 'current.json';$owner=@{active=$true;leaseId='mine';serial='offline-fixture'}|ConvertTo-Json
    $owner|Set-Content -LiteralPath $lease;$owner|Set-Content -LiteralPath (Join-Path $run 'lease.json')
    [pscustomobject]@{run=$run;lease=$lease}
}
function Fake([string]$Output='Salut ș ț',[string]$ErrorText='',[int]$Exit=0){
    $encoding=[Text.Encoding]::UTF8
    $p=[pscustomobject]@{StartInfo=$null;StandardOutput=[IO.StreamReader]::new([IO.MemoryStream]::new($encoding.GetBytes($Output)),$encoding);StandardError=[IO.StreamReader]::new([IO.MemoryStream]::new($encoding.GetBytes($ErrorText)),$encoding);ExitCode=$Exit;Started=0;Killed=0;Disposed=0;DisposeFails=$false;KillFails=$false;Waits=[Collections.Generic.List[int]]::new();Timeout=$false;LeaseToChange=''}
    $p|Add-Member ScriptMethod Start {$this.Started++;if($this.LeaseToChange){@{active=$true;leaseId='other';serial='offline-fixture'}|ConvertTo-Json|Set-Content -LiteralPath $this.LeaseToChange};return $true}
    $p|Add-Member ScriptMethod WaitForExit {param([int]$Milliseconds=0) $this.Waits.Add($Milliseconds);return (-not$this.Timeout-or$this.Killed-gt0)}
    $p|Add-Member ScriptMethod Kill {param([bool]$Tree) $this.Killed++;if($this.KillFails){throw 'controlled termination failure'}}
    $p|Add-Member ScriptMethod Dispose {$this.Disposed++;$this.StandardOutput.Dispose();$this.StandardError.Dispose();if($this.DisposeFails){throw 'controlled dispose failure'}}
    return $p
}
function Command($Context,$Process){Invoke-RentzAdb -RunRoot $Context.run -LeasePath $Context.lease -ProcessFactory {$Process}.GetNewClosure() -Arguments @('offline-only') -Label command -TimeoutSeconds 1}
Case 'UTF8 is explicit on both redirected process streams' {
    $c=Context 'utf8';$p=Fake;$r=Command $c $p
    Require ($r.Output-eq'Salut ș ț') 'Unicode output was altered'
    Require ($p.StartInfo.StandardOutputEncoding.WebName-eq'utf-8'-and$p.StartInfo.StandardErrorEncoding.WebName-eq'utf-8') 'Process encoding depends on host code page'
}
Case 'Changed resource owner invalidates an in-flight result' {
    $c=Context 'changed-owner';$p=Fake;$p.LeaseToChange=$c.lease;$failed=$false
    try{Command $c $p|Out-Null}catch{$failed=$_.Exception.Message-match'ANDROID-LEASE'}
    Require $failed 'Result from a replaced owner was accepted';Require ($p.Disposed-eq1) 'Process was not disposed'
}
Case 'Timeout kills only its child and never uses an infinite exit wait' {
    $c=Context 'deadline';$p=Fake;$p.Timeout=$true;$failed=$false
    try{Command $c $p|Out-Null}catch{$failed=$_.Exception.Message-match'deadline'}
    Require $failed 'Timeout did not fail';Require ($p.Killed-eq1-and$p.Disposed-eq1) 'Owned process was not killed and disposed'
    Require (-not$p.Waits.Contains(0)) 'Timeout cleanup used an unbounded WaitForExit'
}
Case 'Timeout remains primary even when terminating its process fails' {
    $c=Context 'termination';$p=Fake;$p.Timeout=$true;$p.KillFails=$true;$errorFound=$null
    try{Command $c $p|Out-Null}catch{$errorFound=$_.Exception}
    Require ($null-ne$errorFound-and$errorFound.Message-match'deadline') 'Termination failure replaced the deadline'
    Require ($null-ne$errorFound.Data['terminationFailure']-and$p.Disposed-eq1) 'Termination diagnostic or disposal lost'
}
Case 'Nonzero exit cannot become success and stderr remains evidence' {
    $c=Context 'exit';$p=Fake 'partial' 'controlled error' 7;$failed=$false
    try{Command $c $p|Out-Null}catch{$failed=$_.Exception.Message-match'ADB failed'}
    Require $failed 'Unexpected exit accepted';Require ((Get-Content -Raw -LiteralPath "$($c.run)/command.stderr.txt")-match'controlled error') 'Diagnostic lost'
    Require ($p.Disposed-eq1) 'Process not disposed'
}
Case 'Process cleanup failure cannot replace the original command failure' {
    $c=Context 'cleanup';$p=Fake 'partial' 'controlled error' 7;$p.DisposeFails=$true;$errorFound=$null
    try{Command $c $p|Out-Null}catch{$errorFound=$_.Exception}
    Require ($null-ne$errorFound-and$errorFound.Message-match'ADB failed') 'Cleanup replaced the command failure'
    Require ($null-ne$errorFound.Data['cleanupFailures']) 'Secondary cleanup diagnostic was lost'
}
Case 'Replaced owner before start sends no process command' {
    $c=Context 'before';$p=Fake;@{active=$true;leaseId='other';serial='offline-fixture'}|ConvertTo-Json|Set-Content -LiteralPath $c.lease
    $failed=$false;try{Command $c $p|Out-Null}catch{$failed=$_.Exception.Message-match'ANDROID-LEASE'}
    Require ($failed-and$p.Started-eq0) 'Stale owner started a process';$p.Dispose()
}
Case 'Existing evidence is refused before a process starts' {
    $c=Context 'old-evidence';$p=Fake;'old'|Set-Content -LiteralPath "$($c.run)/command.txt"
    $failed=$false;try{Command $c $p|Out-Null}catch{$failed=$_.Exception.Message-match'Evidence already exists'}
    Require ($failed-and$p.Started-eq0) 'Existing evidence was reused';Require ((Get-Content -Raw "$($c.run)/command.txt").Trim()-eq'old') 'Old evidence overwritten';$p.Dispose()
}
Case 'Offline collections have an executable registry' {Require (Test-Path -LiteralPath (Join-Path $project 'config/test-collections.json') -PathType Leaf) 'Quick, regression, learning, infrastructure and Android JVM are not explicit'}
function PlanFixture([string]$Name){
    $folder=Join-Path $fixture $Name;New-Item -ItemType Directory -Path "$folder/config"|Out-Null
    Copy-Item -LiteralPath "$project/config/test-collections.json","$project/config/java-source-layout.json" -Destination "$folder/config"
    $layout=Get-Content -Raw "$folder/config/java-source-layout.json"|ConvertFrom-Json
    foreach($entry in $layout.roots|Where-Object{$_.role-in@('main','test')}){New-Item -ItemType Directory -Path (Join-Path $folder $entry.path) -Force|Out-Null}
    $test=Join-Path $folder 'atf-tests/src/test/java/FutureTest.java';'package future; class FutureTest { @org.junit.jupiter.api.Test void futureCase() {} }'|Set-Content -LiteralPath $test
    return $folder
}
Case 'All five collections are resolvable and regression includes all offline gates' {
    foreach($name in @('quick','regression','learning','infrastructure','android-jvm')){$p=Get-RentzTestPlan -ProjectRoot $project -Collection $name;Require ($p.name-eq$name-and$p.runtimeControls) 'Named collection is not executable'}
    $p=Get-RentzTestPlan -ProjectRoot $project;Require ($p.androidJvm-and$p.selectors-contains'*Test') 'Standard regression omits Android JVM or Maven tests'
}
Case 'Unknown collection and duplicate JSON properties fail closed' {
    $failed=$false;try{Get-RentzTestPlan -ProjectRoot $project -Collection 'typo'|Out-Null}catch{$failed=$true};Require $failed 'Unknown collection accepted'
    $folder=PlanFixture 'duplicate-plan';$file="$folder/config/test-collections.json";$raw=[IO.File]::ReadAllText($file);[IO.File]::WriteAllText($file,$raw.Replace('"schemaVersion": 1','"schemaVersion": 1, "schemaVersion": 1'))
    $failed=$false;try{Get-RentzTestPlan -ProjectRoot $folder|Out-Null}catch{$failed=$_.Exception.Message-match'Duplicate'};Require $failed 'Duplicate collection property accepted'
}
Case 'New conventionally named tests join regression automatically' {
    $folder=PlanFixture 'future-plan';$p=Get-RentzTestPlan -ProjectRoot $folder;Require ($p.expected-contains'future.FutureTest') 'A new test disappeared from regression'
}
Case 'Collection schema uses an integer token rather than coercible values' {
    foreach($value in @('"1"','1.0')){
        $folder=PlanFixture ('typed-plan-'+[guid]::NewGuid().ToString('N'));$file="$folder/config/test-collections.json";$raw=[IO.File]::ReadAllText($file);[IO.File]::WriteAllText($file,$raw.Replace('"schemaVersion": 1',('"schemaVersion": '+$value)))
        $failed=$false;try{Get-RentzTestPlan -ProjectRoot $folder|Out-Null}catch{$failed=$true};Require $failed 'Coercible collection schema version accepted'
    }
}
Case 'Nonconventional test sources cannot silently escape selection' {
    $folder=PlanFixture 'hidden-plan';"package future;`nclass Unselected {`n @org.junit.jupiter.api.Test void ignored(){} }"|Set-Content -LiteralPath "$folder/atf-tests/src/test/java/Unselected.java"
    $failed=$false;try{Get-RentzTestPlan -ProjectRoot $folder|Out-Null}catch{$failed=$_.Exception.Message-match'Unselected'};Require $failed 'A test outside the naming contract was hidden'
}
Case 'Missing extra duplicate skipped and empty XML populations are refused' {
    $plan=[pscustomobject]@{expected=@('fixture.CaseTest')};$ok=[pscustomobject]@{name='fixture.CaseTest';tests=1;failed=0;errors=0;skipped=0}
    Assert-RentzTestPopulation -Plan $plan -Results @($ok)
    foreach($rows in @(@(),@($ok,$ok),@([pscustomobject]@{name='extra.Test';tests=1}),@([pscustomobject]@{name=$ok.name;tests=0}),@([pscustomobject]@{name=$ok.name;tests=1;skipped=1}))){
        $failed=$false;try{Assert-RentzTestPopulation -Plan $plan -Results @($rows)}catch{$failed=$true};Require $failed 'An invalid selected population was accepted'
    }
}
function Capture([string]$Name,[string]$Mode){
    $c=Context $Name
    $port={param($parameters)
        $label=$parameters.Label;$extension=if($parameters.Binary){'bin'}else{'txt'};$path=Join-Path $parameters.RunRoot "$label.$extension"
        $output='';$errorText=''
        if($label-like'*-dump'){$output='UI hierarchy dumped to: '+$parameters.Arguments[-1];if($Mode-eq'zero-error'){$errorText='ERROR: dump failed'}}
        elseif($label-like'*-hierarchy'){$output=if($Mode-eq'malformed'){'<hierarchy'}else{'<hierarchy><node text="Joacă ș ț" /></hierarchy>'}}
        if($parameters.Binary){$bytes=if($Mode-eq'bad-png'){[byte[]]@(1,2,3)}else{[Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBqkAAAAASUVORK5CYII=')};[IO.File]::WriteAllBytes($path,$bytes)}
        elseif($Mode-ne'missing') {[IO.File]::WriteAllText($path,$output)}
        [pscustomobject]@{ExitCode=0;Output=$output;Error=$errorText;File=$path}
    }.GetNewClosure()
    Save-RentzObservation -RunRoot $c.run -Name capture -Command $port
}
Case 'Exit zero with a dump ERROR never reuses an older hierarchy' {
    $failed=$false;try{Capture 'zero-error' 'zero-error'|Out-Null}catch{$failed=$_.Exception.Message-match'Fresh hierarchy'};Require $failed 'Semantic dump failure accepted'
}
Case 'Missing fresh evidence malformed XML and non-PNG output fail' {
    foreach($mode in @('missing','malformed','bad-png')){$failed=$false;try{Capture $mode $mode|Out-Null}catch{$failed=$true};Require $failed "Invalid capture accepted: $mode"}
}
Case 'Valid capture keeps native bytes and Unicode without emulator' {
    $rows=@(Capture 'valid-capture' 'valid');Require ($rows.Count-eq1-and$rows[0].text-eq'Joacă ș ț') 'Native text was altered'
    $png=Join-Path $fixture 'valid-capture/capture.png';Require (Test-Path -LiteralPath $png) 'Native bytes were not retained'
    Require ([Convert]::ToHexString([IO.File]::ReadAllBytes($png)[0..7])-eq'89504E470D0A1A0A') 'PNG changed'
}
$failed=@($results|Where-Object status -NE 'PASS').Count
[ordered]@{status=$(if($failed){'FAIL'}else{'PASS'});tests=$results.Count;failed=$failed;fixture=$fixture;scope='Injected process and lease fixtures only; no adb.exe or emulator';results=$results}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath "$RunRoot/controls.json" -Encoding utf8NoBOM
$results|Format-Table -AutoSize
if($failed){exit 1}
