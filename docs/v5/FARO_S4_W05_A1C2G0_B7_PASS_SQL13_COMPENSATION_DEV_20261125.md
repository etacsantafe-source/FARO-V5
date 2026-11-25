# FARO V5 · W05-A1C2G0 · B7 PASS SQL13 compensaciones aisladas DEV

Fecha 2026-11-25. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
Unidad `S4_W05_A1C2G0_SQL13_UNDO_DEV_WRITE_ADMISSION`. **B7 PASS SCOPED**: únicamente puerto interno SQL13, no W05-A1 global.
Código final `1f21c1770618c8cb725f7c6d6a9956ddd832d689`, parent RED_FIX READY exact3 `b08c385b475d6e6f8f8222c18ec6953c3b6779ef`.
Anterior `8056a65959647a4ae58b0616d5c1f198ec1c84bc` fue B7 RED_FIX por Kotlin CLASSIFIER_REDECLARATION en CI `45637952977`; la reparación del mismo alcance renombró la clase privada y corrigió nombres de paquete XML.

## B7 — CI, XML y artefacto real

- Guardia obligatoria del código `1f21c1770618c8cb725f7c6d6a9956ddd832d689`: **45639876447 SUCCESS**; preflight con permiso RED_FIX exact3.
- [CI 45639876681](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45639876681): **COMPLETED SUCCESS**, exacto `1f21c1770618c8cb725f7c6d6a9956ddd832d689`. Job `139295251790`: SUCCESS.
- `:store:reality:test` **BUILD SUCCESSFUL (2m 35s)** y `:store:generation:test` **BUILD SUCCESSFUL (51s)**, observados en log del job.
- El paso `Verify three original JUnit XML files` terminó SUCCESS. Validó `RealityHousehold13CompensationApplyPortTest` **6/6**, `RealityHousehold13CompensationRowsPortTest` **6/6**, `FaroV5Household13ActiveDevReadHostTest` **6/6**. **18/18 pruebas**, failures=0, errors=0, skipped=0, con 18 `testcase` en los XML originales.
- Artefacto `w05-a1c2g0-sql13-compensation-xml`, id `15033423364` (ZIP 2433 bytes), cargado y verificado en GitHub Actions.
- Contratos cubiertos: SQLite SQL13, recibo y compra vinculados, revision CAS, duplicados, intento obsoleto, principal incorrecto, reversión atómica al fallar, scope ajeno, revocación, lectura y reapertura. Los readers históricos permanecen por defecto READ_ONLY.

## Límites y próximo corte

G0 es exclusivamente **un puerto interno de compensación SQL13 ACTIVE DEV**. No habilita `undo.execute` N-016 desde Chat, no crea Approval/Review/Confirm/Apply, no mueve stock real y no realiza transacciones productivas. El test físico Moto G15 sigue **NOT_RUN**; se requiere test del nuevo opener end-to-end al integrarlo en G1/A1C3.

El siguiente corte es **W05-A1C2G1** (Review→Confirm→Apply N-016 con receipt original COMMITTED, identidad, Undo idempotente, recuperación). Luego **A1C3 Android** y **A1C4 aceptación global**. Requieren B1–B5 y permiso READY **propio**, sin heredar G0.
V2 productiva, Apply productivo, pagos, stock real y Auto/Noche permanecen OFF. Main/V4.4 intactas. Writer RELEASED en época 2021 tras este checkpoint.
