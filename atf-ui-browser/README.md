# Adaptorul WEB Rentz

## Rolul adaptorului

Adaptorul livrează aceeași partidă Rentz în browser, folosind motorul J5 și shell-ul comun Card Table Browser. `GameSession` rămâne sursa regulilor, a comenzilor legale, a AI-ului și a scorului. Adaptorul transformă comenzile locale ale paginii în comenzi pentru sesiunea comună și proiectează numai informația publică; mâinile adversarilor nu sunt trimise către interfață.

```mermaid
flowchart LR
    U[Interfața WEB] -->|acțiune + revizie| P[Adaptor RentzPage]
    P --> S[BrowserSession comun]
    S --> G[GameSession J5]
    G --> C[game-core Rentz]
    G -->|observație publică| P
    P -->|JSON local| U
    U --> L[(localStorage: profil, progres, reluare)]
```

Shell-ul comun gestionează masa, dialogurile, comenzile cu revizie, animațiile și aspectul adaptiv. J5 păstrează conținutul specific Rentz: cele opt sub-jocuri, succesiunea cărților, punctajul, profilul, realizările și istoria.

### Secvența vizibilă a unei mâini Rentz

La o mutare umană, feedback-ul comun ridică mai întâi cartea selectată. Adaptorul proiectează apoi același snapshot în două locuri: cartea pleacă din mâna proprie, iar slotul public din secvența Rentz devine jucat. Pentru fiecare carte AI, locul activ rămâne vizibil după primul cadru și înaintea intervalului de 550 ms; tabla avansează cu o singură carte la fiecare pas. Snapshot-urile conțin doar mâna proprie și secvența publică, niciodată mâinile adversarilor.

```mermaid
sequenceDiagram
    participant J as Jucător
    participant W as Shell WEB comun
    participant A as Adaptor Rentz
    participant S as Sesiune Rentz
    J->>W: Selectează o carte legală
    W->>W: Ridică cartea
    W->>A: Trimite acțiunea și revizia
    A->>S: Aplică mutarea și avansează AI-ul
    S-->>A: Snapshot-uri publice înainte/după fiecare carte
    A-->>W: Playback ordonat, fără mâini adverse
    W->>W: Elimină cartea proprie și actualizează secvența
    loop Pentru fiecare carte AI
        W->>W: Marchează locul activ și așteaptă cadrul + 550 ms
        W->>W: Afișează următorul slot Rentz
    end
    opt Mâna Rentz s-a încheiat
        W->>W: Arată sosirea 1,8 s, apoi scorul confirmat
    end
```

## Contractul de compatibilitate cu APK-ul

Adaptorul WEB nu implementează o a doua versiune a jocului. Testele compilează aceleași surse `game-core` și `GameSession` pe care le folosește APK-ul, apoi verifică stratul de browser la granița publică. Sursa WEB proprie `PlayerNames` există numai în compilarea TeaVM: înlocuiește apelurile Unicode indisponibile în acel runtime, iar testul ei compară rezultatele direct cu biblioteca de nume folosită de APK. Codul nativ și APK-ul nu sunt schimbate de această adaptare.

| Strat | Verifică fără emulator | Ce protejează |
|---|---|---|
| Componente JVM | Validarea configurării comune; 3–6 jucători și Normal/Greu; reguli, acțiuni legale, treceri automate, rezultate și istoric; profil, realizări și checkpoint; contractele de nume și punctaj; ordinea snapshot-urilor Rentz și cele 550 ms după primul cadru al locului activ, comparate cu biblioteca UI Android fixată | Motorul, regulile și ritmul de decizie rămân aliniate cu APK-ul, iar proiecția WEB nu schimbă rezultatul sesiunii |
| Paritate cu biblioteca APK | NFC, limita de zece puncte Unicode, intrări valide/invalide și lista deterministă de adversari, comparate cu JAR-ul `card-table-names`; normalizarea profilului se compară și cu sursa Android `DisplayName` | Shim-urile necesare TeaVM nu schimbă contractul de profil Android |
| Componente pentru API-ul TeaVM | Pornire pentru 3–6 locuri și Normal/Greu; cărți ascunse până la dezvăluire; alegerea Rentz; ancora de deschidere pentru fiecare dimensiune de masă; mutări legale; o mână Rentz completă; sosirea Rentz înaintea scorului; punctaj, progres, reluare și următoarea mână | Codul compilat efectiv pentru browser se comportă ca implementarea JVM, fără DOM sau emulator |
| Componente DOM și transport local | Proiecția incrementală a secvenței după fiecare carte; eliminarea aceleiași cărți din mână când devine vizibilă pe masă; locul AI activ înaintea întârzierii; selectorul și antetul contractului selectat; sosirea Rentz de 1,8 secunde și revenirea la scor | Cardurile nu dispar toate la finalul răspunsului, iar shell-ul nu afișează panoul Whist „Atu” pentru sub-jocurile Rentz |
| Verificare în browser MCP | Încărcarea pachetului static, creare profil, pornirea unei mese, Rentz și o mutare legală | Integrarea dintre browser, worker și artefactele publicate local |

