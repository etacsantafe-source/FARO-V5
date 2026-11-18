# FARO W01 D1B1C1 — B7 del cutover Finance9 en DEV controlado

Fecha local 2026-11-17. Unidad `S4_W01_D1B1C1_FINANCE9_DEV_JOURNALED_CUTOVER`.

## B6
- Kotlin y CI dependiente en `9561ee8659b39ed333c98c10ee8da814fc4b5fbc`, cinco rutas verificadas. El owner exige lease DEV exclusivo, mantiene un gate material, liga journal y puntero completo y deja la promoción/rollback al coordinador existente.
- [CI 44224896409](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44224896409), 6/6, y [guard 44224896391](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44224896391), SUCCESS. Artefacto 14392263688, SHA-256 XML `0a83d17bd0d79481d56b39cc142ff41e0fdab943c651eb138c28a26c57906b0e`. Verificados el 8 fuente byte a byte intacto en el caso positivo, el valor de revisión existente, activación 9, replay tras CAS interrumpido, rollback, ABA, autorización y downgrade.
- Dos jobs colaterales fallaron antes de probar por su preflight histórico fijo. Se autorizó RED_FIX del mismo unit y se corrigieron solo dos YAML en `1eb6a5d078a0c4f84489a1d6b3a31c7b3d2f77f5`. Los blobs Kotlin/test de D1B1C1 son idénticos.

## B7
**PASS SCOPED**. Sobre el SHA de RED_FIX se comprobaron [guard 44226243326](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44226243326), [revocación 44226243341](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44226243341) y [aviso post-COMMIT 44226243338](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44226243338). Los tres terminaron SUCCESS. Se descargaron los artefactos 14392733827 y 14393207451, se cotejó cada ZIP contra el digest publicado y se verificaron 40/40 casos de regresión en cinco XML. Compilación y manifest de W01 DEV y V2 Core DEV pasaron en el workflow de aviso.
- El cutover queda probado para SQLite real dentro de una raíz JVM DEV con stage validado y preexistente. Cada grafo de health se cierra antes de abrir otro. El owner devuelve solo el estado de mantenimiento; los puertos STAGED siguen rechazados en el modo exacto9 activo.
- Pendientes: preparación con datos del usuario existente, integración de lease/arranque/recuperación en W01 Android y UI sobre la misma sesión. El permiso se consume. W1 OPEN, W2 STOP, V2 productiva OFF. Sin prueba física.
