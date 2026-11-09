param(
    [Parameter(Mandatory)][string]$RagReport,
    [Parameter(Mandatory)][string]$PublicationEvidence,
    [Parameter(Mandatory)][string]$RunRoot
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
foreach($path in @($RagReport, $PublicationEvidence, $RunRoot)){
    Assert-AtfArtifactPath -Path $path -ProjectRoot $project
}
if(Test-Path -LiteralPath $RunRoot){throw 'Use a fresh external README verification directory.'}

$rag = Get-Content -LiteralPath $RagReport -Raw | ConvertFrom-Json
$github = Get-Content -LiteralPath (Join-Path $PublicationEvidence 'github-release.json') -Raw | ConvertFrom-Json
$public = Get-Content -LiteralPath (Join-Path $PublicationEvidence 'public-download.json') -Raw | ConvertFrom-Json
$source = $rag.candidate.sourceCommit
$version = $rag.candidate.candidateVersion
$digest = $rag.releaseWorkflow.artifactSha256
if($rag.releaseWorkflow.readyForMainPromotionReview -ne $true -or
    $source -cnotmatch '^[a-f0-9]{40}$' -or $version -cnotmatch '^\d+\.\d+\.\d+$' -or
    $digest -cnotmatch '^[a-f0-9]{64}$'){
    throw 'A qualified candidate, source commit and APK digest are required.'
}
$requiredGates = @('offline-regression','component-extent-report','android-jvm','native-ui','native-ui-report','release-build','local-catalog')
foreach($gate in $requiredGates){
    if(@($rag.gates | Where-Object {$_.id -ceq $gate -and $_.status -ceq 'PASS'}).Count -ne 1){
        throw "Local qualification gate is missing: $gate"
    }
}
$tag = "v$version"
$apk = "RentzLab-$version.apk"
$repository = $rag.candidate.repository
$releaseUrl = "https://github.com/$repository/releases/tag/$tag"
if($github.stage -cne 'PUBLISHED_AND_AUTHENTICATED_DOWNLOAD_VERIFIED' -or $github.sourceCommit -cne $source -or
    $github.tag -cne $tag -or $github.latest -cne $tag -or $github.releaseUrl -cne $releaseUrl -or $github.apkSha256 -cne $digest -or
    $public.stage -cne 'ANONYMOUS_DOWNLOAD_VERIFIED' -or $public.authenticated -ne $false -or $public.bothApkArchivesVerified -ne $true){
    throw 'The published release and both download checks must identify the qualified APK.'
}
foreach($name in @($apk, "$apk.sha256")){
    $authenticated = @($github.assets | Where-Object name -CEQ $name)
    $anonymous = @($public.assets | Where-Object url -Like "*/$tag/$name")
    if($authenticated.Count -ne 1 -or $anonymous.Count -ne 1 -or
        $authenticated[0].http -ne 200 -or $anonymous[0].http -ne 200 -or
        $authenticated[0].sha256 -cne $anonymous[0].sha256 -or $authenticated[0].bytes -ne $anonymous[0].bytes){
        throw "Download evidence differs for $name"
    }
    foreach($channel in @('authenticated','anonymous')){
        $file = Join-Path $PublicationEvidence "$channel-$name"
        if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $anonymous[0].sha256 -or
            (Get-Item -LiteralPath $file).Length -ne $anonymous[0].bytes){
            throw "Downloaded bytes differ for $channel $name"
        }
    }
}
$publicApk = @($public.assets | Where-Object url -Like "*/$tag/$apk")[0]
$direct = $publicApk.url
if($publicApk.sha256 -cne $digest -or ([uri]$direct).Scheme -cne 'https' -or
    [IO.File]::ReadAllText((Join-Path $PublicationEvidence "anonymous-$apk.sha256")).Trim() -cne "$digest  $apk"){
    throw 'The public link or checksum content differs from the qualified APK.'
}

$remote = git -C $project ls-remote --symref origin HEAD "refs/tags/$tag" "refs/tags/$tag^{}"
if($LASTEXITCODE){throw 'Could not inspect the remote default branch and release tag.'}
$headLine = @($remote | Where-Object {$_ -match '^[a-f0-9]{40}\s+HEAD$'})
$tagLines = @($remote | Where-Object {$_ -match ('^[a-f0-9]{40}\s+refs/tags/'+[regex]::Escape($tag)+'(?:\^\{\})?$')})
if($headLine.Count -ne 1 -or $source -cnotin @($tagLines | ForEach-Object {($_ -split '\s+')[0]})){
    throw 'The remote default branch or exact qualified tag is missing.'
}
$head = ($headLine[0] -split '\s+')[0]
git -C $project fetch --no-tags origin $head
if($LASTEXITCODE){throw 'Could not fetch the remote README commit.'}
git -C $project merge-base --is-ancestor $source $head
if($LASTEXITCODE){throw 'The pushed README must descend from the qualified source.'}
$readme = (git -C $project show ($head+':README.md')) -join "`n"
if($LASTEXITCODE){throw 'The pushed README is missing.'}
$section = [regex]::Match($readme, '(?ms)^## Jocul\s*$\n(.*?)(?=^## |\z)')
$links = @([regex]::Matches($section.Groups[1].Value,'\]\(([^)\s]+)\)') | ForEach-Object {$_.Groups[1].Value})
if(-not $section.Success -or $links.Count -ne 2 -or $links[0] -cne $releaseUrl -or $links[1] -cne $direct){
    throw 'The pushed Jocul section does not link the verified release and public APK.'
}
$catalog = (git -C $project show ($head+':release/index.json')) -join "`n"
if($LASTEXITCODE -or ($catalog | ConvertFrom-Json).currentVersion -cne $version){
    throw 'The pushed release catalog does not identify the verified APK version.'
}

$null = New-Item -ItemType Directory -Path $RunRoot
$report = Join-Path $RunRoot 'readme-publication.json'
[ordered]@{
    gate = 'readme-publication'
    status = 'PASS'
    version = $version
    sourceCommit = $source
    readmeCommit = $head
    apkSha256 = $digest
    releaseUrl = $releaseUrl
    directApkUrl = $direct
} | ConvertTo-Json | Set-Content -LiteralPath $report -Encoding utf8NoBOM
Write-Output $report
