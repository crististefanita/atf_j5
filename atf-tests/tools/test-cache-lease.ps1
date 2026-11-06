param([string]$Output='')
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot '../../scripts/AtfMavenCache.psm1') -Force
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('rentz-cache-lease-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture|Out-Null
$first=Enter-AtfJ5CacheLease -Repository $fixture
$blocked=$false
try {try{$unexpected=Enter-AtfJ5CacheLease -Repository $fixture;$unexpected.Dispose()}catch{$blocked=$true}}finally{$first.Dispose()}
if(-not$blocked){throw 'Concurrent cache mutation was not refused.'}
$second=Enter-AtfJ5CacheLease -Repository $fixture
$second.Dispose()
$result=[ordered]@{status='PASS';concurrentAccessRefused=$blocked;availableAfterRelease=$true;fixture=$fixture}
if($Output){$result|ConvertTo-Json|Set-Content -LiteralPath $Output -Encoding utf8NoBOM}
$result|ConvertTo-Json
