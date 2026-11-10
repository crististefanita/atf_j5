# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md) · [Reperul J4](../../../atf_j4/docs/testing/ATF_ReleaseQualification.md)

## Legătura dintre Extent și descărcarea din README

**P1 · Dovada finală pe canal.** Verificatorul compară tagul, commitul README, ruta și octeții APK-ului. Pentru Extent consumă verdictul porților din JSON-ul RAG. [Verificatorul README](../../scripts/verify-release-readme.ps1)

Receipt-ul `readme-publication` păstrează SHA-ul APK-ului. Îi lipsesc hash-ul raportului RAG și locatorii cu hash ai fișierelor HTML care susțin verdictul.

- Verificați și fixați în dovada finală hash-ul calificării și locatorii cu hash ai rapoartelor Extent pentru același candidat. Păstrați accesul autorizat pentru dovezile restricționate. Un HTML absent, înlocuit sau provenit din altă ediție trebuie să blocheze verificarea chiar dacă tagul, copia APK și sumarul RAG sunt valide.

## Stabilitatea acceptanței Android

**P1 · Calificare pe profilul compact.** Confirmați pe emulator că mecanismul de revenire după dialogul de sistem permite continuarea din ierarhia proaspătă. Un dialog persistent trebuie să lase o dovadă `ANDROID_SYSTEM_UI_ANR`. [Contractul runnerului](../rentz/ATF_Android.md)

## Domeniul platformelor

**La introducerea unui release multiplatformă.** RAG-ul citează contractul platformei declarate și cere dovezile sale. Proba negativă prezintă numai APK și receipte Android pentru o livrare care declară și iOS. Răspunsul identifică platforma și dovada lipsă. [Contractul iOS](../rentz/ATF_Release.md#calificarea-ios) · [Feedback-ul iOS](ATF_Rentz.md#platforma-ios)
