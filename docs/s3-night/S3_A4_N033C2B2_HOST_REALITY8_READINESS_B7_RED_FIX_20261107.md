# FARO V5 — S3.A4 N033C2B2 B7 RED_FIX — aislamiento de pruebas por unidad
Fecha 2026-11-07 AR. Unidad exacta `S3_A4_N033C2B2_HOST_REALITY8_READINESS`. Resultado **RED_FIX** sobre commit funcional `d4cbfb1a1c26c9a44e625029ad1fced24126b969`; no PASS final.

## B7 — Auditoría adversarial independiente y evidencia
- B1–B5 precedente `docs/s3-night/S3_A4_N033C2B2_HOST_REALITY8_READINESS_B1_B5_20261107.md`, blob `6d8e1a87fe97d1df054404ebb4b1398a26e80de7`, emitido antes de código; permiso READY en padre `69a0af9cab0077677b4efa4625a3574e1f760916` y guard de permiso `42388858365` SUCCESS.
- Commit d4cbfb1a: exactamente cinco rutas permitidas, dos métodos Boolean de readiness sin graph exportado ni shipping activado, dos suites con 2 y 3 tests añadidos y workflow dirigido nuevo.
- Guardia de protocolo `42389272718` **SUCCESS** SHA d4cbfb1a; workflow dirigido `42389272813` **SUCCESS** XML exacto 14/14 (Reality8 suite 8/8, host suite 6/6; cero fallos/errores/skips).
- **Bloqueador verificado**: regresión histórica N033C1 workflow `42389272772`, job `128453203885`, **FAILURE**. El log confirma `Gradle BUILD SUCCESSFUL`, tests de la clase `FaroV5ActiveReality8ReopenT2Test` con `(8,0,0,0)`, pero el XML exacto histórico esperaba `(6,0,0,0)`: `N033C1_XML_FAIL FaroV5ActiveReality8ReopenT2Test (8,0,0,0)`. NO es un test semántico rojo, pero falla la aceptación de CI del repositorio. Otra suite original de host pasa de 3 a 6 y podría romper otros contadores exactos.
- Dictamen **RED_FIX SAME UNIT**: N033C2B2 NO cerrada. Antes de cualquier nuevo código funcional registrar una preauditoría B1–B5 y un nuevo permiso READY que reconozca este B7. No modificar código fuera de esta unidad ni alterar el guardio.

## Corrección exigida, sin omitir regresiones
Restaurar byte por byte las dos clases históricas de test a su versión previa d4cbfb1a (`69a0af9cab0077677b4efa4625a3574e1f760916`) conservando 6 y 3 tests históricos. Mover los 2 y 3 nuevos casos a dos clases **independientes**, no borrarlos ni marcar skip. El workflow N033C2B2 verifica cuatro XML: históricos 6+3 y nuevos 2+3 = 14/14. Así N033C1 vuelve a exigir sus 6/6 sin editar su workflow antiguo; el nuevo directed sigue validando los 14 casos. No modificar los dos métodos de producción de readiness ni schema, marker, pointer, Reality7, V2 certification, datos.
- No ampliar permiso a producción ni a N033C3. Nuevos test classes y workflow enumerados en `S3_A4_N033C2B2_HOST_REALITY8_READINESS_REDFIX_B1_B5_20261107.md`.
- STOP ante otro rojo hasta resolver con evidencia exacta, no maquillar XML ni modificar el número requerido de pruebas.
- S2 8/8 CLOSED; S3.A4 OPEN; S3 formal 0/5; V2 productiva, S4, Noche/Auto OFF; G15 física NOT_RUN; S5 cleanup diferido.
