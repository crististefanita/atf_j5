# Arhiva documentară Contract Whist

## Pe scurt

Această arhivă păstrează documentele unui domeniu anterior. Ele sunt instantanee de trasabilitate, fără autoritate asupra regulilor, lansatoarelor sau acceptanței Rentz.

## Inventar și retenție

[Politica documentației](../../../config/documentation-policy.json) clasifică fiecare instantaneu, calea inițială și hash-urile înainte și după relocare. Textul arhivat păstrează afirmațiile originale; numai linkurile relative sunt adaptate la noul director.

| Material | Rol istoric |
|---|---|
| [Instrucțiuni](ORIGINAL_INSTRUCTIONS_COVERAGE.md) | Lectura regulilor aplicației anterioare. |
| [Strategie Android](ATF_Android_Whist_Testing.md) | Organizarea observării și a testelor anterioare. |
| [Protocol de partidă](ORIGINAL_ROMANIAN_HARD_LIVE.md) | Limitele acceptanței prin interfață în domeniul anterior. |
| [Feedback de reluare](ORIGINAL_ROMANIAN_LIVE_Feedback.md) | Constatări istorice privind sesiunea. |
| [Partidă-model](REFERENCE_MATCH_Feedback.md) | Trasabilitatea scenariului anterior. |

## Separarea de contractele active

| Zonă | Regulă |
|---|---|
| Ghiduri active | Guvernanță de stil, linkuri, diagramă și comenzi confruntate cu sursa. |
| Instantanee | Inventar complet și hash stabil; conținutul vechi nu se rescrie pentru a imita contractele noi. |
| Dovezi externe | Rămân la destinațiile originale; lipsa unei destinații nu se maschează printr-un rezultat nou. |
| Eliminare | Numai după revizuirea explicită a referințelor și a arhivei; nu prin curățare automată. |

Originalele nemodificate și jurnalul relocării se păstrează în depozitul extern de guvernanță. [Testul contractului](../../../atf-tests/src/test/java/atf/rentz/governance/DocumentationContractTest.java) refuză un instantaneu neinventariat, un hash schimbat sau revenirea sa în traseul activ.

[Documentație Rentz](../../README.md) · [Surse istorice](../../../reference-tests/README.md)
