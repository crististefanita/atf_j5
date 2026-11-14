# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md)

## Varietatea contractelor în acceptanța Android

**P1 · Responsabil: acceptanța Android J5.** Acceptanța pornește prin interfață o partidă nouă cu o distribuție dependentă de ceas. Oponenții pot alege același contract în mâinile a doua și a treia. Runnerul poate raporta PASS pentru cele trei mâini încheiate, iar RAG refuză varietatea cu diagnosticul RAG-UI-CONTRACT-VARIETY.

- Folosiți pentru varianta izolată de acceptanță o sămânță documentată și verificată la nivel de componentă, pe care politica reală alege două contracte standard diferite după Rentz. Păstrați pornirea partidei și mutările prin interfață, plus distribuția obișnuită în produs.
- Verificați înainte de raportarea PASS că cele trei contracte sunt distincte. Proba negativă păstrează rezultatele și capturile celor trei mâini corect jucate și întoarce un diagnostic explicit pentru contractul repetat.
- Verificați că aceeași sămânță produce același traseu la două porniri independente și că receipt-ul, Extent și replay-ul identifică aceeași succesiune. [Poarta de calificare](../rentz/ATF_Release.md#ordinea-calificării-android-curente)

## Domeniul platformelor

**La introducerea unui release multiplatformă.** RAG-ul citează contractul platformei declarate și cere dovezile sale. Proba negativă prezintă numai APK și receipte Android pentru o livrare care declară și iOS. Răspunsul identifică platforma și dovada lipsă. [Contractul iOS](../rentz/ATF_Release.md#calificarea-ios) · [Feedback-ul iOS](ATF_Rentz.md#platforma-ios)

## Retenția spațiilor de lucru

**P2 · Responsabil: infrastructura J5.** Extindeți inventarul read-only la cache-uri și fixture-uri, cu aceleași motive de păstrare și dovezi pe fiecare cale. Inventarul pentru `target` are deja un contract propriu.

- Dovediți că nicio intrare de classpath atestată nu dispare când retrageți un cache duplicat.
- Diferențiați un fixture de guvernanță cu receipt util de o copie regenerabilă. Includeți o sursă de dovezi inaccesibilă și un producător care scrie în timpul inventarierii. [Contractul de retenție](../rentz/ATF_Infrastructure.md#inventarul-țintelor-maven)
