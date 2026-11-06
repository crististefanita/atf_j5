param(
    [Parameter(Mandatory)][string]$BuildRun,
    [Parameter(Mandatory)][string]$ComponentRun,
    [ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$RunName='native',
    [string]$PreviousBuildRun='',
    [string]$Java='C:\tools\Java21\jdk-21.0.12.1+1\bin\java.exe'
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
Assert-AtfArtifactPath -Path $BuildRun -ProjectRoot $project
$BuildRun=[IO.Path]::GetFullPath($BuildRun)
$run=Join-Path $BuildRun $RunName
if(Test-Path -LiteralPath $run){throw 'Fresh native run required.'}
New-Item -ItemType Directory -Path $run|Out-Null
$qualification=Get-Content -Raw -LiteralPath (Join-Path $BuildRun 'build-qualification.json')|ConvertFrom-Json
$apk=Join-Path $BuildRun $(if($qualification.apkFile){$qualification.apkFile}else{'rentz-lab-0.1.0-debug.apk'})
$apkHash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
if($qualification.status-ne'PASS'-or$qualification.apkSha256-ne$apkHash){throw 'APK identity and Android unit qualification required.'}
$classpath=(Get-Content -Raw -LiteralPath (Join-Path $BuildRun 'harness-classpath.txt')).Trim()
Import-Module (Join-Path $project 'rentz-reference/tools/RentzAndroid.psm1') -Force
Import-Module (Join-Path $project 'scripts/AtfMavenCache.psm1') -Force
$component=Get-Content -Raw -LiteralPath (Join-Path $ComponentRun 'result.json')|ConvertFrom-Json
if($component.status-ne'PASS'){throw 'Qualified offline component source required.'}
$cacheLease=Enter-AtfJ5CacheLease -Repository $component.repository
$classpathIdentity=@(foreach($entry in $classpath.Split(';')|Where-Object{$_-and$_.EndsWith('.jar')}){[pscustomobject]@{path=$entry;sha256=(Get-FileHash -LiteralPath $entry -Algorithm SHA256).Hash}})
ConvertTo-Json -InputObject $classpathIdentity -Depth 4|Set-Content -LiteralPath (Join-Path $run 'classpath-identity.json') -Encoding utf8NoBOM
Get-FileHash -LiteralPath $PSCommandPath,(Join-Path $PSScriptRoot 'RentzNativeFlow.java') -Algorithm SHA256|Select-Object Path,Hash|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $run 'adapter-identity.json') -Encoding utf8NoBOM
Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $run 'executed-runner.ps1')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'RentzNativeFlow.java') -Destination (Join-Path $run 'executed-adapter.java')
$leasePath='C:\work\whist_back\android-ui-lease.json'
$leaseId=[guid]::NewGuid().ToString('N')
$owned=$false;$released=$false;$ordinal=0;$checks=[Collections.Generic.List[object]]::new();$uiActions=[Collections.Generic.List[object]]::new();$initialActivity='';$wmSize='';$wmDensity='';$fontScale='';$finalSize='';$finalDensity='';$finalFont='';$api='';$lastAudit=$null;$failure='';$installedBefore=$false;$needsUpgrade=$false;$backupVerified=$false;$backupRestored=$false;$backupFile='';$backupHash=''
function Adb([string[]]$Command,[string]$Name,[switch]$Binary,[int[]]$Allowed=@(0)){
    $script:ordinal++;$label=('{0:d4}-{1}'-f$script:ordinal,$Name)
    Invoke-RentzAdb -RunRoot $run -Arguments $Command -Label $label -Binary:$Binary -AllowedExitCodes $Allowed
}
function DumpUi {
    for($attempt=0;$attempt-lt3;$attempt++){
        $remote='/sdcard/rentz-native-'+[guid]::NewGuid().ToString('N')+'.xml'
        $dump=Adb @('shell','uiautomator','dump',$remote) 'dump'
        if(($dump.Output+$dump.Error)-match[regex]::Escape($remote)-and($dump.Output+$dump.Error)-notmatch'(?i)ERROR|Exception'){
            $xml=Adb @('exec-out','cat',$remote) 'hierarchy';[xml]$document=$xml.Output;return $document
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'Fresh UI hierarchy unavailable after bounded retries.'
}
function NodeText($document){return (@($document.SelectNodes('//node'))|ForEach-Object{$_.text})-join' | '}
function Tap([string]$Label){
    $deadline=[datetime]::UtcNow.AddSeconds(60)
    $scrolls=0
    do{
        $document=DumpUi
        $nodes=@($document.SelectNodes('//node')|Where-Object{($_.text-eq$Label-or$_.'content-desc'-eq$Label)-and$_.enabled-eq'true'-and$_.clickable-eq'true'})
        if($nodes.Count-eq1){
            $bounds=[regex]::Match($nodes[0].bounds,'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$');if(-not$bounds.Success){throw 'Invalid clickable bounds'}
            $x=([int]$bounds.Groups[1].Value+[int]$bounds.Groups[3].Value)/2;$y=([int]$bounds.Groups[2].Value+[int]$bounds.Groups[4].Value)/2
            $watch=[Diagnostics.Stopwatch]::StartNew();Adb @('shell','input','tap',[string][int]$x,[string][int]$y) 'tap'|Out-Null;$watch.Stop();$uiActions.Add([pscustomobject]@{label=$Label;elapsedMillis=$watch.ElapsedMilliseconds});return
        }
        $scroll=@($document.SelectNodes('//node')|Where-Object{$_.scrollable-eq'true'-and$_.class-eq'android.widget.ScrollView'})
        if($scroll.Count-eq1-and$scrolls-lt5){$b=[regex]::Match($scroll[0].bounds,'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$');if($b.Success){$x=([int]$b.Groups[1].Value+[int]$b.Groups[3].Value)/2;$top=[int]$b.Groups[2].Value;$bottom=[int]$b.Groups[4].Value;$height=$bottom-$top;Adb @('shell','input','swipe',[string][int]$x,[string][int]($bottom-$height*.15),[string][int]$x,[string][int]($top+$height*.2),'300') 'scroll'|Out-Null;$scrolls++}}
        Start-Sleep -Milliseconds 250
    }while([datetime]::UtcNow-lt$deadline)
    throw "Native control unavailable: $Label"
}
function State([long]$After=-1,[string]$Screen='GAME'){
    $deadline=[datetime]::UtcNow.AddSeconds(60)
    do{
        $read=Adb @('exec-out','run-as','ro.rentzlab.app','cat','files/actor-view.json') 'actor' -Allowed @(0,1)
        if($read.ExitCode-eq0){try{$state=$read.Output|ConvertFrom-Json;if($state.revision-gt$After-and$state.screen-eq$Screen-and-not$state.busy){$read.Output|Set-Content -LiteralPath (Join-Path $run 'actor.json') -Encoding utf8NoBOM;return $state}}catch{}}
        Start-Sleep -Milliseconds 250
    }while([datetime]::UtcNow-lt$deadline)
    throw 'Native state did not reach the requested checkpoint.'
}
function Audit {
    $private=Adb @('exec-out','run-as','ro.rentzlab.app','cat','files/session.json') 'private-journal'
    $private.Output|Set-Content -LiteralPath (Join-Path $run 'journal.json') -Encoding utf8NoBOM
    $result=& $Java -Xmx256m -XX:ActiveProcessorCount=2 -cp $classpath RentzNativeFlow audit (Join-Path $run 'journal.json') (Join-Path $run 'actor.json')
    if($LASTEXITCODE-ne0){throw 'Native journal or independent score audit failed.'}
    $script:lastAudit=$result|ConvertFrom-Json
    return $script:lastAudit
}
function Capture([string]$Title,[object[]]$Assertions){
    $document=DumpUi;$watch=[Diagnostics.Stopwatch]::StartNew();$png=Adb @('exec-out','screencap','-p') 'capture' -Binary;$watch.Stop();$name=('checkpoint-{0:d2}.png'-f($script:checks.Count+1));Move-Item -LiteralPath $png.File -Destination (Join-Path $run $name)
    $script:checks.Add([pscustomobject]@{title=$Title;image=$name;observed=(NodeText $document);captureMillis=$watch.ElapsedMilliseconds;checks=$Assertions})
    foreach($assertion in $Assertions){if(-not$assertion.passed){throw "Native check failed: $($assertion.name)"}}
    return $document
}
function Check([string]$Name,$Expected,$Actual){[pscustomobject]@{name=$Name;expected=[string]$Expected;actual=[string]$Actual;passed=([string]$Expected-ceq[string]$Actual)}}
try{
    $stream=[IO.File]::Open($leasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true);$currentText=$reader.ReadToEnd();$reader.Dispose();$current=if($currentText){$currentText|ConvertFrom-Json}else{$null}
        if($current-and$current.active){throw "Android resource is reserved by $($current.owner)"}
        $lease=[ordered]@{active=$true;leaseId=$leaseId;serial='127.0.0.1:5555';owner='Rentz Lab native sanity';run=$run;acquiredUtc=[datetime]::UtcNow.ToString('o')}
        $json=$lease|ConvertTo-Json;$json|Set-Content -LiteralPath (Join-Path $run 'lease.json') -Encoding utf8NoBOM;$bytes=[Text.UTF8Encoding]::new($false).GetBytes($json);$stream.SetLength(0);$stream.Position=0;$stream.Write($bytes);$stream.Flush($true);$owned=$true
    }finally{$stream.Dispose()}
    Adb @('get-state') 'device'|Out-Null
    $wmSize=(Adb @('shell','wm','size') 'initial-size').Output;$wmDensity=(Adb @('shell','wm','density') 'initial-density').Output;$fontScale=(Adb @('shell','settings','get','system','font_scale') 'initial-font').Output;$api=(Adb @('shell','getprop','ro.build.version.sdk') 'api').Output.Trim()
    $activity=(Adb @('shell','dumpsys','activity','activities') 'initial-activity').Output;$match=[regex]::Match($activity,'mResumedActivity:.*? ([a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+) ');if($match.Success){$initialActivity=$match.Groups[1].Value}
    $previous=Adb @('shell','pm','path','ro.rentzlab.app') 'previous-package' -Allowed @(0,1);$installedBefore=$previous.Output-match'package:'
    if($installedBefore){
        $base=($previous.Output-split"`n"|Where-Object{$_-match'/base.apk'}|Select-Object -First 1).Trim().Substring(8)
        $installed=Adb @('exec-out','cat',$base) 'installed-apk' -Binary
        $installedHash=(Get-FileHash -LiteralPath $installed.File -Algorithm SHA256).Hash
        if($installedHash-ne$apkHash){
            if(-not$PreviousBuildRun){throw 'Existing local APK differs; a qualified predecessor is required.'}
            $prior=Get-Content -Raw -LiteralPath (Join-Path $PreviousBuildRun 'build-qualification.json')|ConvertFrom-Json
            if($prior.status-ne'PASS'-or$prior.apkSha256-ne$installedHash-or($prior.certificate-join'|')-ne($qualification.certificate-join'|')){throw 'Installed predecessor or signing identity differs'}
            $needsUpgrade=$true
        }
        Adb @('shell','am','force-stop','ro.rentzlab.app') 'backup-stop'|Out-Null
        $backup=Adb @('exec-out','run-as','ro.rentzlab.app','tar','-cf','-','files') 'backup-data' -Binary;$backupFile=$backup.File;$backupHash=(Get-FileHash -LiteralPath $backupFile).Hash
        $listing=@(& tar.exe -tf $backupFile);if($LASTEXITCODE-ne0-or-not$listing.Count-or@($listing|Where-Object{$_-notmatch'^files(/|$)'-or$_-match'(^|/)\.\.(/|$)'}).Count){throw 'Local data archive validation failed'}
        $repeat=Adb @('exec-out','run-as','ro.rentzlab.app','tar','-cf','-','files') 'verify-backup' -Binary
        if((Get-FileHash -LiteralPath $repeat.File).Hash-ne$backupHash){throw 'Quiescent data backup is not repeatable'}
        $backupVerified=$true
        if($needsUpgrade-and@($listing|Where-Object{-not$_.EndsWith('/')}).Count){throw 'Updating a nonempty saved session requires separate migration qualification; data preserved.'}
    }
    if((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash-ne$apkHash){throw 'APK changed before installation.'}
    if(-not$installedBefore-or$needsUpgrade){Adb @('install','-r',$apk) 'install'|Out-Null}
    Adb @('shell','am','start','-W','-n','ro.rentzlab.app/.MainActivity') 'launch'|Out-Null
    $deadline=[datetime]::UtcNow.AddSeconds(30);do{$menu=DumpUi;$menuText=NodeText $menu;if($menuText.Contains('Partidă nouă')){break};Start-Sleep -Milliseconds 250}while([datetime]::UtcNow-lt$deadline)
    Capture 'Meniu: masă, niveluri și profil' @((Check 'Titlul aplicației' 'True' ($menuText.Contains('Rentz Lab'))),(Check 'Partidă nouă disponibilă' 'True' ($menuText.Contains('Partidă nouă'))))|Out-Null
    Tap 'Partidă nouă';$dialog=NodeText (DumpUi);if($dialog.Contains('Începi o partidă nouă?')){Tap 'Începe'};$state=State
    Capture 'Partidă pornită din interfață' @((Check 'Jucători' 4 $state.view.players),(Check 'Adversari' 'HARD' $state.difficulty),(Check 'Mâna inițial ascunsă' 0 @($state.view.hand).Count))|Out-Null
    $private=Adb @('exec-out','run-as','ro.rentzlab.app','cat','files/session.json') 'initial-private-journal';$private.Output|Set-Content -LiteralPath (Join-Path $run 'journal.json') -Encoding utf8NoBOM
    & $Java -Xmx128m -cp $classpath RentzNativeFlow expose (Join-Path $run 'journal.json') 'C:\work\rentz_back\learning\exposure.json'|Out-Null;if($LASTEXITCODE-ne0){throw 'Native data exposure could not be recorded'}
    $humanActions=0;$firstPlay=$false
    while($state.view.phase-in@('CHOOSE','PLAY')){
        $decision=& $Java -Xmx256m -XX:ActiveProcessorCount=2 -cp $classpath RentzNativeFlow decide (Join-Path $run 'actor.json');if($LASTEXITCODE-ne0){throw 'Public-information player failed'};$choice=$decision|ConvertFrom-Json
        [ordered]@{actor=$state.view;decision=$choice}|ConvertTo-Json -Depth 20|Set-Content -LiteralPath (Join-Path $run ('decision-{0:d4}.json'-f$humanActions)) -Encoding utf8NoBOM
        $previousRevision=[long]$state.revision;Tap $choice.label;$state=State -After $previousRevision
        if($choice.action-eq'REVEAL'){Capture 'Cărțile proprii au fost dezvăluite' @((Check 'Opt cărți proprii' 8 @($state.view.hand).Count),(Check 'Alegerea nu este pe nevăzute după dezvăluire' 'True' $state.view.revealed))|Out-Null}
        if($choice.action-eq'PLAY'-and-not$firstPlay){$firstPlay=$true;$stillHeld=(@($state.view.hand|Where-Object{$_.suit-eq$choice.suit-and$_.rank-eq$choice.rank}).Count-gt0);$publiclySeen=(@($state.view.seen|Where-Object{$_.suit-eq$choice.suit-and$_.rank-eq$choice.rank}).Count-gt0);Capture 'Carte jucată prin control nativ' @((Check 'Cartea a ieșit din mâna proprie' $false $stillHeld),(Check 'Memorie publică actualizată' $true $publiclySeen))|Out-Null}
        if(++$humanActions-gt100){throw 'Human action bound exceeded'}
        [ordered]@{phase=$state.view.phase;humanActions=$humanActions;handNumber=$state.view.handNumber;cardsLeft=@($state.view.hand).Count}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $run ('progress-{0:d4}.json'-f$humanActions)) -Encoding utf8NoBOM
    }
    $audit=Audit
    Capture 'Mână completă: reguli și scor verificate' @((Check 'O distribuire încheiată' 1 $audit.completedDeals),(Check 'Scorul pentru fiecare loc coincide cu oracolul' ($audit.totals-join',') ($state.view.totals-join',')),(Check 'Tranziții auditate' 'True' ($audit.auditedTransitions-gt0)))|Out-Null
    Tap 'Istoricul scorului';$state=State -Screen 'HISTORY';$history=DumpUi;$historyText=NodeText $history
    $labels=@{TRICKS='Levate';QUEENS='Dame';DIAMONDS='Carouri';KING_HEARTS='Regele de cupă';TOTALS='Totale';WHIST='Whist';TEN_CLUBS='Zece de treflă';RENTZ='Rentz'}
    $contractLabel=$labels[[string]$audit.history[0].contract]
    Capture 'Istoricul distribuției reale' @((Check 'Istoric accesibil' $true ($historyText.Contains('Istoricul scorului'))),(Check 'Sub-jocul încheiat este prezent' $true ($contractLabel-and$historyText.Contains($contractLabel))))|Out-Null
    Tap 'Înapoi la joc';$state=State;Tap 'Meniu principal';$state=State -Screen 'MENU'
    Capture 'Meniu cu reluarea partidei' @((Check 'O distribuire rămâne salvată' 1 $audit.completedDeals),(Check 'Reluare disponibilă' 'True' ((NodeText (DumpUi)).Contains('Continuă partida'))))|Out-Null
    Adb @('shell','am','force-stop','ro.rentzlab.app') 'restart-stop'|Out-Null;Adb @('shell','am','start','-W','-n','ro.rentzlab.app/.MainActivity') 'restart-start'|Out-Null;Tap 'Continuă partida';$restored=State;$again=Audit
    Capture 'Reluare după repornirea procesului' @((Check 'Faza restaurată' $state.view.phase $restored.view.phase),(Check 'Istoric păstrat' $audit.completedDeals $again.completedDeals),(Check 'Totaluri păstrate' ($audit.totals-join',') ($again.totals-join',')))|Out-Null
}catch{$failure=$_.Exception.Message;[IO.File]::WriteAllText((Join-Path $run 'failure.txt'),($_|Out-String));if($owned){try{Adb @('exec-out','run-as','ro.rentzlab.app','cat','files/session.json') 'failure-private-journal' -Allowed @(0,1)|Out-Null;Adb @('exec-out','screencap','-p') 'failure-display' -Binary|Out-Null;Adb @('logcat','-d','-t','100','-v','threadtime','-s','AndroidRuntime:E','RentzLab:E','*:S') 'failure-log'|Out-Null}catch{}}}
finally{
    if($owned){
        if($backupVerified){
            try{
                if((Get-FileHash -LiteralPath $backupFile).Hash-ne$backupHash){throw 'Backup changed; refusing restoration'}
                Adb @('shell','am','force-stop','ro.rentzlab.app') 'restore-stop'|Out-Null
                $remote='/data/local/tmp/rentz-backup-'+$leaseId+'.tar';Adb @('push',$backupFile,$remote) 'restore-push'|Out-Null
                Adb @('shell','run-as','ro.rentzlab.app','rm','-f','files/session.json','files/actor-view.json','files/session.json.tmp','files/actor-view.json.tmp') 'restore-remove-test-files'|Out-Null
                Adb @('shell','run-as','ro.rentzlab.app','tar','-xf',$remote) 'restore-extract'|Out-Null
                $restoredArchive=Adb @('exec-out','run-as','ro.rentzlab.app','tar','-cf','-','files') 'restored-data' -Binary
                $backupRestored=(Get-FileHash -LiteralPath $restoredArchive.File).Hash-eq$backupHash
                if(-not$backupRestored){throw 'Restored data archive differs from verified backup'}
                Adb @('shell','rm','-f',$remote) 'restore-remove-archive'|Out-Null
            }catch{$failure+=' Data restoration failed: '+$_.Exception.Message}
        }
        try{$finalSize=(Adb @('shell','wm','size') 'final-size').Output;$finalDensity=(Adb @('shell','wm','density') 'final-density').Output;$finalFont=(Adb @('shell','settings','get','system','font_scale') 'final-font').Output;if($wmSize-and($wmSize-ne$finalSize-or$wmDensity-ne$finalDensity-or$fontScale-ne$finalFont)){$failure+=' Display configuration changed; review required.'};if($initialActivity){Adb @('shell','am','start','-W','-n',$initialActivity) 'restore-foreground'|Out-Null}}catch{$failure+=' Cleanup verification failed: '+$_.Exception.Message}
        $stream=[IO.File]::Open($leasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        try{$reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true);$current=$reader.ReadToEnd()|ConvertFrom-Json;$reader.Dispose();if($current.leaseId-eq$leaseId){$current.active=$false;$current|Add-Member -NotePropertyName releasedUtc -NotePropertyValue ([datetime]::UtcNow.ToString('o')) -Force;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($current|ConvertTo-Json));$stream.SetLength(0);$stream.Position=0;$stream.Write($bytes);$stream.Flush($true);$released=$true}}finally{$stream.Dispose()}
    }
    $cacheLease.Dispose()
}
$classpathDrift=@(foreach($identity in $classpathIdentity){if($identity.sha256-ne(Get-FileHash -LiteralPath $identity.path -Algorithm SHA256).Hash){$identity.path}})
if($classpathDrift.Count){$failure+=' Runtime classpath changed.'}
ConvertTo-Json -InputObject @($uiActions.ToArray()) -Depth 6|Set-Content -LiteralPath (Join-Path $run 'actions.json') -Encoding utf8NoBOM
ConvertTo-Json -InputObject @($checks.ToArray()) -Depth 8|Set-Content -LiteralPath (Join-Path $run 'checkpoints.json') -Encoding utf8NoBOM
[ordered]@{status=$(if($failure){'FAIL'}else{'PASS'});package='ro.rentzlab.app';apkSha256=$apkHash;versionName=$qualification.versionName;serial='127.0.0.1:5555';api=$api;scope='Native local-app sanity: one complete deal, menu, history and process restart; not a complete match or original-app parity';failure=$failure;checkpointCount=$checks.Count;actionCount=$uiActions.Count;oracle=$lastAudit;existingPackageBefore=$installedBefore;knownPredecessorUpgraded=$needsUpgrade;displayPreserved=($wmSize-and$wmSize-eq$finalSize-and$wmDensity-eq$finalDensity-and$fontScale-eq$finalFont);priorData=$(if($installedBefore){'Known APK verified; existing files backed up and restoration checked'}else{'No prior local package; original application and other packages not reset'});backupVerified=$backupVerified;backupSha256=$backupHash;backupRestored=$backupRestored;leaseReleased=$released;classpathDrift=$classpathDrift;buildQualification=$qualification}|ConvertTo-Json -Depth 10|Set-Content -LiteralPath (Join-Path $run 'ui-result.json') -Encoding utf8NoBOM
$publishLease=Enter-AtfJ5CacheLease -Repository $component.repository
try{& $Java -Xmx256m -cp $classpath RentzNativeFlow report $run;if($LASTEXITCODE-ne0){throw 'Native report publication failed'}}finally{$publishLease.Dispose()}
if($failure){throw "Native sanity failed: $failure"}
Write-Output $run
