function Invoke-RentzAdb {
    param(
        [Parameter(Mandatory)][string]$RunRoot,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$Label,
        [switch]$Binary,
        [int[]]$AllowedExitCodes = @(0),
        [ValidateRange(1,180)][int]$TimeoutSeconds = 45
    )
    $ErrorActionPreference = 'Stop'
    $own = Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'lease.json') | ConvertFrom-Json
    $current = Get-Content -Raw -LiteralPath 'C:\work\whist_back\android-ui-lease.json' | ConvertFrom-Json
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
    $info.Environment['ADB_SERVER_SOCKET'] = 'tcp:192.168.56.1:5038'
    foreach ($argument in @('-s',$own.serial) + $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    $extension = if ($Binary) { 'bin' } else { 'txt' }
    $outputFile = Join-Path $RunRoot "$Label.$extension"
    if (Test-Path -LiteralPath $outputFile) { throw 'Evidence already exists; choose a new label.' }
    $sink = $null
    try {
        if (-not $process.Start()) { throw 'ADB client did not start.' }
        $errorTask = $process.StandardError.ReadToEndAsync()
        if ($Binary) {
            $sink = [IO.File]::Open($outputFile,[IO.FileMode]::CreateNew)
            $outputTask = $process.StandardOutput.BaseStream.CopyToAsync($sink)
        } else { $outputTask = $process.StandardOutput.ReadToEndAsync() }
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill($true)
            $process.WaitForExit()
            throw "ADB deadline exceeded: $Label"
        }
        $outputTask.GetAwaiter().GetResult() | Out-Null
        $stderr = $errorTask.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $RunRoot "$Label.stderr.txt"),$stderr)
        $output = if ($Binary) { '' } else { $outputTask.Result }
        if (-not $Binary) { [IO.File]::WriteAllText($outputFile,$output) }
        if ($process.ExitCode -notin $AllowedExitCodes) { throw "ADB failed ($($process.ExitCode)): $Label; $stderr" }
        [pscustomobject]@{ExitCode=$process.ExitCode;Output=$output;Error=$stderr;File=$outputFile}
    } finally {
        if ($sink) { $sink.Dispose() }
        $process.Dispose()
    }
}

function Save-RentzObservation {
    param([Parameter(Mandatory)][string]$RunRoot,[Parameter(Mandatory)][string]$Name)
    if ($Name -notmatch '^[a-zA-Z0-9_.-]+$') { throw 'Invalid observation name' }
    $remote = "/sdcard/rentz-$Name.xml"
    $dump = Invoke-RentzAdb -RunRoot $RunRoot -Arguments @('shell','uiautomator','dump',$remote) -Label "$Name-dump"
    if (($dump.Output + $dump.Error) -notmatch [regex]::Escape($remote) -or ($dump.Output + $dump.Error) -match '(?i)ERROR|Exception') {
        throw 'Fresh hierarchy was not confirmed; no earlier capture will be reused.'
    }
    $xml = Invoke-RentzAdb -RunRoot $RunRoot -Arguments @('exec-out','cat',$remote) -Label "$Name-hierarchy"
    $png = Invoke-RentzAdb -RunRoot $RunRoot -Arguments @('exec-out','screencap','-p') -Label "$Name-screen" -Binary
    Move-Item -LiteralPath $png.File -Destination (Join-Path $RunRoot "$Name.png")
    [xml]$document = $xml.Output
    @($document.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object {
        [pscustomobject]@{text=$_.text;description=$_.'content-desc';id=$_.'resource-id';clickable=$_.clickable;bounds=$_.bounds}
    })
}

Export-ModuleMember -Function Invoke-RentzAdb,Save-RentzObservation
