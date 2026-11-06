function Resolve-AtfJ5MavenRepository {
    param([string]$Repository = '')
    if (-not [string]::IsNullOrWhiteSpace($Repository)) {
        $configured = [IO.Path]::GetFullPath($Repository)
        if (-not (Test-Path -LiteralPath $configured -PathType Container)) { throw "Configured Maven repository does not exist: $configured" }
        return $configured
    }
    $cache = Join-Path ([IO.Path]::GetTempPath()) 'ATF_J5_MAVEN_CACHE'
    $ready = Join-Path $cache '.atf-j5-cache-ready'
    if (Test-Path -LiteralPath $ready -PathType Leaf) { return $cache }
    if (Test-Path -LiteralPath $cache) { throw "Incomplete temporary Maven cache found at $cache. Preserve it for inspection or choose -MavenRepository explicitly." }
    $profile = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    $seed = Join-Path $profile '.m2\repository'
    if (-not (Test-Path -LiteralPath $seed -PathType Container)) { throw "Seed Maven repository is missing: $seed" }
    New-Item -ItemType Directory -Path $cache | Out-Null
    & robocopy $seed $cache /E /XD .locks /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Could not seed temporary Maven cache from $seed; inspect $cache before retrying." }
    [IO.File]::WriteAllText($ready,('Seeded from ' + $seed + [Environment]::NewLine),[Text.UTF8Encoding]::new($false))
    return $cache
}
function Enter-AtfJ5CacheLease {
    param([Parameter(Mandatory)][string]$Repository)
    $folder=[IO.Path]::GetFullPath($Repository)
    if(-not(Test-Path -LiteralPath $folder -PathType Container)){throw 'Cache repository is unavailable.'}
    $path=Join-Path $folder '.atf-j5-runtime.lock'
    try {return [IO.File]::Open($path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
    catch [IO.IOException] {throw "Maven cache is in use by another component or arena run: $folder"}
}
Export-ModuleMember -Function Resolve-AtfJ5MavenRepository,Enter-AtfJ5CacheLease
