# FARO V5 · W05-A1B3C1C · RED_FIX workflow exact route count · 24-11-2026

## B1 — Evidencia CI
Commit de código WAL `41dab6671ae2e998bd80f17b2f16034cbadf6db5` contiene exactamente CUATRO rutas con permiso READY previo `e72666558bca498eb07da3eb0619bf5fe0684c7f`. La guardia de protocolo del código está separada de la CI dirigida. El workflow `45508825833`, job `138850931973`, terminó FAILURE en el paso inicial exact routes: la condición shell `[[ "${#paths[@]}" -eq 6 || "${#paths[@]}" -eq 1 ]]` rechazó 4 rutas, antes de Gradle. NO XML ni veredicto funcional del nuevo código. Es mismatch del runner, no evidencia de corrupción SQLite.

## B2 — Comparación y reuso
El gate `scripts/quality/faro-protocol-gate.py` valida rutas individuales contra el permiso de HEAD padre, que ya enumera 4 rutas exactas. El workflow histórico acepta 6 originales y 1 RED_FIX anterior. No hace falta editar ese gate ni código C1B/Android. Reutilizar el mismo preflight por ruta.

## B3 — Investigación y alternativas
A ADAPTAR: permitir solo `1,4,6` archivos en el contador local, mantener loop de preflight exacto y `FARO-UNIT`. B DESCARTAR: ignorar todos los cambios o aceptar rango libre `<=6`, ocultaría rutas no revisadas. C DESCARTAR: mezclar cambio CI con cambios de host, rompe auditabilidad. B3 externo de SQLite ya certificado en documento original C1C, no se reabre con esta corrección de invocación local.

## B4 — Única ruta RED_FIX
`.github/workflows/faro-v5-s4-w05-a1b3c1c-android-sql11-reopen.yml`. Cambiar SOLO la condición booleana de rutas a `1 || 4 || 6`, conservando loop de preflight parent y la selección JUnit XML original. No tocar main, Activity, manifest, permisos productivos ni código WAL.

## B5 — Adversariales
T01 parent READY exact1 en el nuevo commit de workflow; T02 código anterior exact4 solo cuando permitía cuatro rutas; T03 commit inicial exact6 sigue válido; T04 cualquier otra cardinalidad se rechaza; T05 todas las rutas siguen pasando el preflight individual; T06 correr JVM/Android DEV y XML original real, no inventar PASS.

## B7 — Disposición del código anterior
`41dab6671ae2e998bd80f17b2f16034cbadf6db5` queda **RED_FIX** debido a que CI `45508825833` se bloqueó en la selección de rutas (no llegó a Gradle). No se descartan B1–B5 anteriores ni 16/16 de commit `ce12c6f2bbb5c13faafdd8d4930c4cd19335e801`; estos últimos NO certifican el código WAL reciente. Exigir permiso READY propio exact1, guardia SUCCESS y nueva CI+XML antes de B7.
