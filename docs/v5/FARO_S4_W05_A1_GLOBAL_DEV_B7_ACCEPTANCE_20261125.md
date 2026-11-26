# FARO V5 · W05-A1 — A1C4 B7 y aceptación integral DEV aislada

Fecha de cierre: 2026-11-25 (Argentina). Rama `work/s3-night-20261102`. Unidad `S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE`. Alcance **exclusivamente DEV aislado**. No es autorización de producción, datos físicos ni pagos.

## B7 — PASS integral DEV basado en CI y artefactos originales

**Código final** `358895d33d716326bf3e3e8e3421294c3e6697ba`. El commit contiene exactamente DOS rutas del permiso independiente exact3 (test JVM A1C4 y workflow A1C4); la tercera ruta autorizada (test Android A1C4) se conserva desde su commit precedente. No hay modificación a hosts productivos, kernel, Reality, Activity, main ni V4.4. El preflight del workflow valida el permiso READY de su padre; guardia del código [45743934125](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45743934125) **COMPLETED SUCCESS** sobre `358895d33d716326bf3e3e8e3421294c3e6697ba`.

**CI integral A1C4:** [45743934153](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45743934153), job `139655324727` **COMPLETED SUCCESS** sobre `358895d33d716326bf3e3e8e3421294c3e6697ba`. Sus pasos de JVM, compilación de APK/AndroidTest, instrumentación Android API35 con fuente 200%, control XML y subida de artefacto terminaron SUCCESS. No confundir con la CI roja predecesora 45742106711, archivada como RED_FIX de la misma unidad.

**Original ZIP** [artefacto 15083246165](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45743934153/artifacts/15083246165), vigente. Verificado independientemente tras descarga del ZIP real:
- SHA-256 del ZIP `365854f13c0487bbe6b67488361b2d49a37f1ffb5fb2666a7924134df5d25f3b`, idéntico al `digest` publicado por GitHub.
- XML JVM `TEST-ar.com.faro.v5.app.FaroV5W05A1DevAcceptanceTest.xml`: **1/1**, `preservesAppliedPurchaseAndReceiptsThroughRecoveryCutoverAndUndo()`, 0 failures/errors/skips; SHA-256 XML `3633e4fcdda34456f28fa87bfc24e47edd0a43a8e07cf15371f862d8958d287b`.
- XML Android `TEST-test(AVD) - 15-_app-.xml`: **1/1**, `continuesNativeStockHistoryAcrossAuthenticatedCutoverAtFont200`, 0 failures/errors/skips; SHA-256 XML `33ff801d25d7734b22fdd34d165f9f721fac0a9eb2dcf5f9de93da49ca0ea6b3`.
- **Total exacto: 2/2 casos únicos, 0 fallos, 0 errores, 0 omitidos**. APK no vacío `app-v2CoreDev.apk` de 66.292.943 bytes, SHA-256 `4308fc1cafa6b61d62d5264234bf49fa6fe4919c79500b40444700ef4a72001b`; empaquetado **DEV**, no instalación G15.

### Continuidad certificada por los dos casos A1C4

1. Compra manual N-036 realmente creada por Review→Approve→Apply COMMITTED de host local, cantidad 10 UNIT, origen `MANUAL_EXPLICIT`, receipt de Workspace y fila Reality. No proviene de precio, carrito, factura externa o presupuesto.
2. N-035: denegación sin principal/grant; antes de aprobación no Apply, interrupción de observación tras COMMITTED → `RecoveryRequired`; reapertura y `reconcileCommittedOnly` con MISMO recibo, sin doble recepción. Recepción 7 y después 3; pendiente 3→0; recibos físicos individuales y `ExecutionReceipt`/attempt COMMITTED.
3. Cierre de sesiones SQL12, mantenimiento `FaroV5ClosedHousehold12DevMaintenance`: `prepare` deja origen activo y journal VALIDATED; `start` sin permiso devuelve denegación sin switch; `start` aprobado recupera datos hacia SQL13, journal COMPLETED y puntero único con secuencia +1. Se conservan Reviews y recibos COMMITTED.
4. N-016 Undo: deniega origen de revisión antigua, acepta el Review COMMITTED restaurado con confirmación independiente, compensa una vez las 7 recibidas y conserva 3 netas; deniega doble Undo, mantiene dos Reviews de recepción y uno de Undo.
5. Android API35 DEV: paquete DEBUGGABLE permitido y V2 productiva OFF; fixture descartable solo tras verificar emulador; fuente 200%, acciones por árbol de accesibilidad, autorización invalidada tras abandonar foreground, UI SQL12 muestra 0→7→10 y SQL13 solo ofrece historial/Undo sin escribir SQL12 en SQL13 ni ejecutar cutover desde el panel. Mantenimiento entre modos invocado fuera de la UI.

