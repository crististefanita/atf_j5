param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
Import-Module (Join-Path $PSScriptRoot '../../rentz-reference/tools/RentzAndroid.psm1') -Force
if(Test-Path -LiteralPath $RunRoot){throw 'Fresh selector component output required.'}
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$checks=[Collections.Generic.List[object]]::new()
function Check([string]$Name,$Expected,$Actual){
    $pass=[string]$Expected-ceq[string]$Actual
    $checks.Add([pscustomobject]@{name=$Name;expected=[string]$Expected;actual=[string]$Actual;passed=$pass})
    if(-not$pass){throw "Selector assertion failed: $Name"}
}
$failure=''
try{
    [xml]$picker='<hierarchy><node class="android.widget.ListView"><node text="Choice" content-desc="" class="android.widget.CheckedTextView" enabled="true" clickable="false" bounds="[10,20][50,60]"/></node></hierarchy>'
    $target=Get-RentzTapTarget -Nodes @($picker.SelectNodes('//node')) -Label 'Choice'
    Check 'Native AdapterView item is actionable even when its child is not clickable' '[10,20][50,60]' $target.bounds
    $picker.SelectSingleNode('//node/node').SetAttribute('enabled','false')
    Check 'Disabled list item is rejected' $true ($null-eq(Get-RentzTapTarget -Nodes @($picker.SelectNodes('//node')) -Label 'Choice'))
    [xml]$passive='<hierarchy><node text="Choice" content-desc="" class="android.widget.TextView" enabled="true" clickable="false" bounds="[10,20][50,60]"/></hierarchy>'
    Check 'Passive label outside an AdapterView is not a control' $true ($null-eq(Get-RentzTapTarget -Nodes @($passive.SelectNodes('//node')) -Label 'Choice'))
    [xml]$ambiguous='<hierarchy><node text="Choice" content-desc="" class="android.widget.Button" enabled="true" clickable="true"/><node text="Choice" content-desc="" class="android.widget.Button" enabled="true" clickable="true"/></hierarchy>'
    $rejected=$false;try{$null=Get-RentzTapTarget -Nodes @($ambiguous.SelectNodes('//node')) -Label 'Choice'}catch{$rejected=$_.Exception.Message-like'Ambiguous native control:*'}
    Check 'Two matching targets fail rather than choosing one' $true $rejected
}catch{$failure=$_.Exception.ToString()}
[ordered]@{class='atf.rentz.android.NativeUiSelectors';method='adapterViewItemsAndNegativeNeighbours';status=$(if($failure){'FAIL'}else{'PASS'});failure=$failure;checks=$checks.ToArray()}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $RunRoot 'assertions.json') -Encoding utf8NoBOM
if($failure){throw $failure}
