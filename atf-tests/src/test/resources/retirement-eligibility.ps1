param([string]$Project,[string]$Output)
$ErrorActionPreference='Stop'
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $Project 'scripts/verify-release-readme.ps1'),[ref]$null,[ref]$null)
$function=$ast.FindAll({param($node) $node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq'Confirm-RetirementChannel'},$true)
if($function.Count-ne1){throw 'Exactly one source-owned eligibility policy required.'}
. ([scriptblock]::Create($function[0].Extent.Text))
$rows=foreach($case in @('public','private','other-repository','stale-public','fractional-id','missing-id','stale-private-readme','drive','old-catalog')){
 $restricted=$case-in@('private','stale-private-readme','drive')
 $catalog=[pscustomobject]@{currentVersion='0.29.0'}
 $proof=[pscustomobject]@{authenticated=@{provider='GitHub'};latestTag='v0.29.0';readmeCommit='head'}
 $github=[pscustomobject]@{repository='example/rentz';latest='v0.29.0';releaseId=29}
 switch($case){
  'other-repository' {$github.repository='other/rentz'}
  'stale-public' {$github.latest='v0.28.0'}
  'fractional-id' {$github.releaseId=29.5}
  'missing-id' {$github.releaseId=0}
  'stale-private-readme' {$proof.readmeCommit='stale'}
  'drive' {$proof.authenticated.provider='Google Drive'}
  'old-catalog' {$catalog.currentVersion='0.28.0'}
 }
 $expected=$case-in@('public','private')
 $actual=Confirm-RetirementChannel $restricted $catalog $proof $github '0.29.0' 'v0.29.0' 'head' 'example/rentz'
 [pscustomobject]@{case=$case;expected=$expected;actual=$actual}
}
$rows|ConvertTo-Json -Depth 4|Set-Content -LiteralPath $Output -Encoding utf8NoBOM
