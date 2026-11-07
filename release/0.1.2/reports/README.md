# Calificarea Rentz Lab 0.1.2

## Pe scurt

Dosarul leagă APK-ul Release semnat de commit-ul calificat și de verificările efectuate în workspace-uri externe.

| Selecție | Invocări sau comparații | Dovadă |
|---|---:|---|
| Motor Maven | 111 | [Reguli Extent](component/rentz-rules.html) |
| ATF Maven | 231 | [Guvernanță release](distribution/release-governance.html) · [Politică Git](distribution/release-git-policy.html) |
| Android JVM | 54 | [Populații](qualification.json) |
| Android API 28, APK izolat | 63 | [Istoric și reluare Extent](ui/history-extent.html) · [Randare](ui/rendering.json) |
| Transport APK offline | 19 teste incluse în ATF | [Componente de descărcare](distribution/download-components.html) |

## Proveniență

- [Intrările APK](apk-inputs.json) au fost confruntate cu [bloburile commit-ului](git-build-identity.json).
- [Randarea componentelor](component-rendering.json) confirmă expandarea și accesul la ultima aserțiune.
- [Controalele de drift](source-controls.json) refuză modificări urmărite, fișiere noi și căi din afara workspace-ului.
- APK-ul izolat pentru UI și APK-ul Release sunt construite din aceleași intrări. Verificarea Android acoperă istoricul și reluarea pe profilul executat.
- Rapoartele Extent păstrează comparațiile evaluate. Execuția build-ului repetă testele JVM și nu mărește populația primară.

[Procesul livrării](../../../docs/rentz/ATF_Release.md)

## Descărcare

- [Extent API](distribution/download-api.html) verifică statusul HTTP, dimensiunea, SHA-256 și toate CRC-urile APK-ului complet.
- [Recepția GitHub](github-release.json) leagă cele două fișiere de tag și de commit.
- [Extent browser](distribution/download-readme.html) verifică descărcarea completă pornită din README-ul GitHub autentificat. [Receipt](distribution/download-readme.json)
