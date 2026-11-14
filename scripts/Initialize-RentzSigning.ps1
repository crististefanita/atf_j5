#requires -Version 7.0
param([string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
$directory=Get-AtfSigningDirectory -ProjectRoot $project
$store=Join-Path $directory 'rentz-release.p12'
$credential=Join-Path $directory 'password.dpapi'
$identity=Join-Path $directory 'identity.json'
$present=@($store,$credential,$identity|Where-Object{Test-Path -LiteralPath $_})
if($present.Count-and$present.Count-ne3){throw 'Incomplete signing identity; preserve the files and recover explicitly.'}
if($present.Count-eq3){Get-Content -Raw -LiteralPath $identity;exit 0}
New-Item -ItemType Directory -Path $directory|Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
$acl=[Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true,$false)
foreach($principal in @($sid,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){
    $rule=[Security.AccessControl.FileSystemAccessRule]::new($principal,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $directory -AclObject $acl
$password=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$secure=ConvertTo-SecureString $password -AsPlainText -Force
$secure|ConvertFrom-SecureString|Set-Content -LiteralPath $credential -Encoding ascii
$previous=$env:RENTZ_STORE_PASSWORD
try{
    $env:RENTZ_STORE_PASSWORD=$password
    & (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -keystore $store -storetype PKCS12 -alias rentzlab-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Rentz Lab,OU=Local APK distribution' -storepass:env RENTZ_STORE_PASSWORD -keypass:env RENTZ_STORE_PASSWORD *> (Join-Path $directory 'provisioning.log')
    if($LASTEXITCODE-ne0){throw 'Signing key generation failed; inspect the owned provisioning log. Do not rotate automatically.'}
    $certificate=Join-Path $directory 'certificate.der'
    & (Join-Path $JavaHome 'bin/keytool.exe') -exportcert -alias rentzlab-release -keystore $store -storepass:env RENTZ_STORE_PASSWORD -file $certificate *> (Join-Path $directory 'certificate-export.log')
    if($LASTEXITCODE-ne0){throw 'Signing certificate export failed.'}
    [ordered]@{schemaVersion=1;alias='rentzlab-release';store=$store;credential=$credential;certificateSha256=(Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant();createdUtc=[datetime]::UtcNow.ToString('o');protection='Windows DPAPI, current user; private directory ACL'}|ConvertTo-Json|Set-Content -LiteralPath $identity -Encoding utf8NoBOM
    Get-Content -Raw -LiteralPath $identity
}finally{$env:RENTZ_STORE_PASSWORD=$previous;$password=$null;$secure=$null}
