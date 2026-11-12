# Console — diagnostic pentru execuție

## Pe scurt

`atf-console` proiectează evenimentele ATF în stdout/stderr: lifecycle, durate și detalii configurabile. Este util pentru urmărirea unei rulări Maven sau IntelliJ; artifactul HTML este produs de [Reporting](README.Reporting.md).

## Exemplu minim

Fragment într-o metodă; politica este explicită, iar stream-ul rămâne în adaptor:

```java
import atf.console.AtfConsoleReporter;
import atf.console.ConsoleSettings;

var console = AtfConsoleReporter.configuredStdout(
        new ConsoleSettings(true, false));
console.info("Catalogul de scenarii a fost încărcat");
```

`configuredStdout()` citește `atf.report.config` → `ATF_REPORT_CONFIG` → `atf.yaml/yml`; opțiunile sunt:

```yaml
reporting:
  console:
    enabled: true
    detailsEnabled: false
```

## Integrare și opțiuni

| Intenție | Alegere |
|---|---|
| Mesaj independent de detalii | `info(...)` |
| Evenimente typed, cu lifecycle și durată | `publish(AtfConsoleEvent)` prin portul Core `AtfConsoleSink` |
| Consolă complet oprită | `enabled: false` sau `ConsoleSettings.disabled()` |
| Calea raportului final în consolă | Constructorul cu `reportPathEnabled: true` și evenimentul `REPORT_PUBLISHED` |
| Politică JUnit per clasă | Adaptorul JUnit Console și adnotările Test Kit; declarați Kit explicit |

Calea cere opt-in, consolă activă și eveniment cu artifact confirmat; `detailsEnabled` controlează doar detaliile.

Console depinde de Core; Kit este opțional pentru legătura JUnit. Proiecția rămâne diagnostică: comparația și verdictul aparțin testului.

## Verificarea utilizării

| Acțiune | Rezultat observabil |
|---|---|
| Executați exemplul cu politica explicită. | Mesajul furnizat apare în stdout, inclusiv cu detaliile dezactivate. |
| Dezactivați consola prin `ConsoleSettings.disabled()`. | Un nou apel `info(...)` nu produce mesaj în stream. |
| Activați numai afișarea căii. | Calea apare numai după evenimentul de publicare confirmată; opțiunea nu creează raportul. |

Compilarea fragmentului califică semnătura API pe JAR-ul ales. Verificarea stream-ului califică proiecția mesajului; consola nu schimbă rezultatul testului sau regulile aplicației.

---

Acest ghid descrie folosirea JAR-ului, nu sursa lui. Proveniența și hash-urile binarului sunt în [manifestul local](../library-provenance.json); lacunele și acceptanța consumerului J5 sunt în [registrul local](../docs/feedback/README.md#biblioteci-comune).
