param([string]$Project,[string]$Output)
$ErrorActionPreference='Stop'
Import-Module "$Project/rentz-reference/tools/RentzAndroid.psm1" -Force
if(Test-Path "$Project/rentz-reference/tools/RentzSandbox.psm1"){Import-Module "$Project/rentz-reference/tools/RentzSandbox.psm1" -Force}
Add-Type -AssemblyName System.Formats.Tar
$null=New-Item -ItemType Directory -Force $Output
$results=[Collections.Generic.List[object]]::new()
function Check($value,$message){if(!$value){throw $message}}
function Case($name,[scriptblock]$body){try{& $body;$results.Add(@{name=$name;status='PASS'})}catch{$results.Add(@{name=$name;status='FAIL';actual=$_.Exception.Message})}}
function TarBytes([string]$Name='files/session.json',[int]$Uid=10001,[string]$Data='original'){
 $memory=[IO.MemoryStream]::new();$writer=[System.Formats.Tar.TarWriter]::new($memory,$true)
 try{$entry=[System.Formats.Tar.PaxTarEntry]::new([System.Formats.Tar.TarEntryType]::RegularFile,$Name);$entry.Uid=$Uid;$entry.DataStream=[IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($Data));$writer.WriteEntry($entry)}finally{$writer.Dispose()}
 return ,$memory.ToArray()
}
function Context($name,[bool]$present=$true,[string]$member='files/session.json'){
 $root=Join-Path $Output $name;$null=New-Item -ItemType Directory $root
 $run=Join-Path $root 'run';$other=Join-Path $root 'other';$null=New-Item -ItemType Directory $run,$other
 $lease=Join-Path $root 'current.json';'{"active":false}'|Set-Content $lease
 $state=[pscustomobject]@{apk=$(if($present){[Text.Encoding]::UTF8.GetBytes(('APK-original'*300))}else{$null});data=(TarBytes $member);push=$null;commands=[Collections.Generic.List[string]]::new();ordinal=0;wrongRestore=$false;differentMetadata=$false;failUninstall=$false}
 $port={param($p)
  $state.ordinal++;$commandArgs=@($p.Arguments);$key=$commandArgs-join' ';$state.commands.Add($key)
  $file=Join-Path $run ("response-$($state.ordinal).bin");$out='';$exit=0
  if($key-eq'shell pm path ro.rentzlab.app.uisanity'){if($state.apk){$out='package:/data/app/offline/base.apk'}else{$exit=1}}
  elseif($key-eq'exec-out cat /data/app/offline/base.apk'){[IO.File]::WriteAllBytes($file,$state.apk)}
  elseif($key-like'exec-out run-as ro.rentzlab.app.uisanity tar -c *'){[IO.File]::WriteAllBytes($file,$state.data)}
  elseif($commandArgs[0]-eq'uninstall'){$state.apk=$null;if($state.failUninstall){throw 'Controlled response loss after uninstall'}}
  elseif($commandArgs[0]-eq'install'){$state.apk=[IO.File]::ReadAllBytes($commandArgs[-1])}
  elseif($commandArgs[0]-eq'push'){$state.push=[IO.File]::ReadAllBytes($commandArgs[1])}
  elseif($key-like'shell run-as ro.rentzlab.app.uisanity tar -xof *'){$state.data=if($state.wrongRestore){TarBytes -Data wrong}elseif($state.differentMetadata){TarBytes -Uid 20002}else{$state.push}}
  [pscustomobject]@{Output=$out;ExitCode=$exit;File=$file}
 }.GetNewClosure()
 Start-RentzAndroidLease -Path $lease -RunRoot $run -Owner 'offline test' -Serial 'offline-fixture'|Out-Null
 [pscustomobject]@{root=$root;run=$run;other=$other;lease=$lease;state=$state;port=$port}
}
function Save($c){Save-RentzTestSandbox -RunRoot $c.run -LeasePath $c.lease -Command $c.port}
function Restore($c){Restore-RentzTestSandbox -RunRoot $c.run -LeasePath $c.lease -TestApkSha256 ('f'*64) -Command $c.port}
Case 'one-owner-across-stages' {
 $c=Context 'stages';$id=(Get-Content "$($c.run)/lease.json" -Raw|ConvertFrom-Json).leaseId
 foreach($phase in @('PREPARE','TEST','RESTORE')){
  Set-RentzAndroidLeasePhase -Path $c.lease -RunRoot $c.run -Phase $phase|Out-Null
  $denied=$false;try{Start-RentzAndroidLease -Path $c.lease -RunRoot $c.other -Owner other -Serial offline-fixture|Out-Null}catch{$denied=$true}
  Check $denied "Another owner entered during $phase"; $current=Get-Content $c.lease -Raw|ConvertFrom-Json
  Check ($current.active-and$current.leaseId-ceq$id) 'Phase handoff exposed an inactive or foreign reservation'
 }
 Check (Close-RentzAndroidLease -Path $c.lease -LeaseId $id -Serial offline-fixture) 'Own session did not close'
}
Case 'foreign-phase-has-no-write' {
 $c=Context 'foreign-phase';$before=[IO.File]::ReadAllText($c.lease);'{"leaseId":"foreign","serial":"offline-fixture"}'|Set-Content "$($c.other)/lease.json"
 $denied=$false;try{Set-RentzAndroidLeasePhase -Path $c.lease -RunRoot $c.other -Phase TEST|Out-Null}catch{$denied=$true}
 Check ($denied-and[IO.File]::ReadAllText($c.lease)-ceq$before) 'A foreign run changed ownership'
}
Case 'existing-package-restored' {
 $c=Context 'existing';$apk=[Convert]::ToHexString($c.state.apk);Save $c|Out-Null
 Check (!$c.state.apk) 'Original was not removed after its verified backup';Restore $c|Out-Null
 Check ([Convert]::ToHexString($c.state.apk)-ceq$apk) 'Original APK changed'
 $proof=Get-Content "$($c.run)/sandbox-backup/restored.json" -Raw|ConvertFrom-Json
 Check ($proof.status-ceq'PASS'-and$proof.files.Count-eq1) 'Original regular-file bytes were not verified'
 Check (@($c.state.commands|Where-Object{$_-match'^(uninstall|shell am force-stop) ro.rentzlab.app$'}).Count-eq0) 'Product package was touched'
}
Case 'absent-package-stays-absent' {
 $c=Context 'absent' $false;Save $c|Out-Null;Restore $c|Out-Null
 Check (!$c.state.apk-and@($c.state.commands|Where-Object{$_-like'uninstall *'}).Count-eq0) 'Absent package caused destructive work'
}
Case 'unsafe-tar-before-uninstall' {
 $c=Context 'unsafe' $true '../escape.json';$denied=$false;try{Save $c|Out-Null}catch{$denied=$true}
 Check ($denied-and$c.state.apk-and@($c.state.commands|Where-Object{$_-like'uninstall *'}).Count-eq0) 'Unsafe TAR was used destructively'
}
Case 'corrupt-backup-keeps-lease' {
 $c=Context 'corrupt';Save $c|Out-Null;[IO.File]::WriteAllText("$($c.run)/sandbox-backup/original-data.tar",'corrupt')
 $count=$c.state.commands.Count;$denied=$false;try{Restore $c|Out-Null}catch{$denied=$true}
 Check ($denied-and$c.state.commands.Count-eq$count-and(Get-Content $c.lease -Raw|ConvertFrom-Json).active) 'Corrupt backup triggered a command or released ownership'
}
Case 'foreign-lease-before-restore' {
 $c=Context 'foreign-restore';Save $c|Out-Null;'{"active":true,"leaseId":"foreign","serial":"offline-fixture"}'|Set-Content $c.lease
 $count=$c.state.commands.Count;$denied=$false;try{Restore $c|Out-Null}catch{$denied=$true}
 Check ($denied-and$c.state.commands.Count-eq$count) 'Restore sent a command under another owner'
}
Case 'foreign-installed-apk-preserved' {
 $c=Context 'foreign-apk';Save $c|Out-Null;$c.state.apk=[Text.Encoding]::UTF8.GetBytes('FOREIGN')
 $denied=$false;try{Restore $c|Out-Null}catch{$denied=$true}
 Check ($denied-and[Text.Encoding]::UTF8.GetString($c.state.apk)-ceq'FOREIGN') 'Unknown package was overwritten'
}
Case 'wrong-restored-file-refused' {
 $c=Context 'wrong-restored';Save $c|Out-Null;$c.state.wrongRestore=$true;$denied=$false
 try{Restore $c|Out-Null}catch{$denied=$true}
 Check ($denied-and(Get-Content $c.lease -Raw|ConvertFrom-Json).active-and!(Test-Path "$($c.run)/sandbox-backup/restored.json")) 'Incorrect restored bytes were certified'
}
Case 'changed-archive-metadata-preserves-content' {
 $c=Context 'metadata';Save $c|Out-Null;$c.state.differentMetadata=$true;Restore $c|Out-Null
 Check (Test-Path "$($c.run)/sandbox-backup/restored.json") 'UID/timestamps were incorrectly treated as file corruption'
}
Case 'known-test-apk-is-removed-before-restore' {
 $c=Context 'known-test';Save $c|Out-Null;$c.state.apk=[Text.Encoding]::UTF8.GetBytes(('known-test'*300))
 $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($c.state.apk)).ToLowerInvariant()
 Restore-RentzTestSandbox -RunRoot $c.run -LeasePath $c.lease -TestApkSha256 $hash -Command $c.port|Out-Null
 Check ([Text.Encoding]::UTF8.GetString($c.state.apk)-ceq('APK-original'*300)) 'Verified test APK was not replaced by the preserved APK'
}
Case 'lost-uninstall-response-is-recoverable' {
 $c=Context 'lost-uninstall';$c.state.failUninstall=$true;$failed=$false;try{Save $c|Out-Null}catch{$failed=$true}
 Check ($failed-and(Test-Path "$($c.run)/sandbox-backup/identity.json")) 'Recovery identity was not durable before uninstall'
 $c.state.failUninstall=$false;Restore $c|Out-Null
 Check (Test-Path "$($c.run)/sandbox-backup/restored.json") 'Ambiguous uninstall did not restore from the original backup'
}
Case 'phase-cannot-move-backwards' {
 $c=Context 'backwards';Set-RentzAndroidLeasePhase -Path $c.lease -RunRoot $c.run -Phase RESTORE|Out-Null
 $before=[IO.File]::ReadAllText($c.lease);$denied=$false;try{Set-RentzAndroidLeasePhase -Path $c.lease -RunRoot $c.run -Phase TEST|Out-Null}catch{$denied=$true}
 Check ($denied-and[IO.File]::ReadAllText($c.lease)-ceq$before) 'A completed phase was reopened'
}
Case 'empty-regular-file-roundtrip' {
 $c=Context 'empty-file' $true 'files/empty';$c.state.data=TarBytes -Name 'files/empty' -Data ''
 Save $c|Out-Null;Restore $c|Out-Null
 $identity=Get-Content "$($c.run)/sandbox-backup/identity.json" -Raw|ConvertFrom-Json
 Check ($identity.files.Count-eq1-and$identity.files[0].bytes-eq0-and
  $identity.files[0].sha256-ceq'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855') 'Empty regular file was lost or hashed as missing'
}
$results|ConvertTo-Json -Depth 5|Set-Content "$Output/results.json" -Encoding utf8NoBOM