Aceste verificări înlocuiesc verificările de logică, stare, alegeri AI, scor, istoric, profil și realizări care altfel ar fi repetate prin emulator. Se folosesc aceleași surse de motor, aceleași comenzi `GameSession` și același JAR nativ de nume ca bază de comparație; testele nu simulează sau injectează rezultatele partidei.

La o acțiune Rentz, adaptorul trimite snapshot-uri publice pentru cartea umană și fiecare carte AI din acel răspuns. Shell-ul redă fiecare pas în ordinea jocului, iar antetul folosește cele două rânduri Android — progresul mâinii și contractul ales — fără titlu duplicat sau panou de atu. Redarea intermediară păstrează activă bifa de joc automat; doar sosirea terminală o oprește ca să păstreze vizibil scorul. La încheiere, adaptorul păstrează ultimul snapshot cu secvența completă pe masă, prezintă sosirea timp de 1,8 secunde, apoi afișează scorul confirmat. Playback-ul este tranzitoriu și nu intră în checkpoint sau profil; reluarea unei partide terminate afișează direct scorul.

Rămân dependente de un dispozitiv Android numai proprietățile platformei native: instalarea APK-ului, lifecycle-ul și restaurarea prin sistemul Android, inset-urile și fonturile furnizorului, gesturile tactile reale și performanța pe hardware. Ele nu sunt criterii pentru validarea logicii jocului în browser. Pagina WEB se califică separat prin testele de browser și prin publicarea reală.

## Compilare și verificări locale

Necesită PowerShell 7, JDK 21, Maven și Node.js 24. Toate clasele generate, cache-urile și paginile construite se scriu într-un director extern checkout-ului.

```powershell
.\atf-ui-browser\Test-RentzBrowserComponents.ps1 -WorkRoot C:\work\rentz_back\web-01\components
.\atf-ui-browser\Build-RentzPages.ps1 -WorkRoot C:\work\rentz_back\web-01\pages
```

`Test-RentzBrowserComponents.ps1` rulează testele JVM ale adaptorului și ale sesiunii comune. Build-ul WEB rulează, în ordine, testele JVM ale endpoint-ului, comparația cu biblioteca APK, compilarea TeaVM, verificările Node asupra motorului generat și verificările DOM. O eroare la oricare strat oprește build-ul.

## Pornire locală

Folosiți lansatorul și un build extern:

```powershell
.\atf-ui-browser\Start-RentzBrowser.ps1 -WorkRoot C:\work\rentz_back\web-01\local
```

## Publicare

Workflow-ul `.github/workflows/rentz-pages.yml` publică pachetul static din `main` numai după ce trec testele JVM native și verificările adaptorului și ale motorului compilat. Profilul și reluarea rămân în `localStorage`, pe dispozitivul browserului; pagina nu trimite datele într-un serviciu extern.

README-ul principal va lega jocul online după ce workflow-ul publică pagina și URL-ul trece verificarea anonimă. Un build local sau un URL de preview nu este adresă publică de joc.

## Surse

- [Regulile și arhitectura motorului Rentz](../rentz-lab-app/game-core/README.md)
- [JAR-ul comun Card Table Browser](lib/atf-ui-browser-0.2.5.jar) · [checksum](lib/atf-ui-browser-0.2.5.jar.sha256)
