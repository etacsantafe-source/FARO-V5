# FARO V5 · W05-A1C4 — aceptación integral W05-A1 en DEV aislada · B1–B5

Fecha 2026-11-25. **Unidad propuesta** `S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE`. Rama `work/s3-night-20261102`.
**Baseline revalidada** `ca55d08c8cf87eecafed1d485613b92d1f3f66a2`; último código funcional `18f58b2274f6d358a2911fdc9ae657c243b0c06a`. Predecesor A1C3 B7 PASS, no repetir B1–B7 de C3 ni G0/G1A/G1B. Sustituye **solo para A1C4** la preauditoría condicional `FARO_S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE_B1_B5_PREAUDIT_20261125.md`. Este documento no es un B6/B7 ejecutado ni concede permisos hasta emisión READY separada y guardia SUCCESS.

## B1 — Evidencia actual, contratos e hipótesis falsables

- A1C3 B7: `docs/v5/FARO_S4_W05_A1C3_B7_PASS_ANDROID_STOCK_REVIEW_DEV_20261125.md` (blob `999abd9923070c4366177e7522b5f52ac6acc1fc`), código `18f58b2274f6d358a2911fdc9ae657c243b0c06a`; GitHub Actions CI `45731828704` y guard `45731828629` SUCCESS sobre ese SHA. ZIP original artefacto `15076604940`: 3/3 JVM + 5/5 Android API35, cero fallos/errores/omisiones. Permiso C3 consumido/BLOCKED. Writer RELEASED epoch2041 al recuperar, no extrapolar su estado al publicar.
- A1C0B0 compra durable SQL12; A1C0B1 codec FBS2v9; A1C0B2A/B filas y replay cinco stores; A1C0B3A/B grafo ACTIVE + cutover; A1C0B4 compra manual N-036 Review/Apply; A1C1 recepción N-035; A1C2A–F SQL13 compensación + FBS2v10, ACTIVE y journal; A1C2G0/G1A/G1B Undo N-016; A1C3 UI. **Todos estos B7 previos son evidencia acotada**, no cierre integral: consultar sus respectivos archivos `FARO_S4_W05_*_B7_PASS_*_20261125.md` y sus CI/artefactos; no correr toda la batería histórica por inercia.
- Código actual inspeccionado: `S4W05ManualPurchaseDevApplyHost.kt` (blob `76d9ee0fdecb4a3f254b1669895d033ee62198c8`), `S4W05StockReceiveDevApplyHost.kt`, `S4W05StockUndoDevApplyHost.kt` (blob `cecfbdf01a1f37229b40ce9844691e9e6a483503`), `FaroV5GenerationStoreGraph.kt` (blob `0423850163621690d0a3a1296b5e44a3ff29b7bf`), panel nativo `FaroV5W05StockReceiveDevPanel.kt` (blob `ff86816f2b01992e0697dda778cbbba22b1e69ea`), test Android `FaroV5W05StockReceiveAndroidTest.kt` (blob `160582ec1eeaf1eccb719d382f1ad9bf7b4b3e3e`) y workflow `faro-v5-s4-w05-a1c3-stock-android-dev.yml` (blob `4c9aaa486169e1352550ab742e9e87e3ab47955c`).
- **Brecha de aceptación real**: compra/recepción se abren con `openActiveHousehold12DevForPurchaseHost` / `...ForStockReceiveHost`; Undo con `openActiveHousehold13DevForUndoHost`. El test C3 pasa 10→7+3 en SQL12 y aplica Undo sobre SQL13 **después de un fixture de cutover aislado externo al panel**. NO prueba un UI que efectúe cutover ni una compra nueva sobre SQL13; A1C4 debe demostrar continuidad autenticada y recuperación entre esas generaciones **sin atribuir al panel facultades que no tiene**.
- Hipótesis de aceptación: una compra manual explícita COMMITTED con fuente Reality confiable permite recepción parcial solo con permisos frescos; tras backup/restore y transición SQL12→SQL13 certificada, el recibo original y su fuente Review COMMITTED siguen enlazados, Undo compensa una vez y conserva historial. Si falta una transición ejecutable segura con las APIs certificadas, A1C4 queda `BLOCKED_DELTA_REQUIRED` con contraejemplo, NO PASS ficticio.
- Estado no incluido: Moto G15 real `NOT_RUN`, TalkBack físico `NOT_RUN`, despliegue y stock productivo `OFF`. No confundir emulador API35 / tests Android con aceptación física.

