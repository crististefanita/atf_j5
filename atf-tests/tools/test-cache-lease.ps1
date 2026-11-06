param([string]$Output='')
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot '../../scripts/AtfMavenCache.psm1') -Force
Import-Module (Join-Path $PSScriptRoot '../../scripts/InfrastructureWorkspaces.psm1') -Force
$fixture=New-AtfWorkspace -Kind fixture -ProjectRoot ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')))
$first=Enter-AtfJ5CacheLease -Repository $fixture
$blocked=$false
try {try{$unexpected=Enter-AtfJ5CacheLease -Repository $fixture;$unexpected.Dispose()}catch{$blocked=$true}}finally{$first.Dispose()}
if(-not$blocked){throw 'Concurrent cache mutation was not refused.'}
$second=Enter-AtfJ5CacheLease -Repository $fixture
$second.Dispose()
$result=[ordered]@{status='PASS';concurrentAccessRefused=$blocked;availableAfterRelease=$true;fixture=$fixture}
if($Output){$result|ConvertTo-Json|Set-Content -LiteralPath $Output -Encoding utf8NoBOM}
$result|ConvertTo-Json
