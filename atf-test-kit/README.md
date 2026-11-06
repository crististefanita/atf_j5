# Kit comun pentru testare

## Pe scurt

Kit clasifică și auditează teste JUnit. Codul și feedback-ul au owner J0; modulul rămâne sursă până la adopția JAR-ului. Oracolul și publicarea aparțin consumerului.

## Contracte

| Mecanism | Sursă |
|---|---|
| Comparații evaluate | [AuditedAssertions](src/main/java/atf/testkit/assertions/AuditedAssertions.java) |
| Rezultat pe invocare și fază | [ComponentAssertionAudit](src/main/java/atf/testkit/assertions/ComponentAssertionAudit.java) |
| Legătura JUnit și publicare | [ComponentAuditExtension](src/main/java/atf/testkit/assertions/ComponentAuditExtension.java) |
| Proprietarul resursei | [TestRunScope](src/main/java/atf/testkit/scope/TestRunScope.java) |

## Verificare și folosire

Dintr-o copie temporară completă, cu JDK 21, Maven și cache:

```powershell
mvn -B -o -pl atf-test-kit -am test
```

[Testele modulului](src/test/java/atf/testkit/) califică contractele generice. [Consumatorul Rentz](../atf-tests/README.md) cere comparații în corpul testului și mapează rezultatul către bibliotecă; nivelul și semantica jocului rămân distincte.

## Limite

O resursă partajată nu partajează implicit auditul invocărilor. Calificarea concurenței se limitează la combinațiile verificate; modelul jocului și acceptanța Android au contracte proprii.
