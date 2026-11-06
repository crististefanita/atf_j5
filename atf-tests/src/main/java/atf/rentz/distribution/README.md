# Contractele distribuției Rentz

## Pe scurt

Acest pachet verifică identitatea declarată și achiziția APK-ului; nu implementează un renderer, o semnătură Android sau instalarea aplicației.

## Rol

| Componentă | Responsabilitate |
|---|---|
| ReleaseCatalog | Stările candidatului, Gradle, legăturile curente și inventarul dosarului. |
| ApkDownloadTarget | Repository, versiune, bytes integrali și SHA-256 declarate înaintea accesului. |
| ReleaseDownloadAcceptance | Intrare CLI live explicită, cu șase comparații și Extent; nu se execută implicit în regresie. |
| GitHubApkDownload | Metadata, redirecturi limitate și transfer complet; credentiala rămâne numai la originea API. |
| ApkPayload și ReleasePaths | Hash, CRC pentru toate intrările ZIP și căi canonice, fără promovarea textului de eroare la APK. |

Componentele folosesc transport loopback real și martori ZIP mici; acestea nu sunt APK-uri instalabile. Acceptanța live se selectează explicit după publicare, iar [procesul release](../../../../../../../docs/rentz/ATF_Release.md) separă dovada Android și browserul.
