# Feedback și responsabilități

## Pe scurt

[Registrul Rentz](ATF_Rentz.md) păstrează lacunele active ale regulilor, modelului și integrării. O regulă stabilă are document responsabil în [aria Rentz](../rentz/README.md); rezultatele concrete rămân în depozitul extern.

- [Interfața Rentz](ATF_Rentz_UI_ToDo.md) urmărește diferențele rămase de interacțiune, geometrie și scor, cu probe de componentă și acceptanță nativă selectivă. [Biblioteca grafică](../../rentz-lab-app/ui-library/README.md) și adaptoarele Rentz au verificări separate.
- [Integritate și calitate](ATF_RentzQuality.md) deține criteriile strategice și documentare încă deschise. Validarea, lifecycle-ul, guvernanța sursei și [calificarea consumatorului](../rentz/ATF_Qualification.md) au contracte și probe proprii; calificarea nativă rămâne separată.
- [Guvernanța RAG](ATF_Release_RAG.md) păstrează verificările de regresie remediate; [ghidul de release](../rentz/ATF_Release.md#ordinea-calificării-android-curente) deține porțile încă necesare pentru fiecare APK.
- [Extinderea realizărilor](ATF_Rentz_Achievements_ToDo.md) urmărește obiectivele candidate, sincronizarea și probele de accesibilitate rămase. [Integrarea actuală](../rentz/ATF_UiIntegration.md#realizări-și-profil-local)

## ToDo-uri active: prioritate /100

Scorul este prioritatea de remediere, nu nota calității sau procentul de acoperire. Formula este impact/40 + lacună/30 + lucrabilitate/20 + datorie documentară/10; numerele dintre paranteze arată contribuțiile și suma lor. P1 rămâne înaintea P2, iar P2 înaintea P3; scorul ordonează doar restanțele din aceeași clasă.

- **Impact, maximum 40:** efectul asupra corectitudinii jocului, datelor sau unei porți obligatorii.
- **Lacună, maximum 30:** cât din criteriul de acceptanță încă lipsește.
- **Lucrabilitate, maximum 20:** punctaj mai mare când reproducerea și remedierea sunt delimitate și au owner; mai mic când depind de acces extern sau de o decizie de scop.
- **Datorie documentară, maximum 10:** ambiguitatea sau contradicția din sursa de cerințe și criteriul de închidere.

| Ordine | Prioritate și criteriu | Prioritate /100 | Restanță concretă |
|---:|---|---:|---|
| 1 | **P1 · Acceptanță nativă a mesei, turei și scorului** · [backlog UI](ATF_Rentz_UI_ToDo.md) | 92 (40+30+14+8) | Închideți U01–U07, R02–R05 și S01/S04: compact/font mărit, 3/5/6 locuri, o mutare vizibilă per actor, sosiri, tabla terminală, egalități, contoare și derularea istoricului pe APK-ul candidat. |
| 2 | **P1 · Regulile și valorile versiunii originale** · [registrul regulilor](ATF_Rentz.md) | 84 (36+26+14+8) | Confirmați independent preset-ul, valorile neetichetate și frontierele execuției originale; declarațiile din instrucțiuni nu țin loc de rulare verificată. |
| 3 | **P1 · Bifa Automat și ritmul delegării** · [backlog UI](ATF_Rentz_UI_ToDo.md) | 81 (33+25+15+8) | Adoptați contractul comun al antetului, confirmați reluarea după anulare și o singură mutare per cadru, apoi calificați cadrul nativ al actorului și anularea la navigare. |
| 4 | **P1 · Pornirea aplicației de referință** · [registrul regulilor](ATF_Rentz.md) | 74 (31+28+9+6) | Investigația ecranului WebView negru încă cere cauză și probă reproductibilă de pornire funcțională. |
| 5 | **P1 · Identitate, progres și accesibilitatea realizărilor** · [backlog realizări](ATF_Rentz_Achievements_ToDo.md) | 74 (30+21+15+8) | Baza locală există; rămân importul/sincronizarea profilului, proveniența, migrarea pragurilor și fusurilor, textul mărit și navigarea asistivă a catalogului. |
| 6 | **P1 · Suport iOS: decizie de scop și calificare** · [registrul regulilor](ATF_Rentz.md) | 66 (20+25+14+7) | Decideți dacă platforma rămâne în scop; dacă da, adăugați țintă nativă, build și receipte independente. APK-ul Android nu califică iOS. |
| 7 | **P2 · Promovarea AI și calibrarea lumilor** · [calitatea Rentz](ATF_RentzQuality.md) | 82 (33+26+15+8) | Continuarea completă offline există; promovarea cere comparație independentă pe loturi noi, sampler documentat și transportul registrului public în integrare. |
| 8 | **P2 · Variante de reguli și program configurabil** · [registrul regulilor](ATF_Rentz.md) | 77 (26+25+18+8) | „Totale plus” și sub-jocuri dezactivate nu sunt implementate; definiți contracte și teste pe fiecare variantă. |
| 9 | **P2 · Politica numelui în dialogul comun** · [backlog UI](ATF_Rentz_UI_ToDo.md) | 72 (25+22+16+9) | Proprietarul J4 trebuie să alinieze filtrul UTF-16 la politica de caractere a consumatorului, fără a schimba validarea Rentz locală. |
| 10 | **P2 · Compatibilitatea sesiunilor și migrarea scorului** · [registrul regulilor](ATF_Rentz.md) | 67 (23+20+16+8) | Sesiunile vechi și opțiunile/profilurile noi au nevoie de migrare fără pierderea sau reinterpretarea datelor. |
| 11 | **P2 · Gesturile pe antetul fix al istoricului** · [backlog UI](ATF_Rentz_UI_ToDo.md) | 65 (24+18+15+8) | Verificați tragerea și fling-ul pornite de pe antet fix; tragerea derulează, iar atingerea deschide exact un singur detaliu. |
| 12 | **P2 · Împachetarea guvernanței reutilizabile** · [contractul generic](README.md#contract-generic-pentru-guvernanță) | 61 (20+18+15+8) | Adaptorul local și probele există; lipsește API-ul comun împachetat și acceptanța într-un consumator mixt Maven/Gradle. |
| 13 | **P2 · Lansatorul de prototip și ruta UI** · [backlog UI](ATF_Rentz_UI_ToDo.md) | 60 (20+18+14+8) | Evaluați migrarea selecției la actorul public; păstrați replay-ul independent și refuzul controlat al rutei fără ADB. |
| 14 | **P2 · Reconcilierea copiilor istorice externe** · [guvernanța infrastructurii](ATF_Rentz.md) | 56 (16+20+12+8) | Faceți inventarul proprietarilor și referințelor, verificați arhiva și dovediți că niciun receipt sau classpath activ nu depinde de copia retrasă. |
| 15 | **P3 · Portabilitatea exemplului Test Kit** · [adopția Test Kit](../../lib/README.TestKit.md) | 54 (15+16+15+8) | Verificați exemplul într-un consumator autonom și prin import IntelliJ cu același cache izolat. |

**Citirea scorurilor:** 90–100 cere atenție imediată; 75–89 indică risc mare sau impact extins; 50–74 indică remediere planificabilă; sub 50 indică impact sau lucrabilitate redusă în comparația curentă. Nota nu închide cerința: aceasta se închide numai prin criteriul din documentul ownerului și receipt-ul aferent.

**Starea release-ului separat:** RAG-ul de cod și guvernanță nu are ToDo de remediere deschis în [registrul RAG](ATF_Release_RAG.md). Cea mai recentă evaluare disponibilă confirmă regresia offline, Extent-ul componentelor, Android JVM și catalogul local; acceptanța nativă este `NOT_RUN`, deci acel snapshot nu autorizează un release nou.

## Închidere și transfer

- Închideți un criteriu numai cu sursă și verificare identificate.
- Promovați mecanismul stabil în documentul responsabil înaintea eliminării explicației din feedback.
- Păstrați separat regulile jocului, datele unei rulări și defectele bibliotecii comune.

## Biblioteci comune

Producătorul ATF deține codul, testele native și împachetarea bibliotecilor comune. O cerință către producător include versiunea JAR, SHA-256, reproducerea, așteptarea, observația și criteriul de acceptanță. [Contractele binare și probele locale](../../lib/README.md)

## Materiale istorice

[Arhiva Contract Whist](../history/contract-whist/README.md) păstrează separat observațiile și feedback-urile anterioare, cu inventar și retenție verificabile. Aceste instantanee nu intră în calificarea Rentz.

## Contract generic pentru guvernanță

**P2 · integrare împachetată solicitată responsabilului bibliotecilor comune.** Consumatorul adaptează numai validatorii de guvernanță, cu identitatea sursei și a adaptării în manifest; nu copiază implementările Core, Reporting sau Kit.

API-ul comun trebuie să primească rădăcina explicită, inventarul activ și politica de retenție, să expună diagnostice pe fișier și regulă și să permită controale negative fără alt reactor. Acceptanța cere același inventar, aceleași refuzuri și vecinul valid înaintea înlocuirii adaptorului local; versiunea și proveniența JAR/POM se verifică separat.

## Claritatea exemplelor de adopție

**P3 · probă de portabilitate.** [Ghidul J5](../../lib/README.md#integrare) indică acum zona de lucru și repository-ul Maven explicit. Rămâne verificarea fragmentului într-un consumator autonom, cu cache dedicat, apoi importul IntelliJ din același cache. Acceptanța binarelor J5 are propria poartă.

## Inventar executabil și runtime comun

La împachetarea guvernanței, API-ul comun trebuie să păstreze inventarul AST pentru clasificare și selecție, cu rădăcină, source-set și apartenență la build declarate. Refuzul formelor neconforme și al octeților modificați ai bootstrap-ului are probe independente la consumator.

Calificarea runtime-ului cere lista ordonată a classpath-ului, inventarul recursiv de clase și resurse, atestarea la granițele testelor și verificarea aceleiași așteptări înainte/după lansare. Aria rămâne infrastructură de test; domeniul Rentz, observația jucătorului și semantica scorului nu se extrag în biblioteca comună.
