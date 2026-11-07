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
function Launch($c){
    $failed=$false
    try{& (Join-Path $project 'atf-tests/tools/run-arena.ps1') -ComponentRun $c.component -Protocol "$($c.root)/protocol.json" -RunRoot $c.run -Registry (Join-Path $product 'registry.json') -Java $c.spy *> "$($c.root)/launcher.log"}catch{$failed=$true;$_.Exception.Message|Add-Content "$($c.root)/launcher.log"}
    return [pscustomobject]@{failed=$failed;invoked=(Test-Path -LiteralPath "$($c.root)/invoked.txt")}
}
Case 'Unchanged qualified runtime reaches only the injected recorder' {
    $c=Context valid;$r=Launch $c
    Require (-not$r.failed-and$r.invoked) 'Valid neighboring runtime was rejected'
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
if(-not$NativePublicationScript){$NativePublicationScript=Join-Path $project 'atf-tests/tools/run-android-sanity.ps1'}
$publicationTokens=$null;$publicationErrors=$null
$nativeAst=[Management.Automation.Language.Parser]::ParseFile($NativePublicationScript,[ref]$publicationTokens,[ref]$publicationErrors)
if($publicationErrors){throw 'Native publication source has invalid syntax'}
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
$failed=@($results|Where-Object status -EQ FAIL)
[ordered]@{status=$(if($failed.Count){'FAIL'}else{'PASS'});tests=$results.Count;failed=$failed.Count;fixture=$fixture;productFixtures=$product;scope='Byte fixtures, injected Java recorder and extracted publication block; no real JVM body, game, full native launcher or device';results=$results}|ConvertTo-Json -Depth 7|Set-Content "$RunRoot/controls.json" -Encoding utf8NoBOM
$results|Format-Table name,status -AutoSize
if($failed.Count){throw 'Qualified runtime controls failed'}
