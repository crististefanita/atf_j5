# Observarea aplicației Rentz de referință

## Pe scurt

Acest director deține observații și instrumente pentru pachetul oficial `com.boonwiz.rentz`. APK-ul, instrucțiunile și capturile sunt surse de probă; motorul local rămâne independent de codul și activele aplicației.

## Contractul instrumentului

[RentzAndroid](tools/RentzAndroid.psm1) verifică rezervarea comună înaintea fiecărei comenzi. Observația cere un fișier XML nou și confirmarea producerii lui, apoi păstrează PNG-ul original; eroarea capturii nu autorizează citirea observației anterioare.

| Nivel | Semnificație |
|---|---|
| Identitate instalată | Pachet, versiune, installer, split-uri și semnături. |
| Observație nativă | Stare și captură ale dispozitivului identificat. |
| Regulă confirmată | Instrucțiune și frontieră confruntate cu un oracol independent. |

Instalarea nu certifică funcționarea sau fidelitatea motorului local. [Criteriile active](../docs/feedback/ATF_Rentz.md) păstrează compatibilitatea, lipsa regulilor confirmate și calificarea instrumentelor; explorarea grafică se activează separat de componente.
