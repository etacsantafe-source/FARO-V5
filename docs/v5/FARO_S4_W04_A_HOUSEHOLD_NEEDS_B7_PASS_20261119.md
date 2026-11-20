# FARO V5 · W04-A · B7 del modelo Household

Fecha local 2026-11-19. Unidad `S4_W04_A_HOUSEHOLD_NEEDS_MODEL_DEV`. SHA B6 `2f345d5e46a5c7d15cfa6e7b4a530f741dca0fbf`, padre `008c6f1cdb7ad1eade6f461355c34c7fd0a8b8c5`.

## B6 — Implementación y pruebas

- Permiso W04-A READY exacto a cinco rutas y preflight ejecutado contra el padre. El diff contiene únicamente settings Gradle, build Household, modelo, pruebas y workflow dirigido. Catálogo, SQLite, Android productivo, stock y Finance quedan fuera del alcance verificado.
- El modelo JVM admite identidad opaca, nombre humano, milésimas positivas con unidad explícita, prioridad, estado, revisión no negativa y fecha civil opcional. Todos sus componentes son inmutables. La unicidad de ID entre filas queda para persistencia.
- CI W04-A `44662692803`, job `136044466961`, **SUCCESS**. Preflight, Gradle y verificador XML verdes. Descargado artefacto `14597025051`, SHA-256 ZIP `1a66a1254f5a8e41357b6826948facd9f75683ef4f404fcb3586240ec1a7fbb6`, cotejado con el log. XML inspeccionado directamente: **10/10**, 0 failures, errors y skipped, diez nombres únicos. Límites de ID y nombre, controles, milésimas y desborde, unidades, revisión, años civiles e inmutabilidad por copia.
- Guardia `44662692779` **SUCCESS** en el SHA B6. Regresión dirigida planificación `44662692662` **SUCCESS**. El workflow G4 `44662692630` compiló y ejecutó tests, pero su verificador histórico exige 4 tests de Matter y recibe 6 sin fallos. El mismo error exacto ya aparece en `44546592005`, anterior a W04. El diff W04-A no toca G4, los módulos Matter/Objective ni sus pruebas. Se documenta el rojo preexistente, sin reabrir W3.
- Android API35 y Moto G15: NOT_RUN en esta unidad JVM.

## B7 — PASS_SCOPED_W04_A_MODEL_JVM

Cerrado el modelo puro Household. Consumir su permiso individual. W04-B0A tiene preauditoría separada en `docs/v5/FARO_S4_W04_B0A_HOUSEHOLD10_STAGED_SCHEMA_B1_B5_20261119.md`, condicionada a guardia documental y READY propio. Todavía no hay migración, backup, Apply ni host Household integrado.
