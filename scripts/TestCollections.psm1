function Get-RentzTestPlan {
    param([Parameter(Mandatory)][string]$ProjectRoot,[string]$Collection='regression')
    $path=Join-Path $ProjectRoot 'config/test-collections.json'
    $document=[Text.Json.JsonDocument]::Parse([IO.File]::ReadAllText($path))
    try{
        function UniqueFields($Node){
            if($Node.ValueKind-eq[Text.Json.JsonValueKind]::Object){$keys=@($Node.EnumerateObject()|ForEach-Object{$_.Name});if(@($keys|Group-Object|Where-Object Count -GT 1).Count){throw 'Duplicate collection property'};foreach($p in $Node.EnumerateObject()){UniqueFields $p.Value}}
            elseif($Node.ValueKind-eq[Text.Json.JsonValueKind]::Array){foreach($v in $Node.EnumerateArray()){UniqueFields $v}}
        }
        UniqueFields $document.RootElement
    }finally{$document.Dispose()}
    $policy=Get-Content -Raw -LiteralPath $path|ConvertFrom-Json
    if(@(Compare-Object @('schemaVersion','default','collections'|Sort-Object) @($policy.psobject.Properties.Name|Sort-Object)).Count-or($policy.schemaVersion-isnot[long]-and$policy.schemaVersion-isnot[int])-or$policy.schemaVersion-ne1-or$policy.default-ne'regression'){throw 'Invalid collection policy'}
    $names=@('quick','regression','learning','infrastructure','android-jvm')
    if(@(Compare-Object @($names|Sort-Object) @($policy.collections.psobject.Properties.Name|Sort-Object)).Count-or$Collection-notin$names){throw 'Unknown or incomplete collection registry'}
    foreach($entry in $policy.collections.psobject.Properties){
        $p=$entry.Value
        if(@(Compare-Object @('maven','androidJvm','runtimeControls'|Sort-Object) @($p.psobject.Properties.Name|Sort-Object)).Count-or$p.androidJvm-isnot[bool]-or$p.runtimeControls-isnot[bool]-or-not$p.runtimeControls){throw 'Invalid collection stages'}
        if($p.maven-isnot[array]-or@($p.maven|Where-Object{$_-isnot[string]-or$_-notmatch'^[a-zA-Z0-9_.*]+Test$'}).Count-or@($p.maven|Group-Object|Where-Object Count -GT 1).Count){throw 'Invalid Maven selector'}
        if(-not$p.maven.Count-and-not$p.androidJvm){throw 'Empty collection'}
    }
    if(($policy.collections.regression.maven-join',')-ne'*Test'-or-not$policy.collections.regression.androidJvm){throw 'Regression must include all offline gates'}
    $selected=$policy.collections.$Collection
    $layout=Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot 'config/java-source-layout.json')|ConvertFrom-Json
    $expected=[Collections.Generic.List[string]]::new()
    foreach($source in $layout.roots|Where-Object{$_.role-eq'test'-and$_.path-ne'rentz-lab-app/app/src/test/java'}){
        foreach($file in Get-ChildItem -LiteralPath (Join-Path $ProjectRoot $source.path) -Recurse -File -Filter '*.java'){
            $text=[IO.File]::ReadAllText($file.FullName)
            if($file.BaseName-notlike'*Test'){
                if($text-match'(?m)^\s*@(?:org\.junit\.(?:jupiter\.api\.)?)?Test(?:\s|\()'){throw "Unselected test source: $($file.Name)"}
                continue
            }
            $package=[regex]::Match($text,'(?m)^\s*package\s+([a-zA-Z0-9_.]+)\s*;')
            if(-not$package.Success){throw 'Test source has no package'}
            $name=$package.Groups[1].Value+'.'+$file.BaseName
            if(@($selected.maven|Where-Object{$file.BaseName-like$_-or$name-like$_}).Count){$expected.Add($name)}
        }
    }
    if($selected.maven.Count-and-not$expected.Count){throw 'Collection selected no Maven tests'}
    foreach($selector in $selected.maven){if(-not@($expected|Where-Object{$_-like$selector-or($_-split'\.')[-1]-like$selector}).Count){throw "Maven selector resolves to no test: $selector"}}
    [pscustomobject]@{name=$Collection;selectors=@($selected.maven);expected=@($expected|Sort-Object);androidJvm=$selected.androidJvm;runtimeControls=$selected.runtimeControls;policySha256=(Get-FileHash -LiteralPath $path).Hash}
}
function Assert-RentzTestPopulation {
    param([Parameter(Mandatory)]$Plan,[AllowEmptyCollection()][array]$Results)
    $actual=@($Results|ForEach-Object{$_.name})
    if(@($actual|Group-Object|Where-Object Count -GT 1).Count-or@(Compare-Object @($Plan.expected|Sort-Object) @($actual|Sort-Object)).Count){throw 'Selected and executed Maven test populations differ'}
    if(@($Results|Where-Object{$_.tests-lt1-or$_.failed-or$_.errors-or$_.skipped}).Count){throw 'Selected tests contain failure, error, skip or empty suite'}
}
Export-ModuleMember -Function Get-RentzTestPlan,Assert-RentzTestPopulation
