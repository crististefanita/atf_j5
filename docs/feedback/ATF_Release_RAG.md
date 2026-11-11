# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md) · [Reperul J4](../../../atf_j4/docs/testing/ATF_ReleaseQualification.md)

## Legătura dintre Extent și descărcarea din README

**P1 · Dovada finală pe canal.** Verificatorul compară tagul, commitul README, ruta și octeții APK-ului. Pentru Extent consumă verdictul porților din JSON-ul RAG. [Verificatorul README](../../scripts/verify-release-readme.ps1)

Dovada finală păstrează SHA-256 al APK-ului și digestul JSON-ului RAG în `retirement.qualificationSha256`. Verificatorul citește verdictul RAG, iar confruntarea directă a rapoartelor Extent rămâne deschisă.

- Confruntați lungimea, SHA-256 și identitatea fiecărui raport Extent înainte de retragerea ediției anterioare. Păstrați locatorii verificați în dovada finală și folosiți accesul autorizat.
- Proba negativă păstrează APK-ul și JSON-ul RAG valide. Un raport lipsă, alterat sau din altă ediție blochează retragerea și indică fișierul afectat.

## Domeniul platformelor

**La introducerea unui release multiplatformă.** RAG-ul citează contractul platformei declarate și cere dovezile sale. Proba negativă prezintă numai APK și receipte Android pentru o livrare care declară și iOS. Răspunsul identifică platforma și dovada lipsă. [Contractul iOS](../rentz/ATF_Release.md#calificarea-ios) · [Feedback-ul iOS](ATF_Rentz.md#platforma-ios)