### Ledger de B7 anteriores reutilizados (sin volver a ejecutar)

- A1B3C2C1 Android Evidence2 ACTIVE y A1C0A admisión stock: B7 acotados históricos en `docs/v5/FARO_S4_W05_*`; no inflar los 2 casos de A1C4 con otros contadores.
- A1C0B0, A1C0B1, A1C0B2A/B, A1C0B3A/B, A1C0B4: sus `FARO_S4_W05_*_B7_PASS_*_20261125.md` certifican compra, codec y cutover SQL12, no producción.
- A1C1 y A1C2A/B/C/D/E/F/G0/G1A/G1B: `FARO_S4_W05_*_B7_PASS_*_20261125.md` certifican recepción, compensación SQL13, backup v10, recuperación y Undo, sin repetir suites.
- A1C3: `FARO_S4_W05_A1C3_B7_PASS_ANDROID_STOCK_REVIEW_DEV_20261125.md`, CI 45731828704 y guard 45731828629, **8/8** (3 JVM +5 Android). 
- A1C4 verificador SQL13: `FARO_S4_W05_A1C4_SQL13_REVIEW_VERIFIER_B7_PASS_20261125.md`, CI 45740195438 **1/1**.
- A1C4 mantenimiento SQL12 cerrado: `FARO_S4_W05_A1C4_CLOSED_SQL12_MAINTENANCE_B7_PASS_20261125.md`, CI 45741277089 **2/2**. 
- A1C4 RED_FIX de selector JVM: `FARO_S4_W05_A1C4_GLOBAL_DEV_RED_FIX_45742106711_20261125.md` documenta CI roja corregida por el commit final `358895d33d716326bf3e3e8e3421294c3e6697ba`. El XML de la CI final valida las suites **A1C4 reales**, no Panel C3.

### Alcance, límites y decisión de cierre

**PASS: W05-A1C4 B7 y W05-A1 aceptación global DEV aislada.** No se deduce comportamiento no cubierto: el caso A1C4 no ejecuta todos los crashes/ABA/formatos legacy por separado; su cobertura negativa se apoya también en B7 acotados del ledger. La aceptación es de integración local y emulador API35, no una prueba física del Moto G15. TalkBack manual físico y G15 `NOT_RUN`. No hay dinero real, pago, compra externa, stock de producción ni acceso a banca. **V2 productiva/stock real/pagos/Auto/Noche OFF**. Catálogo180 sin nuevos comandos. Main y V4.4 intactos.

**Permiso de esta unidad consumido/BLOCKED** al publicar este B7; `last_code_review` PASS vinculado al SHA final. Writer debe quedar RELEASED al cerrar, guardia documental del checkpoint debe confirmarse en GitHub antes de declarar turno completamente cerrado. No activar la siguiente fase de W05 por herencia: requiere su B1–B5/permiso propio.

## Checkpoint

Código certificado `358895d33d716326bf3e3e8e3421294c3e6697ba`, CI 45743934153, guard del código 45743934125, artefacto 15083246165, 2/2 XML originales. Este documento y el permiso/puntero se publican juntos en commit documental. Guardia final documental a verificar sobre el HEAD de ese commit, writer CAS liberar época 2055.