## B2 — V4.4 y reutilización verificada V5

- Matriz V4.4 `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` L11–L17: SmartCompra separaba precio/carrito de stock; AtomicApply y Undo durables son principios heredables con deudas históricas. Matriz P03 `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` R11/R12/R17/R20: ADAPTAR el flujo, no trasplantar almacenamiento ni crear sexto owner. No repetir tests del código viejo ni llamar compra a presupuesto.
- Reusar los hosts N-036, N-035, N-016, grafo SQL12/SQL13, generación Evidence2 y sus journals/receipts, formatos FBS2v9/v10, interfaces internas para DEV y las pruebas C3 ya PASS. La aceptación A1C4 es una **capa nueva de verificación transversal**, no nueva autoridad de ejecución.
- El circuito de prueba auténtico exige Review→Approve→Confirm explícito, principal/epoch, grant tipado fresco, puntero completo/ABA, lease y cierre antes de cutover, comprobante de compra manual confirmado, receipt de stock físico distinto del `ExecutionReceipt`, reconciliación UNKNOWN; jamás inferir compra de W04, Evidence, presupuesto o pago.
- **Delta mínimo**: test JVM de enlace intergeneración y rollback/backup, test instrumentado de transición y lectura segura en Android, workflow de CI + auditoría documental final. Ningún cambio de `v5/app/src/main`, `store/`, `maintenance/`, Activity/Panel C3, manifest, gradle, catálogos o producción por este permiso. Si la cobertura revela falla funcional real, registrar evidencia y obtener permiso de corrección propio antes de tocarla.

## B3 — Investigación técnica, alternativas, decisión

- **Opción A ADOPTADA**: aceptación de borde a borde mediante tests que componen APIs y fixtures autenticados existentes, mantienen invariantes de generación/lease y validan XML originales. Sin nuevo runtime, no cobrar ni conectar servicios externos. Limitar casos nuevos a invariantes cruzados ausentes; reutilizar B7 anteriores como ledger de evidencias por SHA.
- **Opción B DESCARTADA**: hacer `A1 DONE` por sumar reportes verdes C0/C1/C2/C3 o ejecutar un flujo enteramente simulado que inserte recibos sin Review/Apply COMMITTED. No prueba continuidad, writer, rollback, identidad ni recovery.
- **Opción C DESCARTADA**: extender el panel SQL13 para comprar/recibir, autoconvertir versiones, reescribir stores o habilitar V2 productiva solo para facilitar tests. Sería otra unidad con B1–B5 propios.
- Investigación externa (consulta 2026-11-25): SQLite `https://www.sqlite.org/wal.html` y `https://www.sqlite.org/lang_transaction.html` describen snapshots WAL, un solo escritor/BEGIN, rollback y límites de commit; `https://www.sqlite.org/pragma.html` distingue `synchronous=FULL`. **ADAPTAR** disciplina de commit/rollback a los contratos locales; no sustituir el coordinador existente. Android oficial `https://developer.android.com/training/testing/instrumented-tests`, `https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner` y `https://developer.android.com/studio/test/command-line` fundamentan AndroidJUnit4/runner, emulador y XML originales. **USAR** runner y tareas de Gradle ya montadas, no añadir bibliotecas. Diferencia V4.4: conservar experiencia/atomicidad, no importar arquitectura de prueba legacy.
- Sin candidato externo material que permita autenticar un cutover de FARO: su journal y pointer son propios. Una falla interna solo admite RED_FIX acotado con B5 actualizado; un green aislado de SQLite no es aceptación FARO.

