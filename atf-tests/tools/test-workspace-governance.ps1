param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
$allowed=Get-AtfInfrastructurePath -Area governance -ProjectRoot $project
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
if(-not$RunRoot.StartsWith($allowed+'\',[StringComparison]::OrdinalIgnoreCase)-or(Test-Path -LiteralPath $RunRoot)){throw 'Fresh registered governance run required.'}
New-Item -ItemType Directory -Path $RunRoot -Force|Out-Null
$fixture=New-AtfWorkspace -Kind fixture -ProjectRoot $project
$results=[Collections.Generic.List[object]]::new()
function Case([string]$Name,[scriptblock]$Body){try{& $Body;$results.Add([pscustomobject]@{name=$Name;status='PASS'})}catch{$results.Add([pscustomobject]@{name=$Name;status='FAIL';error=$_.Exception.Message})}}
function Require([bool]$Value,[string]$Reason){if(-not$Value){throw $Reason}}
function Setup([string]$Name){
    $base=Join-Path $fixture $Name;$p=Join-Path $base 'group/atf_j5';$d=Join-Path $base 'data';$t=Join-Path $base 'system-area'
    New-Item -ItemType Directory -Force -Path "$p/config","$p/docs/rentz",$d,$t|Out-Null
    $policy=Get-Content -Raw -LiteralPath (Join-Path $project 'config/infrastructure-workspaces.json')|ConvertFrom-Json
    $policy.producerScripts=@();$policy.reviewedTempReaders=@();$policy|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$p/config/infrastructure-workspaces.json" -Encoding utf8NoBOM
    '# Contract de fixture'|Set-Content -LiteralPath "$p/docs/rentz/ATF_Infrastructure.md" -Encoding utf8NoBOM
    return @{project=$p;data=$d;system=$t;policy=$policy}
}
function Findings($case){return @(Get-AtfWorkspaceFindings -ProjectRoot $case.project -DataRoot $case.data -SystemTempRoot $case.system -Policy $case.policy)}
Case 'Registered source and artifact directories pass' {$c=Setup 'registered';New-Item -ItemType Directory -Path "$($c.data)/learning"|Out-Null;Require ((Findings $c).Count-eq0) 'Valid registry rejected'}
Case 'Unknown source directory fails' {$c=Setup 'source';New-Item -ItemType Directory -Path "$($c.project)/scratch-unregistered"|Out-Null;Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-DIRECTORY') 'Unknown source directory accepted'}
Case 'Unknown project beside ATF projects fails' {$c=Setup 'project';New-Item -ItemType Directory -Path (Join-Path (Split-Path $c.project) 'random-project')|Out-Null;Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-DIRECTORY') 'Unknown checkout accepted'}
Case 'Unknown artifact collection fails' {$c=Setup 'artifact';New-Item -ItemType Directory -Path "$($c.data)/misc-random"|Out-Null;Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-DIRECTORY') 'Unknown output collection accepted'}
Case 'Future legacy compile name is rejected while unrelated OS folders are ignored' {$c=Setup 'legacy';New-Item -ItemType Directory -Path "$($c.system)/other-application","$($c.system)/whist-screen-compile-FutureSuffix"|Out-Null;$f=Findings $c;Require ($f.Count-eq1-and$f[0].code-eq'INFRA-FORBIDDEN-TEMP-WORKSPACE') 'Legacy scope is too broad or only matches the original name'}
Case 'An owner-looking name without descriptor fails' {$c=Setup 'missing';New-Item -ItemType Directory -Path "$($c.data)/infrastructure/workspaces/components-01234567890123456789012345678901" -Force|Out-Null;Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-WORKSPACE') 'Workspace name incorrectly treated as proof'}
Case 'Valid workspace descriptor passes and wrong owner fails' {
    $c=Setup 'descriptor';$id='01234567890123456789012345678901';$w="$($c.data)/infrastructure/workspaces/components-$id";New-Item -ItemType Directory -Path $w -Force|Out-Null
    $alternateRoot=Join-Path $fixture 'alternate-checkout/atf_j5';New-Item -ItemType Directory -Path $alternateRoot -Force|Out-Null
    $record=[ordered]@{schemaVersion=1;owner='atf_j5';kind='components';id=$id;sourceRoot=$alternateRoot};$record|ConvertTo-Json|Set-Content -LiteralPath "$w/.atf-workspace.json" -Encoding utf8NoBOM
    Require ((Findings $c).Count-eq0) 'A valid same-project worktree was rejected';$record.owner='unrelated';$record|ConvertTo-Json|Set-Content -LiteralPath "$w/.atf-workspace.json" -Encoding utf8NoBOM
    Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-WORKSPACE') 'Spoofed owner accepted'
}
Case 'Malformed descriptor fails closed' {$c=Setup 'malformed';$w="$($c.data)/infrastructure/workspaces/bad";New-Item -ItemType Directory -Force -Path $w|Out-Null;'{'|Set-Content -LiteralPath "$w/.atf-workspace.json";Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-WORKSPACE') 'Malformed descriptor accepted'}
Case 'Direct system temporary producer is detected from syntax' {$c=Setup 'producer';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;'$stage=[IO.Path]::GetTempPath()'|Set-Content -LiteralPath "$($c.project)/scripts/bad.ps1";$c.policy.producerScripts=@('scripts/bad.ps1');Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'Direct allocation accepted'}
Case 'A comment mentioning the old API is not executable allocation' {$c=Setup 'comment';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;'# [IO.Path]::GetTempPath()'|Set-Content -LiteralPath "$($c.project)/scripts/comment.ps1";$c.policy.producerScripts=@('scripts/comment.ps1');Require ((Findings $c).Count-eq0) 'Comment produced a false positive'}
Case 'Environment TEMP bypass is rejected' {$c=Setup 'environment';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;'$stage=Join-Path $env:TEMP "unregistered"'|Set-Content -LiteralPath "$($c.project)/scripts/bad.ps1";$c.policy.producerScripts=@('scripts/bad.ps1');Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'Environment bypass accepted'}
Case 'A newly named producer cannot hide outside the declared list' {$c=Setup 'new-producer';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;'$stage=[IO.Path]::GetTempPath()'|Set-Content -LiteralPath "$($c.project)/scripts/new-producer.ps1";Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-SCRIPT') 'New producer was not discovered'}
Case 'Even a harmless new script needs an explicit role' {$c=Setup 'new-harmless';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;'Write-Output "healthy"'|Set-Content -LiteralPath "$($c.project)/scripts/healthy.ps1";Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-SCRIPT') 'Undeclared script was silently accepted'}
Case 'Script discovery includes source modules outside the scripts directory' {$c=Setup 'module-script';New-Item -ItemType Directory -Force -Path "$($c.project)/rentz-lab-app/tools"|Out-Null;'Write-Output "healthy"'|Set-Content -LiteralPath "$($c.project)/rentz-lab-app/tools/new.ps1";Require (((Findings $c).code)-contains'INFRA-UNREGISTERED-SCRIPT') 'Module script was not discovered'}
Case 'Environment API TEMP and TMP bypasses are rejected' {
    foreach($name in @('TEMP','tmp')){$c=Setup ('environment-api-'+$name);New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;('$stage=[Environment]::GetEnvironmentVariable('''+$name+''')')|Set-Content -LiteralPath "$($c.project)/scripts/bad.ps1";$c.policy.producerScripts=@('scripts/bad.ps1');Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'Environment API bypass accepted'}
}
Case 'Unrelated environment settings and string examples remain valid' {$c=Setup 'unrelated-env';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;'$root=[Environment]::GetEnvironmentVariable(''RENTZ_DATA_ROOT''); $example="[Environment]::GetEnvironmentVariable(''TEMP'')"'|Set-Content -LiteralPath "$($c.project)/scripts/healthy.ps1";$c.policy.producerScripts=@('scripts/healthy.ps1');Require ((Findings $c).Count-eq0) 'Non-temporary setting or example was rejected'}
Case 'Computed environment names and bulk environment access fail closed' {
    foreach($code in @('$name="TEMP"; [Environment]::GetEnvironmentVariable($name)','[Environment]::GetEnvironmentVariables()')){$c=Setup ([guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;$code|Set-Content -LiteralPath "$($c.project)/scripts/bad.ps1";$c.policy.producerScripts=@('scripts/bad.ps1');Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'Unproven environment access was accepted'}
}
Case 'A constant non-temporary environment loop passes while temporary or reassigned names fail' {
    $c=Setup 'bounded-environment';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null;$file="$($c.project)/scripts/producer.ps1";$c.policy.producerScripts=@('scripts/producer.ps1')
    'foreach($key in @(''JAVA_HOME'',''PATH'')){ $saved[$key]=[Environment]::GetEnvironmentVariable($key) }'|Set-Content -LiteralPath $file
    Require ((Findings $c).Count-eq0) 'A constant unrelated environment list was rejected'
    'foreach($key in @(''JAVA_HOME'',''TEMP'')){ [Environment]::GetEnvironmentVariable($key) }'|Set-Content -LiteralPath $file
    Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'A temporary name hidden in a loop was accepted'
    'foreach($key in @(''JAVA_HOME'')){ $key=''TEMP''; [Environment]::GetEnvironmentVariable($key) }'|Set-Content -LiteralPath $file
    Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'A reassigned environment name was accepted'
}
Case 'Reviewed temporary readers are limited to the named function' {
    $c=Setup 'scoped-reader';New-Item -ItemType Directory -Path "$($c.project)/scripts"|Out-Null
    $file="$($c.project)/scripts/reader.psm1";'function Inspect-Boundary { [IO.Path]::GetTempPath() }'|Set-Content -LiteralPath $file
    $c.policy.reviewedTempReaders=@([pscustomobject]@{path='scripts/reader.psm1';role='allocator';functions=@('Inspect-Boundary');reason='Read the forbidden boundary without allocating there'})
    Require ((Findings $c).Count-eq0) 'Reviewed boundary read was rejected'
    'function New-Unreviewed { [IO.Path]::GetTempPath() }'|Add-Content -LiteralPath $file
    Require (((Findings $c).code)-contains'INFRA-DIRECT-TEMP-PRODUCER') 'A new function inherited the reader exemption'
}
Case 'Reader entries need a reason and cannot replace producer identities' {
    $c=Setup 'reader-policy';$c.policy.reviewedTempReaders=@([pscustomobject]@{path='scripts/reader.psm1';role='allocator';functions=@('<script>');reason=''})
    $c.policy|ConvertTo-Json -Depth 8|Set-Content -LiteralPath "$($c.project)/config/infrastructure-workspaces.json"
    try{Get-AtfWorkspacePolicy -ProjectRoot $c.project|Out-Null;throw 'Missing reader reason accepted'}catch{Require ($_.Exception.Message-match'explicit role') 'Wrong reader policy failure'}
    $c.policy.reviewedTempReaders[0].reason='Review boundary';$c.policy.producerScripts=@('scripts/reader.psm1');$c.policy|ConvertTo-Json -Depth 8|Set-Content -LiteralPath "$($c.project)/config/infrastructure-workspaces.json"
    try{Get-AtfWorkspacePolicy -ProjectRoot $c.project|Out-Null;throw 'Producer identity was exempted'}catch{Require ($_.Exception.Message-match'distinct') 'Wrong overlapping identity failure'}
}
Case 'Discovery refuses a nested junction without following its scripts' {
    $c=Setup 'nested-script-link';$outside=Join-Path $fixture 'nested-link-target';New-Item -ItemType Directory -Path $outside,"$($c.project)/rentz-lab-app/tools" -Force|Out-Null
    '[IO.Path]::GetTempPath()'|Set-Content -LiteralPath (Join-Path $outside 'hidden.ps1')
    $link=Join-Path $c.project 'rentz-lab-app/tools/linked';New-Item -ItemType Junction -Path $link -Value $outside|Out-Null
    Require (((Findings $c).code)-contains'INFRA-REPARSE-POINT') 'Nested linked scripts escaped inventory'
    Require ((Get-Content -Raw -LiteralPath (Join-Path $outside 'hidden.ps1'))-match'GetTempPath') 'The linked destination was modified'
}
Case 'Duplicate registry names are rejected' {$c=Setup 'duplicate';$c.policy.sourceDirectories+=@('scripts');$c.policy|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$($c.project)/config/infrastructure-workspaces.json";try{Get-AtfWorkspacePolicy -ProjectRoot $c.project|Out-Null;throw 'Duplicate accepted'}catch{Require ($_.Exception.Message-match'Invalid or duplicate') 'Wrong duplicate failure'}}
Case 'Allocator rejects traversal kinds' {try{New-AtfWorkspace -Kind '../escape' -ProjectRoot $project|Out-Null;throw 'Traversal accepted'}catch{Require ($_.Exception.Message-match'not registered') 'Wrong traversal failure'}}
Case 'Allocator uses owned location and matching identity' {$w=New-AtfWorkspace -Kind governance -ProjectRoot $project;$record=Get-Content -Raw -LiteralPath (Join-Path $w '.atf-workspace.json')|ConvertFrom-Json;Require ($w.StartsWith((Get-AtfInfrastructurePath -Area workspaces -ProjectRoot $project)+'\',[StringComparison]::OrdinalIgnoreCase)-and$record.owner-eq'atf_j5'-and(Split-Path $w -Leaf)-eq($record.kind+'-'+$record.id)) 'Allocator escaped declared root'}
Case 'Registered candidate uses its declared checkout layout for cross-project links' {$layout=Get-AtfCheckoutLayoutRoot -ProjectRoot $project;$descriptorPath=Join-Path (Split-Path -Parent $project) '.atf-workspace.json';$expected=$project;if(Test-Path -LiteralPath $descriptorPath){$descriptor=Get-Content -Raw -LiteralPath $descriptorPath|ConvertFrom-Json;$expected=[IO.Path]::GetFullPath([string]$descriptor.sourceRoot)};Require ($layout-ceq$expected) 'Candidate link checks ignored the registered checkout layout'}
Case 'POM-selected reporting artifact and provenance resolve by exact version' {$actual=Get-AtfReportingLibrary -ProjectRoot $project;[xml]$pom=Get-Content -Raw (Join-Path $project 'pom.xml');$expected='atf-reporting-'+$pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='properties']/*[local-name()='atf.reporting.version']").InnerText.Trim();Require ($actual.name-ceq$expected-and(Test-Path -LiteralPath $actual.jar)-and(Test-Path -LiteralPath $actual.pom)-and$actual.sha256-eq(Get-FileHash -LiteralPath $actual.jar -Algorithm SHA256).Hash.ToLowerInvariant()) 'Reporting binary identity is not tied to the root Maven property'}
Case 'A future reporting patch selects its own artifact instead of a pinned JAR' {$fixture=Join-Path $RunRoot 'reporting-version-fixture';New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'lib')|Out-Null;'<project><properties><atf.reporting.version>1.0.0-j99</atf.reporting.version></properties></project>'|Set-Content -LiteralPath (Join-Path $fixture 'pom.xml') -Encoding utf8NoBOM;'fixture-jar'|Set-Content -LiteralPath (Join-Path $fixture 'lib/atf-reporting-1.0.0-j99.jar') -Encoding utf8NoBOM;'fixture-pom'|Set-Content -LiteralPath (Join-Path $fixture 'lib/atf-reporting-1.0.0-j99.pom') -Encoding utf8NoBOM;$actual=Get-AtfReportingLibrary -ProjectRoot $fixture;Require ($actual.name-ceq'atf-reporting-1.0.0-j99') 'A future POM version resolved a pinned artifact'}
Case 'Data root cannot point inside operating-system Temp' {$saved=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT');try{[Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',(Join-Path ([IO.Path]::GetTempPath()) 'atf-bypass'));try{Get-AtfDataRoot -ProjectRoot $project|Out-Null;throw 'Temporary data root accepted'}catch{Require ($_.Exception.Message-match'must be outside') 'Wrong data-root failure'}}finally{[Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$saved)}}
Case 'Registry paths cannot escape through parent segments' {$c=Setup 'escape';$c.policy.producerScripts=@('../outside.ps1');$c.policy|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$($c.project)/config/infrastructure-workspaces.json";try{Get-AtfWorkspacePolicy -ProjectRoot $c.project|Out-Null;throw 'Escape accepted'}catch{Require ($_.Exception.Message-match'must stay inside') 'Wrong escape failure'}}
Case 'Unknown registry properties fail closed' {$c=Setup 'unknown';$c.policy|Add-Member -NotePropertyName allowEverything -NotePropertyValue $true;$c.policy|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$($c.project)/config/infrastructure-workspaces.json";try{Get-AtfWorkspacePolicy -ProjectRoot $c.project|Out-Null;throw 'Unknown field accepted'}catch{Require ($_.Exception.Message-match'Unknown or missing') 'Wrong unknown-field failure'}}
Case 'Duplicate JSON properties are rejected' {$c=Setup 'duplicate-property';$file="$($c.project)/config/infrastructure-workspaces.json";$raw=[IO.File]::ReadAllText($file);[IO.File]::WriteAllText($file,$raw.Replace('"schemaVersion": 1','"schemaVersion": 1, "schemaVersion": 1'));try{Get-AtfWorkspacePolicy -ProjectRoot $c.project|Out-Null;throw 'Duplicate property accepted'}catch{Require ($_.Exception.Message-match'Duplicate registry properties') 'Wrong duplicate-property failure'}}
Case 'Runtime output cannot escape into an unregistered collection' {try{Assert-AtfArtifactPath -Path (Join-Path (Get-AtfDataRoot -ProjectRoot $project) 'random-unregistered/run') -ProjectRoot $project;throw 'Unknown collection accepted'}catch{Require ($_.Exception.Message-match'not registered') 'Wrong collection failure'}}
Case 'Runtime output cannot target system Temp' {try{Assert-AtfArtifactPath -Path (Join-Path ([IO.Path]::GetTempPath()) 'random-output') -ProjectRoot $project;throw 'Output escape accepted'}catch{Require ($_.Exception.Message-match'outside the registered data root') 'Wrong output-boundary failure'}}
Case 'Junction escape is detected without following its destination' {$c=Setup 'junction';$outside=Join-Path $fixture 'junction-destination';New-Item -ItemType Directory -Path $outside|Out-Null;$link=Join-Path $c.project 'linked-output';New-Item -ItemType Junction -Path $link -Value $outside|Out-Null;Require (((Findings $c).code)-contains'INFRA-REPARSE-POINT') 'Junction was not diagnosed';try{Assert-AtfPhysicalPath -Path (Join-Path $link 'new-output');throw 'Junction accepted'}catch{Require ($_.Exception.Message-match'reparse point') 'Wrong junction failure'};Require (@(Get-ChildItem -LiteralPath $outside -Force).Count-eq0) 'Junction destination was modified'}
Case 'The execution gate stops on an undocumented directory' {$c=Setup 'gate';New-Item -ItemType Directory -Path "$($c.project)/unregistered"|Out-Null;$saved=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT');try{[Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$c.data);try{Assert-AtfInfrastructure -ProjectRoot $c.project;throw 'Execution gate accepted invalid source'}catch{Require ($_.Exception.Message-match'Infrastructure governance refused execution') 'Wrong execution-gate failure'}}finally{[Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$saved)};Require (-not(Test-Path -LiteralPath "$($c.data)/infrastructure/workspaces")) 'The gate allocated output before refusing'}
$failed=@($results|Where-Object{$_.status-ne'PASS'})
[ordered]@{status=$(if($failed.Count){'FAIL'}else{'PASS'});tests=$results.Count;failed=$failed.Count;fixture=$fixture;results=$results}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $RunRoot 'controls.json') -Encoding utf8NoBOM
Write-Output ("Controls: {0}/{1} passed"-f($results.Count-$failed.Count),$results.Count)
if($failed.Count){$failed|ConvertTo-Json;exit 1}
exit 0
