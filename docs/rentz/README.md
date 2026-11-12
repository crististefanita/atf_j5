# Regulile și testarea Rentz

## Pe scurt

Rentz combină sub-jocuri cu levate și construirea secvențelor pe culori. Modelul definește explicit profilul de punctaj și interpretările de regulă; fidelitatea față de referință se verifică separat.

## Ghiduri

| Contract | Ghid |
|---|---|
| Pregătire, mutări, închidere și scor | [Reguli](ATF_Rules.md) |
| Declarație a autorului, variantă și ipoteză | [Surse](ATF_RuleSources.md) |
| Stare, observație și politică | [Arhitectură](ATF_Architecture.md) |
| Proprietar, checkpoint și calcul suspendabil | [Lifecycle](ATF_Lifecycle.md) |
| Alegerea nivelului și mutarea verificărilor | [Strategia de testare](ATF_TestingStrategy.md) |
| Cazuri mici, frontiere și oracole | [Componente](ATF_Components.md) |
| Adopție binară, colecții, procese și publicare | [Calificare offline](ATF_Qualification.md) |
| Politici, memorie publică, lumi ipotetice și evaluare | [Învățare și joc offline](ATF_Learning.md) |
| Sesiune nativă, identitate APK și acceptanță delimitată | [Android](ATF_Android.md) |
| Admiterea bibliotecii grafice și adaptoare specifice | [Integrarea UI](ATF_UiIntegration.md) |
| Inventar, workspace-uri și curățare verificabilă | [Infrastructură](ATF_Infrastructure.md) |
| Reutilizare a infrastructurii | [Transfer](ATF_Transfer.md) |

## Modificarea unei reguli

1. Identificați regula, varianta și sursa în [manifest](../../config/rentz-rules.yaml).
2. Formulați comportamentul, refuzul relevant și limita domeniului.
3. Modificați componenta și motorul; confruntați independent observația și scorul.
4. Actualizați documentul responsabil și păstrați rezultatele extern.

[Proiect](../../README.md) · [Motor](../../rentz-lab-app/game-core/README.md) · [Feedback](../feedback/ATF_Rentz.md)

[Livrarea și distribuția pe platforme](ATF_Release.md) descrie stările, semnarea și dovezile cerute fiecărui artefact.
