param(
    [Parameter(Mandatory)][string]$RagReport,
    [Parameter(Mandatory)][string]$PublicationEvidence,
    [Parameter(Mandatory)][string]$RunRoot,
    [switch]$Restricted
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
foreach($path in @($RagReport, $PublicationEvidence, $RunRoot)){
    Assert-AtfArtifactPath -Path $path -ProjectRoot $project
}
if(Test-Path -LiteralPath $RunRoot){throw 'Use a fresh external README verification directory.'}

function Confirm-RestrictedApk($Proof,[string]$File,[string]$Version,[string]$Source,[string]$Digest,[string]$SourceRepository){
    if($Proof.status -cne 'PASS' -or $Proof.scope -cne 'RESTRICTED_TEST_DISTRIBUTION' -or
        $Proof.version -cne $Version -or $Proof.sourceCommit -cne $Source){
        throw 'Restricted publication does not identify the qualified version and source.'
    }
    $copy = $Proof.authenticated
    if($copy.status -cne 'PASS' -or $copy.sha256 -cne $Digest -or
        ($copy.bytes -isnot [long] -and $copy.bytes -isnot [int]) -or $copy.bytes -le 0 -or
        -not (Test-Path -LiteralPath $File -PathType Leaf) -or
        (Get-Item -LiteralPath $File).Length -ne $copy.bytes -or
        (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ine $Digest){
        throw 'Authenticated APK bytes do not match the qualified artifact.'
    }
    switch -CaseSensitive ($copy.provider){
        'GitHub' {
            $repository = $Proof.releaseRepository
            $tag = "v$Version"
            $apk = "RentzLab-$Version.apk"
            if($SourceRepository -cnotmatch '^[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$' -or
                $repository -cnotmatch '^[A-Za-z0-9-]+/[A-Za-z0-9_.-]+$' -or
                ($repository -split '/')[0] -cne ($SourceRepository -split '/')[0] -or
                $Proof.repositoryPrivate -isnot [bool] -or -not $Proof.repositoryPrivate -or
                $Proof.repositoryVisibility -cne 'private' -or $Proof.releaseId -le 0 -or
                $Proof.assetId -le 0 -or $Proof.releaseState -cne 'PUBLISHED' -or
                $Proof.tag -cne $tag -or $Proof.tagCommit -cne $Source -or $copy.http -ne 200 -or
                $Proof.releaseUrl -cne "https://github.com/$repository/releases/tag/$tag" -or
                $Proof.link -cne "https://github.com/$repository/releases/download/$tag/$apk"){
                throw 'GitHub distribution requires a published asset in a verified private repository, bound to the qualified tag.'
            }
            $checksum = Join-Path (Split-Path -Parent $File) "authenticated-$apk.sha256"
            if(-not (Test-Path -LiteralPath $checksum -PathType Leaf) -or
                [IO.File]::ReadAllText($checksum).Trim() -cne "$Digest  $apk"){
                throw 'The authenticated GitHub checksum does not match the qualified APK.'
            }
        }
        'Google Drive' {
            if($Proof.driveFileId -cnotmatch '^[A-Za-z0-9_-]{10,}$' -or
                $Proof.link -cnotmatch ('^https://drive\.google\.com/file/d/'+[regex]::Escape($Proof.driveFileId)+'/view(?:\?usp=drivesdk)?$')){
                throw 'Restricted publication does not identify the qualified Drive file.'
            }
            $permissions = @($Proof.permissions | Where-Object {$_})
            if(-not $permissions.Count -or
                -not @($permissions | Where-Object {$_.type -ceq 'user' -and $_.role -ceq 'owner'}).Count -or
                @($permissions | Where-Object {$_.type -cnotin @('user','group')}).Count){
                throw 'Restricted publication requires explicit Drive permissions and excludes anonymous or domain access.'
            }
        }
        default { throw 'Unknown authenticated APK provider.' }
    }
    return $Proof.link
}

$rag = Get-Content -LiteralPath $RagReport -Raw | ConvertFrom-Json
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
$sectionName = 'Jocul'
if($Restricted){
    $proof = Get-Content -LiteralPath (Join-Path $PublicationEvidence 'restricted-download.json') -Raw | ConvertFrom-Json
    $direct = Confirm-RestrictedApk $proof (Join-Path $PublicationEvidence "authenticated-$apk") $version $source $digest $repository
    $releaseUrl = if($proof.authenticated.provider -ceq 'GitHub'){ $proof.releaseUrl }else{ "https://github.com/$repository/tree/$tag" }
    $sectionName = 'Ediție cu acces autorizat'
}else{
    $github = Get-Content -LiteralPath (Join-Path $PublicationEvidence 'github-release.json') -Raw | ConvertFrom-Json
    $public = Get-Content -LiteralPath (Join-Path $PublicationEvidence 'public-download.json') -Raw | ConvertFrom-Json
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
$section = [regex]::Match($readme, '(?ms)^## '+[regex]::Escape($sectionName)+'[^\S\r\n]*$\n(.*?)(?=^## |\z)')
$links = @([regex]::Matches($section.Groups[1].Value,'\]\(([^)\s]+)\)') | ForEach-Object {$_.Groups[1].Value})
if(-not $section.Success -or $links.Count -ne 2 -or $links[0] -cne $releaseUrl -or $links[1] -cne $direct){
    throw 'The pushed README section does not link the verified source and APK for the selected channel.'
}
$catalog = (git -C $project show ($head+':release/index.json')) -join "`n"
if($LASTEXITCODE){throw 'The pushed release catalog is missing.'}
$catalog = $catalog | ConvertFrom-Json
if($Restricted){
    $tracked = (git -C $project show ($head+":release/restricted-$version.json")) -join "`n"
    if($LASTEXITCODE){throw 'The pushed restricted distribution record is missing.'}
    $tracked = $tracked | ConvertFrom-Json
    if($catalog.candidate.version -cne $version -or $tracked.version -cne $version -or
        $tracked.sourceCommit -cne $source -or $tracked.apkSha256 -cne $digest -or $tracked.link -cne $direct -or
        $tracked.channel -cne 'authenticated-only'){
        throw 'The pushed restricted distribution record differs from the qualified download.'
    }
    if($proof.authenticated.provider -ceq 'GitHub' -and
        ($tracked.provider -cne 'GitHub' -or $tracked.releaseRepository -cne $proof.releaseRepository -or
         $tracked.releaseId -ne $proof.releaseId -or $tracked.assetId -ne $proof.assetId)){
        throw 'The pushed distribution record refers to a different GitHub release or asset.'
    }
}elseif($catalog.currentVersion -cne $version){
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
    channel = $(if($Restricted){'authenticated-only'}else{'public'})
    authenticatedDownload = 'PASS'
    anonymousDownload = $(if($Restricted){'NOT_APPLICABLE'}else{'PASS'})
} | ConvertTo-Json | Set-Content -LiteralPath $report -Encoding utf8NoBOM
Write-Output $report
