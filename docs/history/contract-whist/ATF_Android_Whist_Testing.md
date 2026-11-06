# Testarea aplicației-model Contract Whist

J5 deține verificarea aplicației instalate com.qsoft.android.whist și
interpretarea probelor produse de ea. Motorul offline, aplicația Android și
E2E-urile care acționează controalele Whist Lab rămân în J4.

## Organizare și proveniență

| Zonă | Conținut |
|---|---|
| reference-tests/src/test/java | E2E live, observarea backup-urilor, oracole și teste de continuare pentru aplicația-model. |
| docs/ORIGINAL_INSTRUCTIONS_COVERAGE.md | Text observat și limitele interpretării sale. |
| scripts/run-original-romanian-hard.ps1 | Componente offline și Romanian Hard live, cu activare separată. |
| scripts/run-reference-match.ps1 | Partidă de referință Oh Hell și fixture complet pentru replay-ul Whist Lab. |

Runner-ele pregătesc sursele și rezultatele intermediare în directoare
temporare. Capturile, Surefire XML, fixture-urile și Extent HTML se scriu în
directorul extern al rulării. Nicio rulare live nu este pornită implicit.

Dependențele Maven se copiază o singură dată în cache-ul offline
ATF_J5_MAVEN_CACHE din directorul temporar; output-ul Maven rămâne în staging-ul
temporar al execuției.

## Componente offline

Fără -WithAndroid, runnerul Romanian Hard nu trimite comenzi ADB și nu începe
o partidă. El verifică parserul de backup, auditul independent al regulilor,
decizia limitată la mâna proprie și informația publică, receipt-ul acțiunii
acceptate și replay-ul trace-ului. Rezultatul offline nu certifică selectorii,
textul sau geometria UI-ului instalat.

## Acceptanță live și fixture-ul pentru J4

run-reference-match.ps1 cere simultan -WithAndroid și
-ExclusiveDeviceConfirmed. Înaintea oricărui input verifică ADB, pachetul
com.qsoft.android.whist și versionCode 145, apoi revendică rezervarea comună a
dispozitivului. Fără rezervare activă, testul nu pornește; dacă apare o
partidă salvată neasociată cu checkpoint-uri de continuare, testul se oprește
fără să o înlocuiască.

O partidă completă produce original-match.properties, 19 snapshot-uri
original-hNN-bidding.save2, trace-ul ordonat și un raport
original-match-extent-report.html. Runnerul validează 19 mâini, 436 mutări,
109 levate, rezultatul nativ și existența imaginilor inline în raport.

Pentru continuare se indică directorul unei rulări incomplete:

.\scripts\run-reference-match.ps1 -WithAndroid -ExclusiveDeviceConfirmed -ContinueFrom <directorul-rulării-anterioare>

Sursa rămâne nemodificată; continuarea folosește o destinație nouă și verifică
starea jocului curent înaintea oricărui input. O execuție completă se auditează,
nu se replay-ează automat.

J4 consumă numai fixture-ul înregistrat pentru a testa propriul motor și
propria interfață. Verdictul J5 certifică aplicația-model; verdictul replay-ului
J4 certifică separat Whist Lab. Un raport nu este PASS pentru celălalt proiect.

[Contractul jocului Whist Lab](../../../../atf_j4/docs/CONTRACT_WHIST_RULE_RECONSTRUCTION.md) ·
[Testele Android Whist Lab](../../../../atf_j4/atf-test-android/README.md) ·
[Feedback J5](../../feedback/README.md)