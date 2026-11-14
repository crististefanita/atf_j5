Set-StrictMode -Version Latest

function Get-AtfWorkspacePolicy {
    param([string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
    $project=[IO.Path]::GetFullPath($ProjectRoot)
    $raw=Get-Content -Raw -LiteralPath (Join-Path $project 'config/infrastructure-workspaces.json')
    $document=[Text.Json.JsonDocument]::Parse($raw)
    try{$keys=@($document.RootElement.EnumerateObject()|ForEach-Object{$_.Name});if(@($keys|Group-Object|Where-Object{$_.Count-gt1}).Count){throw 'Duplicate registry properties.'}}finally{$document.Dispose()}
    $policy=$raw|ConvertFrom-Json
    $expected=@('schemaVersion','owner','sourceGroupDirectories','sourceDirectories','artifactDirectories','infrastructureDirectories','workspaceKinds','forbiddenSystemTempPrefixes','producerScripts','reviewedTempReaders','documentation')
    if(@(Compare-Object @($expected|Sort-Object) @($keys|Sort-Object)).Count){throw 'Unknown or missing infrastructure registry fields.'}
    if($policy.schemaVersion-ne1-or$policy.owner-ne'atf_j5'){throw 'Unknown infrastructure registry identity.'}
    foreach($field in @('sourceGroupDirectories','sourceDirectories','artifactDirectories','infrastructureDirectories','workspaceKinds','forbiddenSystemTempPrefixes')){
        if(-not@($policy.$field).Count){throw "Empty registry category: $field"}
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach($name in $policy.$field){if($name-notmatch'^[a-zA-Z0-9_.-]+$'-or$name-in@('.','..')-or-not$seen.Add($name)){throw "Invalid or duplicate registry entry: $field"}}
    }
    $scripts=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($relative in $policy.producerScripts){
        if($relative-notmatch'^[a-zA-Z0-9_./-]+\.(ps1|psm1)$'-or-not$scripts.Add($relative)){throw 'Invalid or duplicate producer script.'}
    }
    foreach($reader in $policy.reviewedTempReaders){
        $fields=@($reader.PSObject.Properties.Name|Sort-Object)
        if(@(Compare-Object @('functions','path','reason','role') $fields).Count-or$reader.role-notin@('allocator','governance')-or$reader.reason-isnot[string]-or[string]::IsNullOrWhiteSpace($reader.reason)){
            throw 'A temporary-directory reader needs an explicit role, scope and reason.'
        }
        if($reader.path-notmatch'^[a-zA-Z0-9_./-]+\.(ps1|psm1)$'-or-not$scripts.Add($reader.path)){throw 'Reader and producer script identities must be distinct.'}
        if(-not@($reader.functions).Count-or@($reader.functions|Where-Object{$_-ne'<script>'-and$_-notmatch'^[a-zA-Z][a-zA-Z0-9-]*$'}).Count){throw 'Invalid reader function scope.'}
    }
    foreach($relative in @($policy.documentation)+@($policy.producerScripts)+@($policy.reviewedTempReaders|ForEach-Object{$_.path})){
        if([IO.Path]::IsPathRooted($relative)-or($relative-split'[\\/]')-contains'..'){throw 'Registry paths must stay inside the project.'}
    }
    if($policy.documentation-notmatch'^docs/[a-zA-Z0-9_./-]+\.md$'-or-not(Test-Path -LiteralPath (Join-Path $project $policy.documentation))){throw 'Infrastructure documentation is missing.'}
    return $policy
}

function Get-AtfPowerShellFiles {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    $pending=[Collections.Generic.Stack[string]]::new();$pending.Push($ProjectRoot)
    while($pending.Count){
        foreach($item in Get-ChildItem -LiteralPath $pending.Pop() -Force){
            if($item.Attributes-band[IO.FileAttributes]::ReparsePoint){$item;continue}
            $relative=[IO.Path]::GetRelativePath($ProjectRoot,$item.FullName).Replace('\','/')
            if($item.PSIsContainer){
                if($relative-match'^(\.git|\.idea)(/|$)|^(atf-test-kit|atf-tests|rentz-lab-app/game-core)/target(/|$)|^rentz-lab-app/((app|game-core)/)?build(/|$)|^rentz-lab-app/(algorithm-build/)?\.gradle(/|$)'){continue}
                $pending.Push($item.FullName)
            }elseif($item.Extension-in@('.ps1','.psm1')){$item}
        }
    }
}

function Test-AtfTempSource {
    param($Node)
    if($Node-is[Management.Automation.Language.VariableExpressionAst]){return $Node.VariablePath.UserPath-match'^(?i)env:(TEMP|TMP)$'}
    if($Node-isnot[Management.Automation.Language.InvokeMemberExpressionAst]){return $false}
    $member=$Node.Member.Extent.Text.Trim('"',"'")
    if($member-in@('GetTempPath','GetTempFileName','GetEnvironmentVariables')){return $true}
    if($member-ne'GetEnvironmentVariable'){return $false}
    if(-not$Node.Arguments.Count){return $true}
    $argument=$Node.Arguments[0]
    if($argument-is[Management.Automation.Language.StringConstantExpressionAst]){return $argument.Value-in@('TEMP','TMP')}
    if($argument-is[Management.Automation.Language.ExpandableStringExpressionAst]-and-not$argument.NestedExpressions.Count){return $argument.Value-in@('TEMP','TMP')}
    if(Test-AtfBoundedEnvironmentName -Argument $argument){return $false}
    return $true # A computed environment-variable name cannot be proven unrelated to TEMP.
}

function Test-AtfBoundedEnvironmentName {
    param($Argument)
    if($Argument-isnot[Management.Automation.Language.VariableExpressionAst]){return $false}
    $name=$Argument.VariablePath.UserPath;$loop=$Argument.Parent
    while($null-ne$loop-and$loop-isnot[Management.Automation.Language.ForEachStatementAst]){$loop=$loop.Parent}
    if($null-eq$loop-or$loop.Variable.VariablePath.UserPath-ne$name){return $false}
    try{
        if($loop.Condition.PipelineElements.Count-ne1){return $false}
        $values=@($loop.Condition.PipelineElements[0].Expression.SafeGetValue())
        if(-not$values.Count-or@($values|Where-Object{$_-isnot[string]-or$_-in@('TEMP','TMP')}).Count){return $false}
    }catch{return $false}
    $writes=$loop.Body.FindAll({param($node)
        $node-is[Management.Automation.Language.AssignmentStatementAst]-and
        $node.Left-is[Management.Automation.Language.VariableExpressionAst]-and$node.Left.VariablePath.UserPath-eq$name
    },$true)
    return $writes.Count-eq0
}

function Get-AtfAstFunctionName {
    param($Node)
    $parent=$Node.Parent
    while($null-ne$parent){
        if($parent-is[Management.Automation.Language.FunctionDefinitionAst]){return $parent.Name}
        $parent=$parent.Parent
    }
    return '<script>'
}

function Get-AtfDataRoot {
    param([string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
    $project=[IO.Path]::GetFullPath($ProjectRoot)
    $configured=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT')
    if($configured){
        $data=[IO.Path]::GetFullPath($configured)
    }else{
        $top=git -C $project rev-parse --show-toplevel
        if($LASTEXITCODE-ne0-or-not$top-or[IO.Path]::GetFullPath([string]$top)-cne$project){throw 'Default data root requires the ATF repository root.'}
        $common=Get-AtfGitCommonDirectory -RepositoryRoot $project
        Assert-AtfPhysicalPath -Path $common
        $checkout=Split-Path -Parent $common
        Assert-AtfPhysicalPath -Path $checkout
        if((Split-Path -Leaf $checkout)-cne'atf_j5'-or-not(Test-Path -LiteralPath (Join-Path $checkout 'config/infrastructure-workspaces.json') -PathType Leaf)){
            throw 'Default data root requires a verified atf_j5 Git common directory.'
        }
        $data=Join-Path (Split-Path -Parent (Split-Path -Parent $checkout)) 'rentz_back/j5-active'
    }
    $forbidden=@($project,[IO.Path]::GetTempPath().TrimEnd('\','/'))
    foreach($boundary in $forbidden){if($data.Equals($boundary,[StringComparison]::OrdinalIgnoreCase)-or$data.StartsWith($boundary+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Data root must be outside sources and the operating-system temporary directory.'}}
    return $data
}

function Get-AtfGitCommonDirectory {
    param([Parameter(Mandatory)][string]$RepositoryRoot)
    $value=git -C $RepositoryRoot rev-parse --git-common-dir
    if($LASTEXITCODE-ne0-or-not$value){throw 'Git common directory unavailable.'}
    $path=if([IO.Path]::IsPathRooted($value)){[string]$value}else{Join-Path $RepositoryRoot ([string]$value)}
    return [IO.Path]::GetFullPath($path)
}

function Assert-AtfPhysicalPath {
    param([Parameter(Mandatory)][string]$Path)
    $cursor=[IO.Path]::GetFullPath($Path)
    while($cursor){
        if(Test-Path -LiteralPath $cursor){$item=Get-Item -LiteralPath $cursor -Force;if($item.Attributes-band[IO.FileAttributes]::ReparsePoint){throw "Infrastructure path crosses a reparse point: $cursor"}}
        $parent=Split-Path -Parent $cursor;if($parent-eq$cursor){break};$cursor=$parent
    }
}

function Assert-AtfArtifactPath {
    param([Parameter(Mandatory)][string]$Path,[string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
    $policy=Get-AtfWorkspacePolicy -ProjectRoot $ProjectRoot
    $data=Get-AtfDataRoot -ProjectRoot $ProjectRoot;$full=[IO.Path]::GetFullPath($Path)
    if(-not$full.StartsWith($data+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Artifact destination is outside the registered data root.'}
    $collection=([IO.Path]::GetRelativePath($data,$full)-split'[\\/]')[0]
    if($collection-notin$policy.artifactDirectories-or$collection-eq'infrastructure'){throw 'Artifact collection is not registered for product output.'}
    Assert-AtfPhysicalPath -Path $full
}

function Get-AtfInfrastructurePath {
    param([Parameter(Mandatory)][ValidateSet('workspaces','cache','governance','signing')][string]$Area,[string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
    Get-AtfWorkspacePolicy -ProjectRoot $ProjectRoot|Out-Null
    $path=Join-Path (Get-AtfDataRoot -ProjectRoot $ProjectRoot) ('infrastructure/'+$Area)
    Assert-AtfPhysicalPath -Path $path
    return $path
}

function Get-AtfSigningDirectory {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    $checkout=Split-Path -Parent (Get-AtfGitCommonDirectory -RepositoryRoot $ProjectRoot)
    if((Split-Path -Leaf $checkout)-cne'atf_j5'){throw 'Rentz signing requires the canonical atf_j5 checkout.'}
    $directory=Join-Path $checkout '.atf-signing'
    Assert-AtfPhysicalPath -Path $directory
    $null=git -C $checkout check-ignore -- .atf-signing/rentz-release.p12
    if($LASTEXITCODE){throw 'The canonical signing directory must be ignored by Git.'}
    return $directory
}

function New-AtfWorkspace {
    param([Parameter(Mandatory)][string]$Kind,[string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
    $policy=Get-AtfWorkspacePolicy -ProjectRoot $ProjectRoot
    if($Kind-notin$policy.workspaceKinds){throw 'Workspace kind is not registered.'}
    $id=[guid]::NewGuid().ToString('N');$base=Get-AtfInfrastructurePath -Area workspaces -ProjectRoot $ProjectRoot
    $path=Join-Path $base ($Kind+'-'+$id)
    if(Test-Path -LiteralPath $path){throw 'Fresh workspace required.'}
    New-Item -ItemType Directory -Force -Path $base|Out-Null
    New-Item -ItemType Directory -Path $path|Out-Null
    [ordered]@{schemaVersion=1;owner=$policy.owner;id=$id;kind=$Kind;sourceRoot=[IO.Path]::GetFullPath($ProjectRoot);createdUtc=[datetime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $path '.atf-workspace.json') -Encoding utf8NoBOM
    return $path
}

function Get-AtfWorkspaceFindings {
    param([Parameter(Mandatory)][string]$ProjectRoot,[Parameter(Mandatory)][string]$DataRoot,[Parameter(Mandatory)][string]$SystemTempRoot,[Parameter(Mandatory)]$Policy)
    $findings=[Collections.Generic.List[object]]::new()
    function Add-Finding([string]$Code,[string]$Path,[string]$Reason){$findings.Add([pscustomobject]@{code=$Code;path=$Path;reason=$Reason})}
    $scopes=@(
        @{path=(Split-Path -Parent $ProjectRoot);names=$Policy.sourceGroupDirectories},
        @{path=$ProjectRoot;names=$Policy.sourceDirectories},
        @{path=$DataRoot;names=$Policy.artifactDirectories},
        @{path=(Join-Path $DataRoot 'infrastructure');names=$Policy.infrastructureDirectories}
    )
    foreach($scope in $scopes){
        if(-not(Test-Path -LiteralPath $scope.path -PathType Container)){continue}
        foreach($directory in Get-ChildItem -LiteralPath $scope.path -Directory -Force){
            if($directory.Name-notin$scope.names){Add-Finding 'INFRA-UNREGISTERED-DIRECTORY' $directory.FullName 'Directory is not declared in the infrastructure registry.'}
            if($directory.Attributes-band[IO.FileAttributes]::ReparsePoint){Add-Finding 'INFRA-REPARSE-POINT' $directory.FullName 'Managed infrastructure cannot escape through a junction or symbolic link.'}
        }
    }
    $workspaces=Join-Path $DataRoot 'infrastructure/workspaces'
    if(Test-Path -LiteralPath $workspaces){
        foreach($directory in Get-ChildItem -LiteralPath $workspaces -Directory -Force){
            try{
                if($directory.Attributes-band[IO.FileAttributes]::ReparsePoint){throw 'Linked workspace'}
                $descriptor=Get-Content -Raw -LiteralPath (Join-Path $directory.FullName '.atf-workspace.json') -ErrorAction Stop|ConvertFrom-Json
                $declaredRoot=[string]$descriptor.sourceRoot
                if(-not[IO.Path]::IsPathRooted($declaredRoot)){throw 'Relative source root'}
                $declaredRoot=[IO.Path]::GetFullPath($declaredRoot)
                if($descriptor.schemaVersion-ne1-or$descriptor.owner-ne$Policy.owner-or$descriptor.kind-notin$Policy.workspaceKinds-or$descriptor.id-notmatch'^[a-f0-9]{32}$'-or$directory.Name-ne($descriptor.kind+'-'+$descriptor.id)-or(Split-Path -Leaf $declaredRoot)-ne$Policy.owner-or-not(Test-Path -LiteralPath $declaredRoot -PathType Container)){throw 'Workspace identity mismatch'}
                Assert-AtfPhysicalPath -Path $declaredRoot
            }catch{Add-Finding 'INFRA-UNREGISTERED-WORKSPACE' $directory.FullName 'Workspace descriptor is missing, malformed or does not match the directory and owner.'}
        }
    }
    if(Test-Path -LiteralPath $SystemTempRoot){
        foreach($directory in Get-ChildItem -LiteralPath $SystemTempRoot -Directory -Force){
            foreach($prefix in $Policy.forbiddenSystemTempPrefixes){if($directory.Name.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){Add-Finding 'INFRA-FORBIDDEN-TEMP-WORKSPACE' $directory.FullName 'Legacy compilation directory outside the documented ATF workspace root.';break}}
        }
    }
    foreach($relative in $Policy.producerScripts){
        $file=Join-Path $ProjectRoot $relative
        if(-not(Test-Path -LiteralPath $file)){Add-Finding 'INFRA-MISSING-PRODUCER' $file 'Registered producer script is missing.'}
    }
    $readers=@{}
    foreach($reader in $Policy.reviewedTempReaders){
        $readers[$reader.path]=$reader
        if(-not(Test-Path -LiteralPath (Join-Path $ProjectRoot $reader.path))){Add-Finding 'INFRA-MISSING-TEMP-READER' $reader.path 'Reviewed temporary-directory reader is missing.'}
    }
    foreach($source in Get-AtfPowerShellFiles -ProjectRoot $ProjectRoot){
        if($source.Attributes-band[IO.FileAttributes]::ReparsePoint){Add-Finding 'INFRA-REPARSE-POINT' $source.FullName 'Script inventory cannot follow a junction or symbolic link.';continue}
        $file=$source.FullName;$relative=[IO.Path]::GetRelativePath($ProjectRoot,$file).Replace('\','/')
        if($relative-notin$Policy.producerScripts-and-not$readers.ContainsKey($relative)){Add-Finding 'INFRA-UNREGISTERED-SCRIPT' $file 'Discovered script has no declared producer or reviewed reader role.'}
        $tokens=$null;$parseErrors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$parseErrors)
        if($parseErrors.Count){Add-Finding 'INFRA-SCRIPT-SYNTAX' $file 'Producer script cannot be parsed.'}
        $bad=@($ast.FindAll({param($node) Test-AtfTempSource -Node $node},$true)|Where-Object{
            -not$readers.ContainsKey($relative)-or(Get-AtfAstFunctionName -Node $_)-notin$readers[$relative].functions
        })
        if($bad.Count){Add-Finding 'INFRA-DIRECT-TEMP-PRODUCER' $file 'Producer bypasses the registered infrastructure allocator.'}
    }
    return $findings.ToArray()
}

function Assert-AtfInfrastructure {
    param([string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
    $policy=Get-AtfWorkspacePolicy -ProjectRoot $ProjectRoot
    $findings=@(Get-AtfWorkspaceFindings -ProjectRoot $ProjectRoot -DataRoot (Get-AtfDataRoot -ProjectRoot $ProjectRoot) -SystemTempRoot ([IO.Path]::GetTempPath()) -Policy $policy)
    if($findings.Count){throw ('Infrastructure governance refused execution: '+(($findings|ForEach-Object{$_.code+' '+$_.path})-join'; '))}
}

function Get-AtfReleaseSourceCommit {
    param([Parameter(Mandatory)][string]$SourceRoot,[Parameter(Mandatory)][string]$ProjectRoot)
    $source=[IO.Path]::GetFullPath($SourceRoot)
    Assert-AtfPhysicalPath -Path $source
    $workspace=Split-Path -Parent $source
    $base=Get-AtfInfrastructurePath -Area workspaces -ProjectRoot $ProjectRoot
    if((Split-Path -Parent $workspace)-ne$base-or(Split-Path -Leaf $source)-ne'atf_j5'){
        throw 'Release source must be an atf_j5 worktree inside a registered workspace.'
    }
    $descriptor=Get-Content -Raw -LiteralPath (Join-Path $workspace '.atf-workspace.json')|ConvertFrom-Json
    $declaredRoot=[string]$descriptor.sourceRoot
    if(-not[IO.Path]::IsPathRooted($declaredRoot)-or(Split-Path -Leaf $workspace)-ne("$($descriptor.kind)-$($descriptor.id)")){
        throw 'Release workspace identity differs from the registered project.'
    }
    $declaredRoot=[IO.Path]::GetFullPath($declaredRoot)
    if((Split-Path -Leaf $declaredRoot)-ne'atf_j5'-or-not(Test-Path -LiteralPath $declaredRoot -PathType Container)){
        throw 'Release workspace source root is unavailable.'
    }
    Assert-AtfPhysicalPath -Path $declaredRoot
    if((Get-AtfGitCommonDirectory -RepositoryRoot $declaredRoot)-cne(Get-AtfGitCommonDirectory -RepositoryRoot $ProjectRoot)){
        throw 'Release workspace belongs to another Git repository.'
    }
    $top=git -C $source rev-parse --show-toplevel
    if($LASTEXITCODE-ne0-or[IO.Path]::GetFullPath($top)-ne$source){throw 'Explicit release Git worktree required.'}
    $commit=git -C $source rev-parse --verify HEAD
    if($LASTEXITCODE-ne0-or$commit-notmatch'^[a-f0-9]{40}$'){throw 'Release source commit unavailable.'}
    $changes=git -C $source status --porcelain=v1 --untracked-files=all
    if($LASTEXITCODE-ne0-or$changes){throw 'Release worktree must match its commit.'}
    return $commit
}

function Get-AtfCheckoutLayoutRoot {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    $project=[IO.Path]::GetFullPath($ProjectRoot)
    Assert-AtfPhysicalPath -Path $project
    $workspace=Split-Path -Parent $project
    $workspaceBase=Get-AtfInfrastructurePath -Area workspaces -ProjectRoot $project
    if(-not$workspace.StartsWith($workspaceBase+'\',[StringComparison]::OrdinalIgnoreCase)){
        return $project
    }
    $descriptorPath=Join-Path $workspace '.atf-workspace.json'
    if(-not(Test-Path -LiteralPath $descriptorPath -PathType Leaf)){
        throw 'Registered workspace checkout layout descriptor is missing.'
    }
    $descriptor=Get-Content -Raw -LiteralPath $descriptorPath|ConvertFrom-Json
    $declaredRoot=[string]$descriptor.sourceRoot
    if($descriptor.schemaVersion-ne1-or$descriptor.owner-cne'atf_j5'-or
        -not[IO.Path]::IsPathRooted($declaredRoot)-or
        (Split-Path -Leaf $workspace)-cne("$($descriptor.kind)-$($descriptor.id)")){
        throw 'Registered workspace checkout layout identity is invalid.'
    }
    $declaredRoot=[IO.Path]::GetFullPath($declaredRoot)
    if((Split-Path -Leaf $declaredRoot)-cne'atf_j5'-or-not(Test-Path -LiteralPath $declaredRoot -PathType Container)){
        throw 'Registered workspace checkout layout source is unavailable.'
    }
    Assert-AtfPhysicalPath -Path $declaredRoot
    if((Get-AtfGitCommonDirectory -RepositoryRoot $declaredRoot)-cne(Get-AtfGitCommonDirectory -RepositoryRoot $project)){
        throw 'Registered workspace checkout layout belongs to another Git repository.'
    }
    return $declaredRoot
}

function Get-AtfReportingLibrary {
    param([Parameter(Mandatory)][string]$ProjectRoot)
    $project=[IO.Path]::GetFullPath($ProjectRoot)
    $pomPath=Join-Path $project 'pom.xml'
    Assert-AtfPhysicalPath -Path $pomPath
    if(-not(Test-Path -LiteralPath $pomPath -PathType Leaf)){throw 'Root Maven POM is missing.'}
    [xml]$pom=Get-Content -Raw -LiteralPath $pomPath
    $nodes=@($pom.SelectNodes("/*[local-name()='project']/*[local-name()='properties']/*[local-name()='atf.reporting.version']"))
    if($nodes.Count-ne1){throw 'Root Maven POM must declare exactly one atf.reporting.version.'}
    $version=$nodes[0].InnerText.Trim()
    if($version-notmatch'^\d+\.\d+\.\d+-j\d+$'){throw 'ATF reporting version has an invalid artifact form.'}
    $name='atf-reporting-'+$version
    $jar=Join-Path $project ('lib/'+$name+'.jar')
    $artifactPom=Join-Path $project ('lib/'+$name+'.pom')
    Assert-AtfPhysicalPath -Path $jar
    Assert-AtfPhysicalPath -Path $artifactPom
    if(-not(Test-Path -LiteralPath $jar -PathType Leaf)-or-not(Test-Path -LiteralPath $artifactPom -PathType Leaf)){
        throw 'The POM-selected ATF reporting JAR and POM must both be present.'
    }
    return [pscustomobject]@{version=$version;name=$name;jar=$jar;sha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant();pom=$artifactPom;pomSha256=(Get-FileHash -LiteralPath $artifactPom -Algorithm SHA256).Hash.ToLowerInvariant()}
}

Export-ModuleMember -Function Get-AtfWorkspacePolicy,Get-AtfDataRoot,Assert-AtfPhysicalPath,Assert-AtfArtifactPath,Get-AtfInfrastructurePath,Get-AtfSigningDirectory,New-AtfWorkspace,Get-AtfWorkspaceFindings,Assert-AtfInfrastructure,Get-AtfReleaseSourceCommit,Get-AtfCheckoutLayoutRoot,Get-AtfReportingLibrary
