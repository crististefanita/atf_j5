param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference='Stop'
$source=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $source 'scripts/BuildRetention.psm1') -Force
New-Item -ItemType Directory -Path $RunRoot | Out-Null
$checks=[Collections.Generic.List[object]]::new()
function Require([bool]$value,[string]$message){if(!$value){throw $message}}
function Case([string]$name,[scriptblock]$test){
    try{& $test;$checks.Add([pscustomobject]@{name=$name;expected='PASS';actual='PASS'})}
    catch{$checks.Add([pscustomobject]@{name=$name;expected='PASS';actual=$_.Exception.Message})}
}
function Fixture([string]$name){
    $data=Join-Path $RunRoot $name
    $id=[guid]::NewGuid().ToString('N')
    $fixture=Join-Path $data "infrastructure/workspaces/fixture-$id"
    $product=Join-Path $data "components/runtime-fixtures-$id"
    $cache=Join-Path $data "infrastructure/cache/runtime-fixture-$id"
    $owner=Join-Path $data "infrastructure/governance/qualified-runtime-controls-$id"
    $evidence=Join-Path $data 'distribution'
    New-Item -ItemType Directory $fixture,$product,$cache,$owner,$evidence | Out-Null
    $descriptor=[ordered]@{schemaVersion=1;owner='atf_j5';kind='fixture';id=$id;sourceRoot=$source;createdUtc=[datetime]::UtcNow.ToString('o')}
    $descriptor | ConvertTo-Json | Set-Content "$fixture/.atf-workspace.json"
    'synthetic runtime' | Set-Content "$fixture/fixture.txt"
    '{"sample":"regenerable"}' | Set-Content "$product/result.json"
    $receipt=Join-Path $owner 'controls.json'
    [ordered]@{status='PASS';tests=1;failed=0;fixture=$fixture;productFixtures=$product;disposableCaches=@($cache);
        scope='Byte fixtures, injected Java recorder and extracted publication block; no real JVM body, game, full native launcher or device';
        results=@(@{name='synthetic case';status='PASS'})} | ConvertTo-Json -Depth 6 | Set-Content $receipt
    [pscustomobject]@{data=$data;fixture=$fixture;product=$product;cache=$cache;receipt=$receipt;evidence=$evidence}
}
function Plan($c,[hashtable]$a=@{}){
    Get-RentzRuntimeRetention -DataRoot $c.data -ControlReceipt $c.receipt -EvidenceRoot $c.evidence @a
}
$quiet={@{ActiveCommandLine=@();GitWorktree=@()}}
Case 'Synthetic owner yields an inventoried fixture, product and cache' {
    $c=Fixture 'eligible';$p=Plan $c
    Require ($p.status-ceq'REGENERABLE') "Unexpected plan: $($p.status)"
    Require ($p.paths.Count-eq3-and@($p.paths|Where-Object sha256 -Match '^[a-f0-9]{64}$').Count-eq3) 'Expected all three physical snapshots'
}
Case 'Receipts, active classpaths and managed worktrees preserve their cohort' {
    $c=Fixture 'references'
    @{classpath=($c.product.Replace('\','/')+'/archive')}|ConvertTo-Json|Set-Content "$($c.evidence)/result.json"
    $p=Plan $c @{ActiveCommandLine=@("java -cp $($c.cache)");GitWorktree=@("$($c.fixture)/checkout")}
    foreach($reason in 'evidence','process','worktree'){Require ($p.reasons -contains $reason) "Missing blocker: $reason"}
    Require ($p.status-ceq'KEEP') 'Referenced group became eligible'
}
Case 'An inaccessible evidence root preserves the entire cohort' {
    $c=Fixture 'missing-evidence'
    $p=Get-RentzRuntimeRetention -DataRoot $c.data -ControlReceipt $c.receipt -EvidenceRoot "$($c.evidence)/missing"
    Require ($p.status-ceq'KEEP'-and$p.reasons-contains'inventory-incomplete') 'Incomplete scan was accepted'
}
Case 'Active reusable caches and Git or evidence payloads are refused' {
    $c=Fixture 'foreign'
    $v=Get-Content -Raw $c.receipt|ConvertFrom-Json
    $maven=Join-Path $c.data 'infrastructure/cache/maven'
    New-Item -ItemType Directory $maven | Out-Null
    $v.disposableCaches=@($maven);$v|ConvertTo-Json -Depth 6|Set-Content $c.receipt
    $p=Plan $c
    Require ($p.status-ceq'KEEP'-and$p.reasons-contains'ownership') 'Reusable Maven cache selected'
    $v.disposableCaches=@($c.cache);$v|ConvertTo-Json -Depth 6|Set-Content $c.receipt
    'protected'|Set-Content "$($c.fixture)/release.apk"
    $p=Plan $c
    Require ($p.status-ceq'KEEP'-and$p.reasons-contains'retained-output') 'APK was selected'
}
Case 'Only the exact mock APK bytes at the fixture root are regenerable' {
    $c=Fixture 'mock-binary'
    $apk=Join-Path $c.fixture 'downloaded.apk'
    [IO.File]::WriteAllText($apk,'Qualified artifact fixture')
    [IO.File]::WriteAllText((Join-Path $c.fixture 'authenticated-RentzLab-0.11.0.apk'),'Qualified GitHub artifact fixture')
    Require ((Plan $c).status-ceq'REGENERABLE') 'Owned non-APK test bytes were classified as release binaries'
    [IO.File]::WriteAllText($apk,'Different artifact fixture')
    Require ((Plan $c).status-ceq'REGENERABLE') 'The negative control leaves known non-APK bytes'
    [IO.File]::WriteAllText($apk,'Qualified artifact fixture altered')
    $p=Plan $c
    Require ($p.status-ceq'KEEP'-and$p.reasons-contains'retained-output') 'Changed mock bytes were accepted'
}
Case 'Changed bytes block stale plans' {
    $c=Fixture 'drift';$plan=Plan $c
    'rewritten'|Set-Content "$($c.fixture)/fixture.txt"
    try{Invoke-RentzRuntimeRetention -Plan $plan -RunRoot "$($c.data)/validation/apply" -Activity $quiet -VerifyEvidence {$true};throw 'Accepted stale plan'}
    catch{Require ($_.Exception.Message-match'drift') $_.Exception.Message}
    Require ((Test-Path $c.fixture)-and(Test-Path $c.cache)) 'A stale plan moved live data'
}
Case 'Evidence created after review blocks the entire apply' {
    $c=Fixture 'new-evidence';$p=Plan $c
    @{componentRoot=$c.fixture}|ConvertTo-Json|Set-Content "$($c.evidence)/new-receipt.json"
    try{Invoke-RentzRuntimeRetention -Plan $p -RunRoot "$($c.data)/validation/apply" -Activity $quiet -VerifyEvidence {$true};throw 'Accepted stale plan'}
    catch{Require ($_.Exception.Message-match'drift.*evidence') $_.Exception.Message}
    Require ((Test-Path $c.product)-and(Test-Path $c.cache)) 'A newly referenced group was moved'
}
Case 'Verification is performed after moves and a failure restores original paths' {
    $c=Fixture 'rollback';$p=Plan $c
    try{Invoke-RentzRuntimeRetention -Plan $p -RunRoot "$($c.data)/validation/apply" -Activity $quiet -VerifyEvidence {
        Require (!(Test-Path $c.fixture)-and!(Test-Path $c.cache)) 'Evidence check ran before moves'
        $false
    };throw 'Accepted failed proof'}catch{Require ($_.Exception.Message-match'evidence') $_.Exception.Message}
    Require ((Test-Path $c.fixture)-and(Test-Path $c.product)-and(Test-Path $c.cache)) 'Failed proof did not restore source'
    $receipt=Get-Content -Raw "$($c.data)/validation/apply/result.json"|ConvertFrom-Json
    Require ($receipt.status-ceq'ROLLED_BACK') 'Missing rollback result'
}
Case 'Success quarantines the exact reviewed paths and retains external proof' {
    $c=Fixture 'success'
    'retained original'|Set-Content "$($c.evidence)/extent.html"
    $expected=(Get-FileHash "$($c.evidence)/extent.html").Hash
    $result=Invoke-RentzRuntimeRetention -Plan (Plan $c) -RunRoot "$($c.data)/validation/apply" -Activity $quiet -VerifyEvidence {
        !(Test-Path $c.fixture)-and!(Test-Path $c.cache)-and((Get-FileHash "$($c.evidence)/extent.html").Hash-ceq$expected)
    }
    Require ($result.status-ceq'VERIFIED'-and$result.paths.Count-eq3) 'Missing verified quarantine receipt'
    Require ((Test-Path $c.receipt)-and(Test-Path "$($c.evidence)/extent.html")) 'Original evidence was affected'
}
$checks|ConvertTo-Json -Depth 5|Set-Content -LiteralPath "$RunRoot/checks.json"
if(@($checks|Where-Object actual -CNE PASS).Count){exit 1}
