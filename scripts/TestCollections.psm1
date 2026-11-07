Import-Module (Join-Path $PSScriptRoot 'TestSourceInventory.psm1')
function Get-RentzTestPlan {
    param([Parameter(Mandatory)][string]$ProjectRoot,[string]$Collection='regression',
          [string]$InventorySourceRoot=(Split-Path -Parent $PSScriptRoot),[string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1')
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
    $reports=[Collections.Generic.List[string]]::new()
    $nestedReports=[Collections.Generic.List[string]]::new()
    $nestedCaseClasses=[Collections.Generic.List[string]]::new()
    if(-not$layout.mavenModules){throw 'TEST-INVENTORY: declared Maven membership required'}
    $inventory=Get-RentzSourceInventory -ProjectRoot $ProjectRoot -Layout $layout -InventorySourceRoot $InventorySourceRoot -JavaHome $JavaHome
    foreach($entry in $inventory.entries){
        $sourceRoot=@($layout.roots|Where-Object{$_.role-eq'test'-and$entry.file.StartsWith($_.path+'/',[StringComparison]::Ordinal)})
        if(-not$sourceRoot.Count){continue}
        if($sourceRoot.Count-ne1){throw 'TEST-INVENTORY: ambiguous source-set ownership'}
        $maven=@($layout.mavenModules|Where-Object{$sourceRoot[0].path.StartsWith($_+'/src/test/',[StringComparison]::Ordinal)})
        if(-not$maven.Count){continue}
        if($maven.Count-ne1){throw 'TEST-INVENTORY: ambiguous Maven ownership'}
        if($entry.nested){continue}
        $simple=($entry.name-split'\.')[ -1 ]
        $fileName=[IO.Path]::GetFileNameWithoutExtension($entry.file)
        if($entry.executable){
            if($simple-notlike'*Test'-or$simple-cne$fileName){throw "TEST-CONVENTION: executable $($entry.name) in $($entry.file) needs an explicit conventional test file"}
            if(@($selected.maven|Where-Object{$simple-like$_-or$entry.name-like$_}).Count){
                $expected.Add($entry.name)
                $reports.AddRange([string[]]$entry.suites)
                if($entry.suites.Count-gt1){
                    $nestedReports.AddRange([string[]]$entry.suites)
                    $nestedCaseClasses.AddRange([string[]]$entry.caseClasses)
                }
            }
        }elseif($entry.concrete-and$simple-like'*Test'){
            throw "TEST-INVENTORY: named suite $($entry.name) has no resolved executable methods; inheritance or another engine needs an explicit contract"
        }
    }
    if($selected.maven.Count-and-not$expected.Count){throw 'Collection selected no Maven tests'}
    foreach($selector in $selected.maven){if(-not@($expected|Where-Object{$_-like$selector-or($_-split'\.')[-1]-like$selector}).Count){throw "Maven selector resolves to no test: $selector"}}
    if(@($reports|Group-Object -CaseSensitive|Where-Object Count -GT 1).Count){
        throw 'TEST-INVENTORY: selected roots share a binary report class; Surefire XML cannot attribute those cases independently'
    }
    [pscustomobject]@{name=$Collection;selectors=@($selected.maven);expected=@($expected|Sort-Object);reports=@($reports|Sort-Object);nestedReports=@($nestedReports|Sort-Object);nestedCaseClasses=@($nestedCaseClasses|Sort-Object);androidJvm=$selected.androidJvm;runtimeControls=$selected.runtimeControls;policySha256=(Get-FileHash -LiteralPath $path).Hash;inventoryImplementation=$inventory.implementationSources}
}
function Read-RentzSurefireResult {
    param([Parameter(Mandatory)][string]$Path,[Parameter(Mandatory)][string]$Module,[Parameter(Mandatory)]$Plan)
    $settings=[System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing=[System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver=$null
    $xml=[System.Xml.XmlDocument]::new()
    $reader=[System.Xml.XmlReader]::Create($Path,$settings)
    try{$xml.Load($reader)}finally{$reader.Dispose()}
    $suite=$xml.DocumentElement
    if($suite.get_Name() -cne 'testsuite' -or $suite.get_NamespaceURI()){throw 'Expected one Surefire testsuite'}
    $name=$suite.GetAttribute('name')
    if([string]::IsNullOrWhiteSpace($name)){throw 'Missing Surefire suite name'}
    $nested=$name -cin $Plan.nestedReports
    if($xml.SelectSingleNode('//*[namespace-uri() != ""]')){throw "Unexpected namespace in Surefire elements for $name"}
    foreach($child in $suite.SelectNodes('*')){
        if($child.get_Name() -cnotin @('properties','testcase','system-out','system-err')){
            throw "Unexpected Surefire suite element $($child.get_Name()) in $name"
        }
    }
    if($xml.SelectSingleNode('//*[self::failure or self::error or self::skipped][not(parent::testcase)]')){
        throw "Misplaced testcase outcome in $name"
    }
    $cases=$xml.SelectNodes('/testsuite/testcase')
    if($xml.SelectNodes('//testcase').Count -ne $cases.Count){throw "Misplaced testcase in $name"}
    $reported=@{}
    foreach($counter in @('tests','failures','errors','skipped')){
        $value=$suite.GetAttribute($counter)
        $number=0
        if($value -cnotmatch '^(0|[1-9][0-9]*)$' -or -not [int]::TryParse($value,[ref]$number)){
            throw "Invalid Surefire counter $counter in $name"
        }
        $reported[$counter]=$number
    }
    $observed=@{tests=$cases.Count;failures=0;errors=0;skipped=0}
    $outcomeCounters=@{failure='failures';error='errors';skipped='skipped'}
    $owners=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $invocations=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $nestedInvocations=[Collections.Generic.List[object]]::new()
    foreach($case in $cases){
        $owner=$case.GetAttribute('classname')
        $invocation=$case.GetAttribute('name')
        if([string]::IsNullOrWhiteSpace($invocation) -or [string]::IsNullOrWhiteSpace($owner)){
            throw "Missing testcase identity in $name"
        }
        if(-not $nested -and $owner -cne $name){throw "Unexpected testcase owner $owner in $name"}
        $identity=$owner+[char]0+$invocation
        if(-not $invocations.Add($identity)){throw "Duplicate testcase invocation $owner#$invocation in $name"}
        if($nested){$nestedInvocations.Add([pscustomobject]@{owner=$owner;name=$invocation})}
        [void]$owners.Add($owner)
        foreach($child in $case.SelectNodes('*')){
            if($child.get_Name() -cnotin @('failure','error','skipped','properties','system-out','system-err')){
                throw "Unexpected testcase element $($child.get_Name()) in $name"
            }
        }
        $outcomes=$case.SelectNodes('failure | error | skipped')
        if($outcomes.Count -gt 1){throw "Multiple outcomes for one testcase in $name"}
        if($outcomes.Count){$observed[$outcomeCounters[$outcomes[0].get_Name()]]++}
    }
    # Surefire can consolidate nested testcases under one report while leaving counters on other containers.
    if(-not $nested){
        foreach($counter in $observed.Keys){
            if($observed[$counter] -ne $reported[$counter]){
                throw "Surefire counter $counter disagrees with the testcase nodes in $name"
            }
        }
    }
    [pscustomobject]@{
        module=$Module; name=$name; tests=$observed.tests; reportedTests=$reported.tests
        failed=[Math]::Max($reported.failures,$observed.failures)
        errors=[Math]::Max($reported.errors,$observed.errors)
        skipped=[Math]::Max($reported.skipped,$observed.skipped)
        caseFailure=($observed.failures -or $observed.errors -or $observed.skipped)
        caseClasses=@($owners|Sort-Object -CaseSensitive)
        caseInvocations=$nestedInvocations.ToArray()
    }
}
function Assert-RentzTestPopulation {
    param([Parameter(Mandatory)]$Plan,[AllowEmptyCollection()][array]$Results)
    $actual=@($Results|ForEach-Object{$_.name})
    $reports=if($Plan.PSObject.Properties['reports']){$Plan.reports}else{$Plan.expected}
    if(@($actual|Group-Object -CaseSensitive|Where-Object Count -GT 1).Count-or@(Compare-Object @($reports|Sort-Object) @($actual|Sort-Object) -CaseSensitive).Count){throw 'Selected and executed Maven test populations differ'}
    if(@($Results|Where-Object{$_.failed-or$_.errors-or$_.skipped-or$_.caseFailure-or($_.tests-lt1-and$_.name-cnotin$Plan.nestedReports)}).Count){
        throw 'Selected tests contain failure, error, skip or empty suite'
    }
    if($Plan.nestedReports.Count){
        # Surefire 3.5.2 may place the root and inner cases together in the deepest suite XML.
        $nestedRows=@($Results|Where-Object {$_.name -cin $Plan.nestedReports})
        $actualCases=@($nestedRows|ForEach-Object {$_.caseClasses}|Sort-Object -Unique -CaseSensitive)
        $seenInvocations=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($row in $nestedRows){
            if($row.caseInvocations.Count -ne $row.tests){throw "Missing Nested invocation evidence in $($row.name)"}
            foreach($case in $row.caseInvocations){
                if(-not $seenInvocations.Add($case.owner+[char]0+$case.name)){
                    throw "Duplicate Nested testcase invocation $($case.owner)#$($case.name) across reports"
                }
            }
        }
        if(@(Compare-Object @($Plan.nestedCaseClasses) $actualCases -CaseSensitive).Count){
            throw 'Selected Nested case classes and executed Maven case classes differ'
        }
    }
}
Export-ModuleMember -Function Get-RentzTestPlan,Read-RentzSurefireResult,Assert-RentzTestPopulation
