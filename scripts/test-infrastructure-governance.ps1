param([Parameter(Mandatory)][string]$RunRoot,[ValidateSet('RED','GREEN')][string]$Phase='GREEN')
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
$policy=Get-AtfWorkspacePolicy -ProjectRoot $project
$data=Get-AtfDataRoot -ProjectRoot $project
$allowed=Get-AtfInfrastructurePath -Area governance -ProjectRoot $project
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
if(-not$RunRoot.StartsWith($allowed+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or(Test-Path -LiteralPath $RunRoot)){throw 'A fresh run below the registered infrastructure/governance root is required.'}
Assert-AtfPhysicalPath -Path $RunRoot
New-Item -ItemType Directory -Path $RunRoot -Force|Out-Null
$findings=@(Get-AtfWorkspaceFindings -ProjectRoot $project -DataRoot $data -SystemTempRoot ([IO.Path]::GetTempPath()) -Policy $policy)
[ordered]@{phase=$Phase;status=$(if($findings.Count){'FAIL'}else{'PASS'});mode='LIVE_READ_ONLY';registrySha256=(Get-FileHash -LiteralPath (Join-Path $project 'config/infrastructure-workspaces.json')).Hash;scopes=@($project,(Split-Path -Parent $project),$data);systemTempScope=@{root=[IO.Path]::GetTempPath();prefixes=$policy.forbiddenSystemTempPrefixes};findings=$findings}|ConvertTo-Json -Depth 7|Set-Content -LiteralPath (Join-Path $RunRoot 'governance.json') -Encoding utf8NoBOM
Write-Output ("{0}: {1} findings"-f$(if($findings.Count){'FAIL'}else{'PASS'}),$findings.Count)
foreach($finding in $findings){Write-Output ($finding.code+' '+$finding.path)}
if($findings.Count){exit 1}
exit 0