## B4 — Unificación y rutas B6 exactas (solo pruebas)

**Unit ID:** `S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE`. Tres y solo tres rutas funcionales **NUEVAS** (no existen al HEAD de baseline):

1. `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05A1DevAcceptanceTest.kt` — JVM de integración de hosts y generación, puente SQL12/13, backup v9/v10 + persistencia/restart, negative paths y receipts. Sin mocks que conviertan datos no autorizados en COMMITTED.
2. `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5W05A1DevAcceptanceAndroidTest.kt` — instrumentación API35 en paquete DEV aislado; pasos explícitos sobre fixture de mantenimiento reconocido, cambio de generaciones **fuera** de la UI; comprobar estado visible, recuperación, revocación foreground y no operaciones productivas, fuente 200 %.
3. `.github/workflows/faro-v5-s4-w05-a1c4-global-dev-acceptance.yml` — preflight sobre padre READY exact3; CI dirigida de las 2 clases, APK DEV, emulador API35, JUnit XML originales únicos sin fallos/errores/skips + artefactos; verificar guard sobre SHA exacto. El runner y opciones Gradle deben seguir el workflow A1C3 certificado.

**Documentos no funcionales**: este B1–B5, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, `docs/v5/FARO_CONTINUITY_POINTER.json`, y tras B7 el candidato `docs/v5/FARO_S4_W05_A1_GLOBAL_DEV_B7_ACCEPTANCE_20261125.md` con ledger de SHA/CI/guard/ZIP/XML. No incluir ese B7 en B6 funcional, no inventar éxito antes de pruebas. Trailer exigido para cualquier commit funcional: `FARO-UNIT:S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE`.

**Puerta previa**: B1–B5 y su guard documental deben pasar; emitir READY independiente por exactamente estas tres rutas y `last_code_review` A1C3; el gate del padre debe responder PASS en las 3. Si un test de aceptación exige cuarta ruta material, STOP, evidencia, nueva auditoría y permiso; nunca parche silencioso.

## B5 — Auditoría adversarial anticipada (criterios binarios; B6/B7 NO ejecutados)

