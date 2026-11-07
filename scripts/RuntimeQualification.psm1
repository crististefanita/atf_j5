Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1')
function Get-RentzRuntimeHash([string]$File){return (Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash.ToLowerInvariant()}
function Read-RentzRuntimeJson([string]$File){
    Assert-AtfPhysicalPath -Path $File
    $doc=[Text.Json.JsonDocument]::Parse([IO.File]::ReadAllText($File))
    try{
        function UniqueRuntimeFields($node){
            if($node.ValueKind-eq[Text.Json.JsonValueKind]::Object){
                $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
                foreach($p in $node.EnumerateObject()){if(-not$names.Add($p.Name)){throw 'RUNTIME-QUALIFICATION: duplicate JSON field'};UniqueRuntimeFields $p.Value}
            }elseif($node.ValueKind-eq[Text.Json.JsonValueKind]::Array){foreach($child in $node.EnumerateArray()){UniqueRuntimeFields $child}}
        }
        UniqueRuntimeFields $doc.RootElement
    }finally{$doc.Dispose()}
    return Get-Content -Raw -LiteralPath $File|ConvertFrom-Json -Depth 100
}
function Assert-RentzRuntimeFields($Node,[string[]]$Fields){
    if($null-eq$Node-or@($Node.psobject.Properties.Name).Count-ne$Fields.Count-or
       @(Compare-Object @($Fields|Sort-Object) @($Node.psobject.Properties.Name|Sort-Object)).Count){throw 'RUNTIME-QUALIFICATION: unknown or missing inventory field'}
}
function Get-RentzRuntimeDirectoryFiles([string]$Directory){
    Assert-AtfPhysicalPath -Path $Directory
    $pending=[Collections.Generic.Stack[string]]::new();$pending.Push($Directory)
    $files=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    while($pending.Count){
        $current=$pending.Pop()
        foreach($entry in [IO.DirectoryInfo]::new($current).EnumerateFileSystemInfos()){
            if($entry.Attributes-band[IO.FileAttributes]::ReparsePoint){throw 'RUNTIME-QUALIFICATION: linked directory content'}
            if($entry.Attributes-band[IO.FileAttributes]::Directory){$pending.Push($entry.FullName)}
            else{$relative=[IO.Path]::GetRelativePath($Directory,$entry.FullName).Replace([IO.Path]::DirectorySeparatorChar,[char]47);$files.Add($relative,(Get-RentzRuntimeHash $entry.FullName))}
        }
    }
    return ,$files
}
function Assert-RentzRuntimeInventory {
    param([Parameter(Mandatory)][string]$Inventory,[Parameter(Mandatory)][string]$InventorySha256,
          [Parameter(Mandatory)][string]$Classpath)
    if((Get-RentzRuntimeHash $Inventory)-cne$InventorySha256.ToLowerInvariant()){throw 'RUNTIME-QUALIFICATION: inventory changed after qualification'}
    $document=Read-RentzRuntimeJson $Inventory
    Assert-RentzRuntimeFields $document @('schemaVersion','entries')
    if(($document.schemaVersion-isnot[int]-and$document.schemaVersion-isnot[long])-or$document.schemaVersion-ne1-or$document.entries-isnot[array]){throw 'RUNTIME-QUALIFICATION: inventory schema'}
    $roots=$Classpath.Split([IO.Path]::PathSeparator)
    if(-not$roots.Count-or@($roots|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count-or$roots.Count-ne$document.entries.Count){throw 'RUNTIME-QUALIFICATION: classpath population or empty entry'}
    $unique=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    for($index=0;$index-lt$roots.Count;$index++){
        $root=$roots[$index];$entry=$document.entries[$index]
        Assert-RentzRuntimeFields $entry @('path','kind','files')
        if($entry.path-isnot[string]-or$entry.kind-isnot[string]-or$entry.files-isnot[array]-or
           -not[IO.Path]::IsPathFullyQualified($root)-or-not[IO.Path]::IsPathFullyQualified($entry.path)-or-not$unique.Add([IO.Path]::GetFullPath($root))-or
           -not[IO.Path]::GetFullPath($root).Equals([IO.Path]::GetFullPath($entry.path),[StringComparison]::OrdinalIgnoreCase)){
            throw 'RUNTIME-QUALIFICATION: classpath order or entry identity'
        }
        Assert-AtfPhysicalPath -Path $root
        if($entry.kind-eq'directory'){
            if(-not(Test-Path -LiteralPath $root -PathType Container)){throw 'RUNTIME-QUALIFICATION: class directory missing'}
            $actual=Get-RentzRuntimeDirectoryFiles $root
        }elseif($entry.kind-eq'file'){
            if(-not(Test-Path -LiteralPath $root -PathType Leaf)){throw 'RUNTIME-QUALIFICATION: classpath file missing'}
            $actual=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
            $actual.Add('',(Get-RentzRuntimeHash $root))
        }else{throw 'RUNTIME-QUALIFICATION: unsupported entry kind'}
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        if($actual.Count-ne$entry.files.Count){throw ("RUNTIME-QUALIFICATION: content added or removed in "+$root+"; expected "+$entry.files.Count+" files, actual "+$actual.Count)}
        foreach($file in $entry.files){
            Assert-RentzRuntimeFields $file @('path','sha256')
            if($file.path-isnot[string]-or$file.sha256-isnot[string]-or$file.sha256-cnotmatch'^[a-f0-9]{64}$'-or
               -not$seen.Add($file.path)-or-not$actual.ContainsKey($file.path)-or$actual[$file.path]-cne$file.sha256){
                throw ("RUNTIME-QUALIFICATION: qualified bytes differ in "+$root+" :: "+$file.path+"; expected "+$file.sha256+"; actual "+$actual[$file.path])
            }
        }
    }
}
function Assert-RentzRuntimeProof($Proof){
    $allowed=@('schemaVersion','status','token','inventorySha256','classpath','diagnostic','testClasspath')
    foreach($name in @('schemaVersion','status','token','inventorySha256','classpath')){
        if(-not$Proof.psobject.Properties[$name]){throw 'RUNTIME-QUALIFICATION: incomplete test-plan proof'}
    }
    if(@($Proof.psobject.Properties.Name|Where-Object{$_-notin$allowed}).Count-or
       ($Proof.schemaVersion-isnot[int]-and$Proof.schemaVersion-isnot[long])-or$Proof.schemaVersion-ne1-or
       $Proof.status-cne'PASS'-or$Proof.token-isnot[string]-or$Proof.token-cnotmatch'^[a-f0-9]{32}$'-or
       $Proof.inventorySha256-isnot[string]-or$Proof.inventorySha256-cnotmatch'^[a-f0-9]{64}$'-or
       $Proof.classpath-isnot[string]-or-not$Proof.classpath-or
       ($Proof.psobject.Properties['diagnostic']-and$Proof.diagnostic-cne'')){throw 'RUNTIME-QUALIFICATION: invalid or failed test-plan proof'}
}
function Assert-RentzQualifiedRuntime {
    param([Parameter(Mandatory)][string]$Descriptor,[Parameter(Mandatory)][string]$DescriptorSha256,
          [Parameter(Mandatory)][string]$MainClass,[Parameter(Mandatory)][string]$Stage)
    if((Get-RentzRuntimeHash $Descriptor)-cne$DescriptorSha256.ToLowerInvariant()){throw 'RUNTIME-QUALIFICATION: runtime descriptor changed'}
    $runtime=Read-RentzRuntimeJson $Descriptor
    foreach($field in @('mainClass','classpath','classFile','classSha256','inventory','inventorySha256','attestation','attestationSha256','qualificationToken')){
        if(-not$runtime.psobject.Properties[$field]-or$runtime.$field-isnot[string]-or-not$runtime.$field){throw "RUNTIME-QUALIFICATION: required descriptor field $field"}
    }
    if($runtime.mainClass-cne$MainClass-or$runtime.qualificationToken-cnotmatch'^[a-f0-9]{32}$'){throw 'RUNTIME-QUALIFICATION: main class or token differs'}
    $stageRoot=[IO.Path]::GetFullPath($Stage)
    $classFile=[IO.Path]::GetFullPath($runtime.classFile)
    if(-not$classFile.StartsWith($stageRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or
       (Get-RentzRuntimeHash $classFile)-cne$runtime.classSha256.ToLowerInvariant()){throw 'RUNTIME-QUALIFICATION: entry class differs'}
    if((Get-RentzRuntimeHash $runtime.attestation)-cne$runtime.attestationSha256.ToLowerInvariant()){throw 'RUNTIME-QUALIFICATION: attestation changed'}
    $attestation=Read-RentzRuntimeJson $runtime.attestation
    Assert-RentzRuntimeProof $attestation
    if($attestation.token-cne$runtime.qualificationToken-or$attestation.classpath-cne$runtime.classpath-or
       $attestation.inventorySha256-cne$runtime.inventorySha256.ToLowerInvariant()){throw 'RUNTIME-QUALIFICATION: pre/post test attestation absent or inconsistent'}
    Assert-RentzRuntimeInventory -Inventory $runtime.inventory -InventorySha256 $runtime.inventorySha256 -Classpath $runtime.classpath
    return $runtime
}
function Assert-RentzRuntimeAttestation {
    param([Parameter(Mandatory)][string]$Inventory,[Parameter(Mandatory)][string]$Attestation,
          [Parameter(Mandatory)][string]$Token,[Parameter(Mandatory)][string]$Classpath)
    $proof=Read-RentzRuntimeJson $Attestation
    Assert-RentzRuntimeProof $proof
    if($proof.token-cne$Token-or($proof.psobject.Properties['testClasspath']-and$proof.testClasspath-cne$Classpath)-or(-not$proof.psobject.Properties['testClasspath']-and$proof.classpath-cne$Classpath)){throw 'RUNTIME-QUALIFICATION: test-plan byte attestation failed'}
    Assert-RentzRuntimeInventory -Inventory $Inventory -InventorySha256 $proof.inventorySha256 -Classpath $proof.classpath
}
Export-ModuleMember -Function Assert-RentzQualifiedRuntime,Assert-RentzRuntimeInventory,Assert-RentzRuntimeAttestation
