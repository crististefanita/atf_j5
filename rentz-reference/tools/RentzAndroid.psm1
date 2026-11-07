function Invoke-RentzAdb {
    param(
        [Parameter(Mandatory)][string]$RunRoot,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Label,
        [switch]$Binary,
        [int[]]$AllowedExitCodes = @(0),
        [ValidateRange(1,180)][int]$TimeoutSeconds = 45,
        [string]$LeasePath = 'C:\work\whist_back\android-ui-lease.json',
        [scriptblock]$ProcessFactory = { [Diagnostics.Process]::new() }
    )
    $ErrorActionPreference = 'Stop'
    $own = Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'lease.json') | ConvertFrom-Json
    $current = Get-Content -Raw -LiteralPath $LeasePath | ConvertFrom-Json
    if (-not $current.active -or $current.leaseId -ne $own.leaseId -or $current.serial -ne $own.serial) {
        throw 'ANDROID-LEASE: current resource owner differs; no command sent.'
    }
    if ($Label -notmatch '^[a-zA-Z0-9_.-]+$') { throw 'Invalid evidence label' }
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = 'C:\tools\whist-android-sdk\platform-tools\adb.exe'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $info.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    $info.Environment['ADB_SERVER_SOCKET'] = 'tcp:192.168.56.1:5038'
    foreach ($argument in @('-s',$own.serial) + $Arguments) { $info.ArgumentList.Add($argument) }
    $extension = if ($Binary) { 'bin' } else { 'txt' }
    $outputFile = Join-Path $RunRoot "$Label.$extension"
    $errorFile=Join-Path $RunRoot "$Label.stderr.txt"
    if ((Test-Path -LiteralPath $outputFile)-or(Test-Path -LiteralPath $errorFile)) { throw 'Evidence already exists; choose a new label.' }
    $process = & $ProcessFactory
    $process.StartInfo = $info
    $sink = $null
    $primary = $null
    function Stop-OwnedClient([string]$Message){
        $deadline=[TimeoutException]::new($Message)
        try{$process.Kill($true);if(-not$process.WaitForExit(2000)){$deadline.Data['terminationIncomplete']=$true}}
        catch{$deadline.Data['terminationFailure']=$_.Exception}
        throw $deadline
    }
    try {
        if (-not $process.Start()) { throw 'ADB client did not start.' }
        $errorTask = $process.StandardError.ReadToEndAsync()
        if ($Binary) {
            $sink = [IO.File]::Open($outputFile,[IO.FileMode]::CreateNew)
            $outputTask = $process.StandardOutput.BaseStream.CopyToAsync($sink)
        } else { $outputTask = $process.StandardOutput.ReadToEndAsync() }
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            Stop-OwnedClient "ADB deadline exceeded: $Label"
        }
        if(-not[Threading.Tasks.Task]::WhenAll([Threading.Tasks.Task[]]@($outputTask,$errorTask)).Wait(2000)){
            Stop-OwnedClient "ADB stream deadline exceeded: $Label"
        }
        $outputTask.GetAwaiter().GetResult() | Out-Null
        $stderr = $errorTask.GetAwaiter().GetResult()
        $stream=[IO.File]::Open($errorFile,[IO.FileMode]::CreateNew);try{$bytes=[Text.Encoding]::UTF8.GetBytes($stderr);$stream.Write($bytes)}finally{$stream.Dispose()}
        $output = if ($Binary) { '' } else { $outputTask.Result }
        if (-not $Binary) { $stream=[IO.File]::Open($outputFile,[IO.FileMode]::CreateNew);try{$bytes=[Text.Encoding]::UTF8.GetBytes($output);$stream.Write($bytes)}finally{$stream.Dispose()} }
        if ($process.ExitCode -notin $AllowedExitCodes) { throw "ADB failed ($($process.ExitCode)): $Label; $stderr" }
        $current=Get-Content -Raw -LiteralPath $LeasePath|ConvertFrom-Json
        if(-not$current.active-or$current.leaseId-ne$own.leaseId-or$current.serial-ne$own.serial){throw 'ANDROID-LEASE: resource owner changed in flight; result rejected.'}
        [pscustomobject]@{ExitCode=$process.ExitCode;Output=$output;Error=$stderr;File=$outputFile}
    } catch { $primary=$_.Exception; throw }
    finally {
        $cleanup=[Collections.Generic.List[Exception]]::new()
        if($sink){try{$sink.Dispose()}catch{$cleanup.Add($_.Exception)}}
        try{$process.Dispose()}catch{$cleanup.Add($_.Exception)}
        if($cleanup.Count){
            if($primary){$primary.Data['cleanupFailures']=$cleanup.ToArray()}
            else{$errorToThrow=$cleanup[0];if($cleanup.Count-gt1){$errorToThrow.Data['cleanupFailures']=$cleanup.ToArray()[1..($cleanup.Count-1)]};throw $errorToThrow}
        }
    }
}

