$script:InventoryBootstrap=$null
function Invoke-RentzInventoryProcess {
    param([string]$Executable,[string[]]$CommandArgs,[string]$Runtime)
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$Executable;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $start.StandardOutputEncoding=[Text.Encoding]::UTF8;$start.StandardErrorEncoding=[Text.Encoding]::UTF8
    foreach($argument in $CommandArgs){$start.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    try{
        if(-not$process.Start()){throw 'TEST-INVENTORY: process did not start'}
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if(-not$process.WaitForExit(60000)){$process.Kill($true);[void]$process.WaitForExit(5000);throw 'TEST-INVENTORY: bounded parser deadline exceeded'}
        if(-not$stdout.Wait(5000)-or-not$stderr.Wait(5000)){throw 'TEST-INVENTORY: parser streams did not finish'}
        $output=$stdout.GetAwaiter().GetResult();$diagnostic=$stderr.GetAwaiter().GetResult()
        if($process.ExitCode-ne0){throw "TEST-INVENTORY: parser failed. $diagnostic $output"}
        return $output
    }finally{$process.Dispose()}
}
function Get-RentzInventoryClassIdentity {
    param([string]$Classes)
    return (@(Get-ChildItem -LiteralPath $Classes -Recurse -File|Sort-Object FullName|ForEach-Object{
        [IO.Path]::GetRelativePath($Classes,$_.FullName)+'='+((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)
    })-join'|')
}
function Get-RentzSourceInventory {
    param([Parameter(Mandatory)][string]$ProjectRoot,[Parameter(Mandatory)]$Layout,
          [string]$InventorySourceRoot=(Split-Path -Parent $PSScriptRoot),
          [string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1')
    $owner=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
    Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1')
    $sourceDirectory=Join-Path $InventorySourceRoot 'atf-tests/src/main/java/atf/rentz/discovery'
    $sourceNames=@('JavaTestDeclarations.java','TestLayers.java','TestSourceInventory.java','AnnotationGraph.java','TestHierarchy.java','InheritedMethods.java')
    $sourceRows=@(foreach($name in $sourceNames){
        $file=Join-Path $sourceDirectory $name
        if(-not(Test-Path -LiteralPath $file -PathType Leaf)){throw "TEST-INVENTORY: implementation source absent: $name"}
        [pscustomobject]@{name=$name;path=$file;sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash}
    })
    $key=$JavaHome+'|'+(($sourceRows|ForEach-Object{$_.name+'='+$_.sha256})-join'|')
    if($null-eq$script:InventoryBootstrap-or$script:InventoryBootstrap.key-ne$key){
        $work=New-AtfWorkspace -Kind governance -ProjectRoot $owner
        New-Item -ItemType Directory "$work/source","$work/classes","$work/runtime"|Out-Null
        foreach($row in $sourceRows){
            Copy-Item -LiteralPath $row.path -Destination (Join-Path "$work/source" $row.name)
            if((Get-FileHash -LiteralPath (Join-Path "$work/source" $row.name)).Hash-ne$row.sha256){throw 'TEST-INVENTORY: implementation changed while copying'}
        }
        $compile=@('-J-Xmx256m',('-J-Djava.io.tmpdir='+$work+'/runtime'),'-proc:none','--release','21','-encoding','UTF-8','-d',"$work/classes")+@($sourceRows|ForEach-Object{Join-Path "$work/source" $_.name})
        Invoke-RentzInventoryProcess -Executable (Join-Path $JavaHome 'bin/javac.exe') -CommandArgs $compile -Runtime "$work/runtime"|Set-Content "$work/compile.log" -Encoding utf8NoBOM
        $script:InventoryBootstrap=[pscustomobject]@{key=$key;work=$work;classes="$work/classes";identity=(Get-RentzInventoryClassIdentity "$work/classes");sources=$sourceRows}
    }
    $bootstrap=$script:InventoryBootstrap
    if((Get-RentzInventoryClassIdentity $bootstrap.classes)-cne$bootstrap.identity){throw 'TEST-INVENTORY: bootstrap class bytes changed'}
    $roots=@($Layout.roots|Where-Object{$_.role-in@('main','test')}|ForEach-Object{[string]$_.role+'='+[string]$_.path})
    if($Layout.psobject.Properties['externalNonTestBases']){
        if($Layout.externalNonTestBases-isnot[array]-or@($Layout.externalNonTestBases|Where-Object{$_-isnot[string]-or$_-notmatch'^[A-Za-z_$][A-Za-z0-9_.$]*$'}).Count){throw 'TEST-INVENTORY: invalid external interface registry'}
        $roots+=@($Layout.externalNonTestBases|ForEach-Object{'external='+[string]$_})
    }
    if(-not$roots.Count){throw 'TEST-INVENTORY: no declared source roots'}
    $command=@('-Xmx256m','-XX:ActiveProcessorCount=2',('-Djava.io.tmpdir='+$bootstrap.work+'/runtime'),'-cp',$bootstrap.classes,'atf.rentz.discovery.TestSourceInventory',[IO.Path]::GetFullPath($ProjectRoot))+$roots
    $output=Invoke-RentzInventoryProcess -Executable (Join-Path $JavaHome 'bin/java.exe') -CommandArgs $command -Runtime ($bootstrap.work+'/runtime')
    if((Get-RentzInventoryClassIdentity $bootstrap.classes)-cne$bootstrap.identity){throw 'TEST-INVENTORY: bootstrap changed during inspection'}
    $lines=@(($output.TrimEnd([char[]]@([char]10,[char]13)))-split [Environment]::NewLine)
    if(-not$lines.Count-or$lines[0]-cne'ATF-TEST-INVENTORY-2'){throw 'TEST-INVENTORY: bridge protocol absent'}
    $entries=@(foreach($line in $lines|Select-Object -Skip 1){
        $cells=$line.Split([char]9)
        if($cells.Count-ne9-or@($cells[2..4]|Where-Object{$_-cnotin@('true','false')}).Count){throw 'TEST-INVENTORY: malformed bridge row'}
        $file=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($cells[0]))
        [pscustomobject]@{file=$file;name=$cells[1];executable=($cells[2]-ceq'true');concrete=($cells[3]-ceq'true');nested=($cells[4]-ceq'true');kind=$cells[5];layers=@(if($cells[6]){$cells[6].Split(',')});suites=@(if($cells[7]){$cells[7].Split(',')});caseClasses=@(if($cells[8]){$cells[8].Split(',')})}
    })
    return [pscustomobject]@{entries=$entries;implementationSources=$sourceRows;bootstrap=$bootstrap.work}
}
Export-ModuleMember -Function Get-RentzSourceInventory
