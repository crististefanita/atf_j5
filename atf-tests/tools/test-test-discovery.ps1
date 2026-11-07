param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $project 'scripts/TestCollections.psm1') -Force
if(Test-Path -LiteralPath $RunRoot){throw 'Fresh control output required'}
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$fixture=New-AtfWorkspace -Kind fixture -ProjectRoot $project
$results=[Collections.Generic.List[object]]::new()
function Require([bool]$Value,[string]$Reason){if(-not$Value){throw $Reason}}
function Case([string]$Name,[scriptblock]$Body){try{& $Body;$results.Add([pscustomobject]@{name=$Name;status='PASS'})}catch{$results.Add([pscustomobject]@{name=$Name;status='FAIL';error=$_.Exception.Message})}}
function Context([string]$Name){
    $root=Join-Path $fixture $Name
    New-Item -ItemType Directory "$root/config","$root/module/src/test/java/p" -Force|Out-Null
    Copy-Item (Join-Path $project 'config/test-collections.json') "$root/config/test-collections.json"
    '{"mavenModules":["module"],"roots":[{"role":"test","path":"module/src/test/java"}]}'|Set-Content "$root/config/java-source-layout.json"
    'package p; @atf.testkit.AtfComponentTest class NeighborTest { @org.junit.jupiter.api.Test void runs() {} }'|Set-Content "$root/module/src/test/java/p/NeighborTest.java"
    return $root
}
function Rejected([string]$Root){
    try{Get-RentzTestPlan -ProjectRoot $Root -Collection regression|Out-Null;return $false}catch{return $_.Exception.Message-match'TEST-CONVENTION|TEST-INVENTORY|GOV-JAVA'}
}
Case 'Conventional executable is selected and a plain helper is excluded' {
    $r=Context neighbor
    'package p; class Helper { void work() {} }'|Set-Content "$r/module/src/test/java/p/Helper.java"
    $p=Get-RentzTestPlan -ProjectRoot $r -Collection regression
    Require ($p.expected.Count-eq1-and$p.expected[0]-eq'p.NeighborTest') 'Healthy inventory differs'
}
$methods=[ordered]@{
    parameterized='@org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={1}) void cases(int n) {}'
    repeated='@org.junit.jupiter.api.RepeatedTest(2) void cases() {}'
    factory='@org.junit.jupiter.api.TestFactory org.junit.jupiter.api.DynamicTest cases() { return org.junit.jupiter.api.DynamicTest.dynamicTest("x", () -> {}); }'
    template='@org.junit.jupiter.api.TestTemplate void cases() {}'
    qualifiedModifier='public @org.junit.jupiter.api.Test void cases() {}'
}
foreach($entry in $methods.GetEnumerator()){
    $captured=$entry
    Case ("Nonconventional executable is refused: "+$captured.Key) {
        $r=Context $captured.Key
        ('package p; @atf.testkit.AtfComponentTest class LostSpec { '+$captured.Value+' }')|Set-Content "$r/module/src/test/java/p/LostSpec.java"
        Require (Rejected $r) 'Runnable source disappeared without an explicit convention failure'
    }
}
Case 'Composed method annotation is resolved before filename filtering' {
    $r=Context composed
    'package p; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @org.junit.jupiter.api.Test @interface CaseMarker {}'|Set-Content "$r/module/src/test/java/p/CaseMarker.java"
    'package p; class LostSpec { @CaseMarker void cases() {} }'|Set-Content "$r/module/src/test/java/p/LostSpec.java"
    Require (Rejected $r) 'Composed executable was silently omitted'
}
Case 'Comments literals text blocks and foreign annotations do not invent tests' {
    $r=Context text
    $quotes=([string][char]34)*3
    @(('package p; class Helper { String example = '+$quotes),'@Test','void imaginary() {}',($quotes+'; @foreign.Test void other() {} }'))|Set-Content "$r/module/src/test/java/p/Helper.java"
    $p=Get-RentzTestPlan -ProjectRoot $r -Collection regression
    Require ($p.expected.Count-eq1) 'Text example was mistaken for an executable source'
}
Case 'Malformed Java is refused before launching tests' {
    $r=Context invalid
    'package p; class Broken { void broken( }'|Set-Content "$r/module/src/test/java/p/Broken.java"
    Require (Rejected $r) 'Invalid Java escaped source inventory'
}
Case 'Annotation type named Test is metadata rather than an executable suite' {
    $r=Context annotation
    'package p; @org.junit.jupiter.api.Tag("component") @interface CaseTest {}'|Set-Content "$r/module/src/test/java/p/CaseTest.java"
    $p=Get-RentzTestPlan -ProjectRoot $r -Collection regression
    Require ($p.expected.Count-eq1) 'Annotation type became an expected JUnit suite'
}
Case 'Maven membership is declared instead of hardcoding the Android path' {
    $r=Context builds
    New-Item -ItemType Directory "$r/phone/src/test/java/p" -Force|Out-Null
    '{"mavenModules":["module"],"roots":[{"role":"test","path":"module/src/test/java"},{"role":"test","path":"phone/src/test/java"}]}'|Set-Content "$r/config/java-source-layout.json"
    'package p; class PhoneTest { @org.junit.Test public void runs() {} }'|Set-Content "$r/phone/src/test/java/p/PhoneTest.java"
    $p=Get-RentzTestPlan -ProjectRoot $r -Collection regression
    Require ($p.expected.Count-eq1) 'Gradle-only source entered Maven population'
}
Case 'Expected suite without resolved executable methods fails closed' {
    $r=Context unknown
    'package p; class UnknownTest extends external.Base {}'|Set-Content "$r/module/src/test/java/p/UnknownTest.java"
    Require (Rejected $r) 'Unknown executable inheritance was accepted as an empty helper'
}
Case 'Inherited conventional suite joins the selected population' {
    $r=Context inherited
    'package p; abstract class Base { @org.junit.jupiter.api.Test void run() {} }'|Set-Content "$r/module/src/test/java/p/Base.java"
    'package p; @atf.testkit.AtfComponentTest class InheritedTest extends Base {}'|Set-Content "$r/module/src/test/java/p/InheritedTest.java"
    $plan=Get-RentzTestPlan -ProjectRoot $r -Collection regression
    Require ($plan.expected.Count-eq2-and'p.InheritedTest'-in$plan.expected) 'Inherited suite escaped the declared population'
}
Case 'Inherited nonconventional suite is refused before filename filtering' {
    $r=Context inheritedLost
    'package p; interface Contract { @org.junit.jupiter.api.Test default void run() {} }'|Set-Content "$r/module/src/test/java/p/Contract.java"
    'package p; class LostSpec implements Contract {}'|Set-Content "$r/module/src/test/java/p/LostSpec.java"
    Require (Rejected $r) 'Inherited executable source was silently omitted'
}
function NestedContext([string]$Name,[string]$Leaf){
    $r=Context $Name
    'package p; abstract class Grand { @org.junit.jupiter.api.Nested class Inner { @org.junit.jupiter.api.Test void runs() {} } }'|Set-Content "$r/module/src/test/java/p/Grand.java"
    'package p; abstract class Middle extends Grand {}'|Set-Content "$r/module/src/test/java/p/Middle.java"
    ('package p; @atf.testkit.AtfComponentTest class '+$Leaf+' extends Middle {}')|Set-Content "$r/module/src/test/java/p/$Leaf.java"
    return $r
}
Case 'Indirect Nested suite is selected under its conventional top-level class' {
    $r=NestedContext nestedConventional InheritedTest
    $plan=Get-RentzTestPlan -ProjectRoot $r -Collection regression
    Require ($plan.expected.Count-eq2-and'p.InheritedTest'-in$plan.expected) 'Indirect Nested suite escaped the selected population'
    Require ($plan.nestedCaseClasses.Count-eq1-and$plan.nestedCaseClasses[0]-ceq'p.Grand$Inner') 'Wrong binary case owner'
    $xml=Join-Path $r 'nested.xml'
    function SuiteRow([string]$Name,[int]$Tests,[string]$Body){
        ('<testsuite name="'+$Name+'" tests="'+$Tests+'" failures="0" errors="0" skipped="0">'+$Body+'</testsuite>')|Set-Content $xml
        Read-RentzSurefireResult -Path $xml -Module module -Plan $plan
    }
    $outer=SuiteRow 'p.InheritedTest' 0 ''
    $neighbor=SuiteRow 'p.NeighborTest' 1 '<testcase classname="p.NeighborTest" name="runs"/>'
    $nested=SuiteRow 'p.Grand$Inner' 1 '<testcase classname="p.Grand$Inner" name="runs[1]"/><testcase classname="p.Grand$Inner" name="runs[2]"/>'
    Assert-RentzTestPopulation -Plan $plan -Results @($outer,$neighbor,$nested)
    Require ($nested.tests-eq2-and$nested.reportedTests-eq1) 'Nested consolidation must retain the observed cases'
    foreach($body in @('','<testcase classname="p.Unexpected" name="runs"/>','<testcase classname="p.Grand$Inner" name="runs"><skipped/></testcase>')){
        $altered=SuiteRow 'p.Grand$Inner' 1 $body
        $refused=$false
        try{Assert-RentzTestPopulation -Plan $plan -Results @($outer,$neighbor,$altered)}catch{$refused=$_.Exception.Message-match'Selected'}
        Require $refused 'Missing, foreign or skipped nested case was accepted'
    }
}
Case 'Indirect Nested suite with a nonconventional name is refused' {
    $r=NestedContext nestedLost LostSpec
    Require (Rejected $r) 'Indirect Nested suite was silently omitted'
}
foreach($duplicateLocation in @('same report','across reports')){
    Case ("Nested duplicate invocation "+$duplicateLocation) {
        $plan=[pscustomobject]@{
            expected=@('p.OuterTest'); reports=@('p.OuterTest','p.OuterTest$Inner')
            nestedReports=@('p.OuterTest','p.OuterTest$Inner'); nestedCaseClasses=@('p.OuterTest','p.OuterTest$Inner')
        }
        $xml=Join-Path $fixture 'nested-identities.xml'
        function Read-NestedRow([string]$Suite,[string]$Cases){
            ('<testsuite name="'+$Suite+'" tests="0" failures="0" errors="0" skipped="0">'+$Cases+'</testsuite>')|Set-Content $xml
            Read-RentzSurefireResult -Path $xml -Module module -Plan $plan
        }
        $parentCase='<testcase classname="p.OuterTest" name="runs()[1]"/>'
        $innerCase='<testcase classname="p.OuterTest$Inner" name="runs()[1]"/>'
        $outer=Read-NestedRow 'p.OuterTest' ''
        $inner=Read-NestedRow 'p.OuterTest$Inner' ($parentCase+$innerCase)
        Assert-RentzTestPopulation -Plan $plan -Results @($outer,$inner)
        $refused=$false
        try {
            if($duplicateLocation -eq 'same report'){
                $inner=Read-NestedRow 'p.OuterTest$Inner' ($parentCase+$innerCase+$innerCase)
            }else{
                $outer=Read-NestedRow 'p.OuterTest' $innerCase
            }
            Assert-RentzTestPopulation -Plan $plan -Results @($outer,$inner)
        }catch{
            $refused=$_.Exception.Message -like 'Duplicate*testcase invocation*'
        }
        Require $refused 'A duplicated Nested invocation was accepted'
    }
}
$surefirePlan=[pscustomobject]@{expected=@('p.CaseTest');nestedReports=@()}
$ordinaryCases=[ordered]@{
    'parameterized and dynamic testcase names'=@(2,'<testcase classname="p.CaseTest" name="cases(int)[1]"/><testcase classname="p.CaseTest" name="generated()[1]"/>',$true)
    'duplicate testcase with matching suite count'=@(2,'<testcase classname="p.CaseTest" name="cases(int)[1]"/><testcase classname="p.CaseTest" name="cases(int)[1]"/>',$false)
    'positive count without cases'=@(17,'',$false)
    'positive count exceeding the cases'=@(2,'<testcase classname="p.CaseTest" name="runs"/>',$false)
    'case assigned to another class'=@(1,'<testcase classname="p.OtherTest" name="runs"/>',$false)
    'case with empty name'=@(1,'<testcase classname="p.CaseTest" name=""/>',$false)
    'namespaced failure with zero counter'=@(1,'<testcase classname="p.CaseTest" name="runs"><failure xmlns="urn:probe"/></testcase>',$false)
    'prefixed error with zero counter'=@(1,'<testcase classname="p.CaseTest" name="runs"><p:error xmlns:p="urn:probe"/></testcase>',$false)
    'namespaced skip with zero counter'=@(1,'<testcase classname="p.CaseTest" name="runs"><skipped xmlns="urn:probe"/></testcase>',$false)
    'misplaced failure with zero counter'=@(1,'<testcase classname="p.CaseTest" name="runs"><system-out><failure/></system-out></testcase>',$false)
    'unknown testcase outcome'=@(1,'<testcase classname="p.CaseTest" name="runs"><unknown/></testcase>',$false)
    'negative failure counter'=@(1,'<testcase classname="p.CaseTest" name="runs"/>',$false)
    'failure node with zero declared failures'=@(1,'<testcase classname="p.CaseTest" name="runs"><failure/></testcase>',$false)
}
foreach($entry in $ordinaryCases.GetEnumerator()){
    Case ("Surefire XML: "+$entry.Key) {
        $count,$body,$expected=$entry.Value
        $failures=if($entry.Key -ceq 'negative failure counter'){-1}else{0}
        $xml=Join-Path $fixture 'TEST-p.CaseTest.xml'
        ('<testsuite name="p.CaseTest" tests="'+$count+'" failures="'+$failures+'" errors="0" skipped="0">'+$body+'</testsuite>')|Set-Content $xml
        $accepted=$true
        try{
            $row=Read-RentzSurefireResult -Path $xml -Module module -Plan $surefirePlan
            Assert-RentzTestPopulation -Plan $surefirePlan -Results @($row)
        }catch{
            if($expected){throw}
            $accepted=$false
        }
        Require ($accepted-eq$expected) 'XML population verdict did not match the fixture'
    }
}
Case 'Shared binary Nested XML cannot qualify two selected owners' {
    $r=NestedContext nestedCollision InheritedTest
    'package p; @atf.testkit.AtfComponentTest class SecondTest extends Middle {}'|Set-Content "$r/module/src/test/java/p/SecondTest.java"
    try{Get-RentzTestPlan -ProjectRoot $r -Collection regression|Out-Null}catch{
        Require ($_.Exception.Message-match'share a binary report class') 'Incorrect cause for ambiguous binary XML'
        return
    }
    throw 'Shared binary XML was accepted as independent proof for two suites'
}
Case 'Unresolved ancestry is explicit even outside the Test naming convention' {
    $r=Context unknownLost
    'package p; class LostSpec extends outside.UnknownBase {}'|Set-Content "$r/module/src/test/java/p/LostSpec.java"
    Require (Rejected $r) 'Unknown ancestry was mistaken for a test-free helper'
}
Case 'Direct bridge uses the declared JDK default and refuses a changed compiled bootstrap' {
    Import-Module (Join-Path $project 'scripts/TestSourceInventory.psm1') -Force
    $r=Context bootstrap
    $layout=Get-Content -Raw "$r/config/java-source-layout.json"|ConvertFrom-Json
    $inventory=Get-RentzSourceInventory -ProjectRoot $r -Layout $layout
    Require ($inventory.entries.Count-eq1) 'Direct bridge did not inventory the healthy neighbor'
    $class=Get-ChildItem -LiteralPath ($inventory.bootstrap+'/classes') -Recurse -File -Filter '*.class'|Select-Object -First 1
    $original=[IO.File]::ReadAllBytes($class.FullName)
    try{
        [IO.File]::WriteAllText($class.FullName,'changed compiled parser')
        $refused=$false
        try{Get-RentzSourceInventory -ProjectRoot $r -Layout $layout|Out-Null}catch{$refused=$_.Exception.Message-match'bootstrap class bytes changed'}
        Require $refused 'Changed bootstrap was executed or accepted through a new baseline'
    }finally{[IO.File]::WriteAllBytes($class.FullName,$original)}
    $restored=Get-RentzSourceInventory -ProjectRoot $r -Layout $layout
    Require ($restored.entries.Count-eq1) 'Restored original parser no longer qualifies'
}
$failed=@($results|Where-Object status -EQ FAIL)
[ordered]@{status=$(if($failed.Count){'FAIL'}else{'PASS'});tests=$results.Count;failed=$failed.Count;fixture=$fixture;scope='Source fixtures only; no Maven tests, device or UI';results=$results}|ConvertTo-Json -Depth 6|Set-Content "$RunRoot/controls.json" -Encoding utf8NoBOM
$results|Format-Table name,status -AutoSize
if($failed.Count){throw 'Test discovery controls failed'}