function Save-RentzObservation {
    param([Parameter(Mandatory)][string]$RunRoot,[Parameter(Mandatory)][string]$Name,[scriptblock]$Command={param($Parameters) Invoke-RentzAdb @Parameters})
    if ($Name -notmatch '^[a-zA-Z0-9_.-]+$') { throw 'Invalid observation name' }
    if(Test-Path -LiteralPath (Join-Path $RunRoot "$Name.png")){throw 'Observation already exists'}
    $remote = "/sdcard/rentz-$Name-$([guid]::NewGuid().ToString('N')).xml"
    $dump = & $Command @{RunRoot=$RunRoot;Arguments=@('shell','uiautomator','dump',$remote);Label="$Name-dump"}
    if (($dump.Output + $dump.Error) -notmatch [regex]::Escape($remote) -or ($dump.Output + $dump.Error) -match '(?i)ERROR|Exception') {
        throw 'Fresh hierarchy was not confirmed; no earlier capture will be reused.'
    }
    $xml = & $Command @{RunRoot=$RunRoot;Arguments=@('exec-out','cat',$remote);Label="$Name-hierarchy"}
    if(-not(Test-Path -LiteralPath (Join-Path $RunRoot "$Name-hierarchy.txt"))-or$xml.File-ne(Join-Path $RunRoot "$Name-hierarchy.txt")-or[IO.File]::ReadAllText($xml.File)-cne$xml.Output){throw 'Fresh hierarchy evidence is missing or differs from output'}
    [xml]$document = $xml.Output
    if($document.DocumentElement.Name-ne'hierarchy'){throw 'Native hierarchy root required'}
    $png = & $Command @{RunRoot=$RunRoot;Arguments=@('exec-out','screencap','-p');Label="$Name-screen";Binary=$true}
    if($png.File-ne(Join-Path $RunRoot "$Name-screen.bin")-or-not(Test-Path -LiteralPath $png.File)){throw 'Fresh screen evidence is missing'}
    $bytes=[IO.File]::ReadAllBytes($png.File)
    if($bytes.Length-lt8-or[Convert]::ToHexString($bytes[0..7])-ne'89504E470D0A1A0A'){throw 'Native PNG signature required'}
    Move-Item -LiteralPath $png.File -Destination (Join-Path $RunRoot "$Name.png")
    @($document.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object {
        [pscustomobject]@{text=$_.text;description=$_.'content-desc';id=$_.'resource-id';class=$_.class;enabled=$_.enabled;scrollable=$_.scrollable;focusable=$_.focusable;clickable=$_.clickable;bounds=$_.bounds}
    })
}

function Get-RentzTapTarget {
    param([Parameter(Mandatory)][System.Xml.XmlElement[]]$Nodes,[Parameter(Mandatory)][string]$Label,[string]$Class='')
    $matches=@(foreach($node in $Nodes){
        $listItem=$node.class-in@('android.widget.CheckedTextView','android.widget.TextView')-and
            $null-ne$node.SelectSingleNode("ancestor::node[@class='android.widget.ListView']")
        if(($node.text-ieq$Label-or$node.'content-desc'-ceq$Label)-and$node.enabled-eq'true'-and
           ($node.clickable-eq'true'-or$listItem)-and(-not$Class-or$node.class-eq$Class)){$node}
    })
    if($matches.Count-gt1){throw "Ambiguous native control: $Label"}
    if($matches.Count-eq1){return $matches[0]}
    return $null
}

Export-ModuleMember -Function Invoke-RentzAdb,Save-RentzObservation,Get-RentzTapTarget
