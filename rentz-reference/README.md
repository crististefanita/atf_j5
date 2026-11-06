# Observarea aplicației Rentz de referință

## Pe scurt

Acest director deține observații și instrumente pentru pachetul oficial `com.boonwiz.rentz`. APK-ul, instrucțiunile și capturile sunt surse de probă; motorul local rămâne independent de codul și activele aplicației.

## Contractul instrumentului

[RentzAndroid](tools/RentzAndroid.psm1) verifică rezervarea comună înaintea fiecărei comenzi. Observația cere un fișier XML nou și confirmarea producerii lui, apoi păstrează PNG-ul original; eroarea capturii nu autorizează citirea observației anterioare.

| Nivel | Semnificație |
|---|---|
| Identitate instalată | Pachet, versiune, installer, split-uri și semnături. |
| Observație nativă | Stare și captură ale dispozitivului identificat. |
| Declarație publicată | `PUBLISHER_DECLARED`: descrierea autorului; nu dovedește execuția regulii pe dispozitiv. |
| Declarație în APK | `PUBLISHER_APK_DECLARED`: resursă legată de hash și cheie; lectura resursei nu este o observație de runtime. |
| Observație în execuție | Acțiune, stare și captură native cu identitate; califică numai frontiera și configurația observate. |
| Interpretare de laborator | Decizie explicită de modelare, distinctă de declarație și de observație; cere confirmare unde manifestul o indică. |

[Manifestul regulilor](../config/rentz-rules.yaml) leagă declarațiile de resurse, cod și componente; [proveniența](../docs/rentz/ATF_RuleSources.md) păstrează necunoscutele de preset și punctaj. Legătura către un test nu înseamnă că regula originalului a fost observată în execuție.

Instalarea nu certifică funcționarea sau fidelitatea motorului local. [Criteriile active](../docs/feedback/ATF_Rentz.md) păstrează compatibilitatea, lipsa regulilor confirmate și calificarea instrumentelor; explorarea grafică se activează separat de componente.