- **T01** ledger: B7 por cada dependencia C0B0→C3 y G0/G1A/G1B con SHA y CI original; registrar hueco explícito. No equiparar SUCCESS de workflow con XML correcto.
- **T02** compra N-036: manual, positiva, misma unidad, Review/Approve/Confirm/COMMITTED, comprobante canónico + Reality row; carrito, presupuesto, pago o legacy H11R sin compra nueva no son autoridad.
- **T03** recepción N-035: 10, después 7 deja 3, después 3 deja 0; receipts físicos independientes, intent idempotente, revision CAS y saldo de Reality, sin doble escritura.
- **T04** transición: cerrar todos los leases SQL12, cortar/respaldar por journal Evidence2 autenticado, seleccionar SQL13 COMPLETED y reabrir SQL13 con puntero full; solo el fixture de mantenimiento aislado puede generar la transición. UI jamás la inicia.
- **T05** backup/restore: FBS2v9 y FBS2v10 preservan 5 stores, purchase refs, receipt COMMITTED, source Review y ledger, no infieren compras de receipts históricos; backups corruptos/versión equivocada rollback sin mutación del ACTIVE.
- **T06** Undo N-016: origen N-035 Review COMMITTED, `ExecutionReceipt` legítimo y receipt físico exacto; 7→3 netas tras compensación, append-only, recibos 7 y 3 originales presentes; segundo Undo DENIED, no restaurar stock dos veces.
- **T07** restart/crash: PREPARED/COMMITTED/UNKNOWN + reconciliación receipt real sin re-Apply ciego; si no hay cobertura soportada por los hosts existentes, fail-closed y `BLOCKED_DELTA_REQUIRED`.
- **T08** denegación: falta de approval/grant/foreground, principal/UID/hostEpoch ajenos, revision stale, intentKey repetido o nueva generación bajo mismo ID (ABA) → cero mutaciones.
- **T09** SQL12/SQL13: no usar un opener de una versión con ACTIVE de otra; no reabrir apuntador VALIDATED/STAGED; evitar transacción dual o corte con lease vivo.
- **T10** Android: API35 real en CI, package allowlist DEBUGGABLE y V2 productiva OFF; verificar hardware emulator ANTES de resetear fixture disposable, identidad y raíz noBackupFilesDir, fuente 200%, accesibilidad y reanudación.
- **T11** respeto de ámbitos: solo 3 rutas nuevas, ninguna clase de producción, manifest, writer script, catálogo, main, V4.4, pagos, red, stock real o Auto/Noche; no G15 físico.
- **T12** independencia: workflow prueba las dos suites de A1C4 seleccionadas, resultado XML en ZIP con testcases únicos y 0 failure/error/skipped, Android APK DEV + guard SUCCESS en el SHA funcional. Sin suplantar evidencia C3, ni declarar PASS con CI running.
- **T13** dictamen: B7 global únicamente si todos los T obligatorios son verdes y dependencias corroboradas. Si la prueba demuestra hueco de continuidad SQL12→SQL13, emitir `BLOCKED_DELTA_REQUIRED`, ubicar componente y preparar subunidad RED_FIX; no ampliar este permiso.

**Resultado B5 DOCUMENTAL**: hipótesis, límites, tres rutas, negativos y decisión verificables. Los PASS son del trabajo predecesor, **no** de A1C4. Emitir READY tras guard y preflight; B6/B7 pendientes.

## Segunda revisión de pendientes adelantables (sin repetir)

1. A1C3 ya confirmó UI real en modo aislado y receipts 8/8. No rehacer Kotlin, tests de Panel ni Groq. Para A1C4 preparar ledger verificable de dependencias con vínculos a XML/ZIP, no editar sus fuentes.
2. Brecha potencial **mayor**: cutover SQL12→SQL13 no se ejecuta desde UI. La aceptación debe incluir mantenimiento autenticado externo y cierre de leases; si falla, **no** falsificar experiencia integral. Esto es condición T04/T09, no autorización tácita de feature.
3. Distinguir `receive.intentKey` del receipt de Apply en origen Undo; rechazar legacy receipt sin compra confirmada. No inferir datos de finanzas/SmartCompra.
4. El protocolo de writer CAS exige RELEASED al final; un guard documental SUCCESS no es guard de código ni prueba Android. La fase G15 física queda para autorización aparte, nunca bloquear artificialmente un cierre **DEV acotado** por G15 NOT_RUN, pero sí dejarlo visible.
5. No hay otra subunidad funcional B6 adelantable **en paralelo** antes de un B7 A1C4: si aparecen fallos reales, delimitar RED_FIX con permiso nuevo. Si la aceptación supera todos los negativos, cerrar W05-A1 **solo DEV** y continuar backlog W05 sin abrir productivo por herencia.

## Handoff Work

Leer `AGENTS.md`, pointer, permiso, este documento y B7 C3. Verificar HEAD, CAS writer y gate parent exact3. **Comenzar en B6**, no repetir B1–B5. Implementar solo tests/workflow, ejecutar CI con APK DEV y XML originales; contrastar cada test con source/receipt (no fixtures falsos). Registrar B7/guard/consumo del permiso, actualizar pointer y liberar writer. Si prueba falla, clasificar RED_FIX con evidencia antes de ampliar rutas. Main/V4.4/V2 productiva/stock real/pagos/Auto/Noche OFF; G15 físico NOT_RUN.
