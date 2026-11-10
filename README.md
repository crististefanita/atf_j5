# Rentz Lab

## Pe scurt

Laborator specializat în regulile Rentz și verificarea lor prin componente offline. Motorul local este independent de aplicația de referință `com.boonwiz.rentz`; regulile declarate, variantele modelate și ipotezele neconfirmate au statut explicit.

Motorul validează distribuirea, alegerea sub-jocului, mutările legale și scorul. Aplicația Android folosește același motor și adaugă masa de joc, salvarea, reluarea și profilul local.

## Ediție cu acces autorizat

- [Rentz Lab 0.13.0](https://github.com/crististefanita/atf_j5_releases/releases/tag/v0.13.0) · Cărțile jucabile în Rentz se disting printr-un contur verde discret.
- [Descarcă APK-ul Rentz Lab 0.13.0](https://github.com/crististefanita/atf_j5_releases/releases/download/v0.13.0/RentzLab-0.13.0.apk) · Android 8.0+. Autentifică-te în GitHub cu un cont care are acces la ediție.

## Module și responsabilități

| Zonă | Responsabilitate |
|---|---|
| [Motor Rentz](rentz-lab-app/game-core/README.md) | Cărți, levate fără atu, opt sub-jocuri, secvențe, rotație, scor și jucători Normal/Hard. |
| [Aplicație locală](rentz-lab-app/app/README.md) | Interfață nativă pe UI-ul comun, adaptoare Rentz, sesiune salvată, istoric și reluare. |
| [Testare și integrare](atf-tests/README.md) | Profil YAML tipizat, componente, arenă offline și acceptanța bibliotecilor comune. |
| [Kit](lib/README.TestKit.md#scope-și-integrare) | Clasificare, lifecycle JUnit și păstrarea comparațiilor evaluate. |
| [Observarea referinței](rentz-reference/README.md) | Identitatea aplicației originale și capturi Android cu rezervare exclusivă. |
| [Documentație Rentz](docs/rentz/README.md) | Reguli, proveniență, arhitectură, componente și transfer. |

## Traseul de lucru

```mermaid
flowchart LR
    SOURCE[Regulă și proveniență] -->|formalizează| CONTRACT[Contract Rentz]
    CONTRACT -->|exercită| CASE[Componentă mică]
    CASE -->|califică| MODEL[Motor offline]
    MODEL -->|precede| UI[Adaptor grafic]
```

<details>
<summary>Legendă: domeniul se califică înaintea interfeței</summary>

Un rezultat al modelului verifică regula implementată și profilul său. Echivalența cu aplicația originală cere confruntare separată; existența unui APK sau a unei componente verzi nu o stabilește automat.

</details>

## Pornire

Din rădăcina proiectului, în PowerShell 7, cu JDK 21, Maven, SDK Android și cache-urile Gradle/Maven pregătite:

```powershell
.\scripts\run-rentz-components.ps1
```

Intrarea implicită este colecția regression: componente Maven și Android JVM, fără dispozitiv. Pentru reguli fără SDK Android, selectați explicit quick prin parametrul -Collection.

Lansatorul execută o copie temporară și păstrează rezultatele în afara surselor. Profilul activ este de laborator; valorile originale „Classic” și „Traditional” rămân neconfirmate până la calificarea lor.

[Reguli](docs/rentz/ATF_Rules.md) · [Surse și certitudine](docs/rentz/ATF_RuleSources.md) · [Feedback activ](docs/feedback/ATF_Rentz.md)

[Jocul și evaluarea offline](docs/rentz/ATF_Learning.md) explică memoria publică, politicile, compararea distribuirilor și separarea dezvoltării de evaluarea nouă. O partidă a modelului nu confirmă automat comportamentul aplicației originale.

## Documentație

[Catalogul livrării](release/README.md) · [Procesul de release](docs/rentz/ATF_Release.md)

## Materiale păstrate separat

[Testele istorice Contract Whist](reference-tests/README.md) nu intră în reactorul Rentz. Rularea lor nu califică noul joc și nu face parte din fluxul activ.
