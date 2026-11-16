# FARO V5 — S4 W01: CERRAR TODA LA ARQUITECTURA ANTES DE CONTINUAR CÓDIGO
**Fecha:** 2026-11-16 (Argentina). **Tipo:** decisión de orden y gate DOC_ONLY. **Usuario:** «No. Terminamos todo lo de w1 y después hacemos código de w1».
**Alcance:** W01/Finanzas F02–F06 exclusivamente; conservar sin repetir código y B7 PASS parciales. W02 y demás W permanecen fuera de trabajo hasta terminar arquitectura, programación y certificación funcional de W01.

## B1 — Evidencia de estado y brechas confirmadas
- Fuente de continuidad: `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_S4_W01_GATE_HANDOFF_20261115.json` y preauditorías W01. Punto de partida: HEAD `7b9c5dc68c36f57d396b4b3676928ee1cfb1a8ef`, guard `43887416327` SUCCESS, writer RELEASED epoch 971, permiso funcional BLOCKED.
- W01-A preview y W01-B0 propuesta, W01-B1 Review, W01-B2A alcance, B2B grants DEV in-process, B2C0 Apply DEV 7/7 y B2C2 receipt-only 5/5: **código certificado únicamente en cada alcance**. No suponen UI productiva ni autoridad Finance entre procesos.
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: `SCHEMA_VERSION=7`; `PLANNING_SCHEMA_VERSION=8` en opener aislado; backup legacy=5. `initializeSchema()` rechaza >7; `initializePlanningStage8()` migra desde 7; G4 restore exige versión 7; backup legacy exige 5. El cambio requiere estrategia de apertura, copia/restauración y generación, no subir versión a ciegas.
- `MaterialWriteGate.kt` controla solo dentro del proceso; `S4W01B2FinanceObjectGrantOwner.kt` conserva nonce/epoch en memoria; `ApplyTransactionExecutor.kt` explicita carrera check→COMMIT. No existe ACL Finance durable SQL serializada con la revocación ni acreditación de consentimiento humano en host de producción.
- `FinanceOperations.kt` expone `finance.snapshot.read`, `finance.account.create` y `finance.entry.record`, NO nuevas operaciones C/D/E. `FinanceModel.kt` segrega monedas; MonthlyPlan no puede marcar pago por vencimiento. `FARO_S4_W01_CDE_AUTHORIZED_SCHEMA_CATALOG_SCOPE_B1_B5_20261115.md` registra aprobación humana de alcance de contratos/migraciones, NO READY funcional.

## B2 — Reuso y límite de trabajo
Conservar W01 A/B0/B1/B2A/B2B/B2C0/B2C2 B7 PASS, sus SHA y suites. Conservar catálogo de 180 operaciones y sus reglas de generación. No tocar main, V4.4, S2, S3, V2 productiva, Auto, Noche, APK instalada ni datos G15. Arquitectura íntegra de W01 primero; ninguna subunidad funcional nueva B6 de W01 antes de la puerta global; B6 ya certificado no se rehace.
**Límite de alcance:** W01 = F02 cuentas, F03 movimientos, F04 transferencias, F05 compromisos/cuotas, F06 presupuestos. F07 motor general de proyección/gráfico se implementa en W08; W01 debe definir su interfaz de lectura/UX sin presentar W08 como completado ni invadir su código.

## B3 — Alternativas y precedencia de la decisión humana
- A, **elegida ahora**: terminar de principio a fin TODA arquitectura W01; cerrar un B7 de arquitectura global; luego implementación funcional W01 por unidades con permisos B1–B7 propios; cerrar integración W01; recién ahí W02 arquitectura.
- B, descartada: resolver una decisión arquitectónica y programar esa parte mientras el resto de W01 queda sin definir.
- C, descartada: programar Compose o ampliar catálogo/migrar Reality antes del gate global; reabrir W00–S3 o iniciar arquitectura W02 por comodidad.
- Evidencia técnica externa previamente investigada en B3 de W01: SQLite `https://www.sqlite.org/lang_transaction.html`, `https://www.sqlite.org/isolation.html`, `https://www.sqlite.org/lang_altertable.html` y Android `https://developer.android.com/topic/architecture/data-layer/offline-first`. Reusar estudios vigentes por unidad; ampliar B3 sólo si cambia la decisión técnica.

## B4 — Las seis puertas arquitectónicas que deben estar TODAS cerradas
**ARCH-01. Inventario exacto de compatibilidad.** Identificar en rutas de código todos consumidores/aperturas de Reality7/Planning8, `GenerationAuthorityStoreGraph`, `FaroV5ActiveGenerationGraphOwner`, backup legacy5 y G4, backup Planning8, restore, generación/ABA, staged cutover y paquetes aislados. Definir cómo versiones antiguas se abren o rechazan sin pérdida, y criterio de rollback/transacción. Registrar paths y pruebas existentes a reutilizar. El mapeo parcial C1 existente no equivale a inventario total.

**ARCH-02. Autoridad Finance durable.** Definir fuente REAL de principal humano y consentimiento local explícito (Workspace APPROVED no es un grant), claves de autorización por objeto/operación/generación/epoch, persistencia, revoke/regrant, expiración y reinicio. Policy y Apply deben serializarse por el MISMO dominio SQLite `BEGIN IMMEDIATE`→`COMMIT`; la revocación debe tomar ese mismo dominio. Definir orden de carrera, revalidación, recibo y failure mode; prueba adversarial entre dos conexiones/procesos y restore ABA. Nunca habilitar Apply por monitor in-process como solución final.

**ARCH-03. Esquema y migración compatibles.** Resolver evolución de 7/8, legacy5/G4, Planning8 y generaciones sin elegir número nuevo por inercia; documentar cambios DDL/FK/índices/copia/restore y plan de crash/rollback. Los datos antiguos deben migrar con integridad o quedar rechazados explícitamente SIN destrucción. Apertura desconocida no equivale a saldo $0.

**ARCH-04. Contratos F02–F06 completos.** Conservar bindings oficiales F02/F03; cerrar de forma explícita F04 transferencia de misma moneda: 2 efectos en una sola transacción/receipt, no doble Apply, decisión de sobregiro y sobreflujo; F05 obligación, cuota, recurrencia, liquidación REAL distinta de COMPROMETIDO, identidad/dedupe M+7/M+12/M+36 y frontera MonthlyPlan; F06 presupuesto por mes/categoría/moneda, sin escritura de gasto REAL, permisos/fuente parcial. Especificar OperationIds nuevas, payloads canónicos, ownership, fuentes de spec generadas (NO editar TSV generado a mano), migración y negativos antes de READY. Política de sobregiro, cambios de obligación y correcciones deben quedar resueltos, no delegarse a B6.

**ARCH-05. Interfaz e integración W01.** Arquitectar estados y navegación de cuentas/movimientos/transacciones, review/confirmación, receipt, UNKNOWN y recovery; fuentes Reality vs Evidence; apertura explícita, cuenta no autorizada != cero, ARS/USD separados. Contrato UI de Finanzas: gráfico M-6..M+5, línea cero siempre visible, negativos solo bajo cero, resumen cuentas, M0 detallado/M+1..M+5 compacto, tabla TalkBack, 200% de fuente; la lógica de proyecciones F07 se entregará en W08. Decidir manejo de estado parcial y no prometer gráfico real antes de W08.

**ARCH-06. Plan de implementación/aceptación verificable.** Ordenar código exclusivamente W01: (1) fences/migraciones y contratos Finance, (2) F02/F03 host productivo y Compose, (3) F04, (4) F05, (5) F06, (6) W01-F integración Android/CI + B7; ajustar al grafo de dependencias realmente auditado, no alfabético automático. Para cada unidad: owners, paths exactos, B1–B5 vigentes, riesgos negativos, READY por permiso separado, XML/CI por SHA, receipt/rollback/revoke/restart. No repetir 10/10, 12/12, 7/7, 8/8 o 5/5 ya certificados sin delta real. Campaña G15 final queda bajo autorización y cierre de S4/W11.

**Condición binaria de salida:** `W01_ARCHITECTURE_B7_PASS` SOLO si ARCH-01..06 tienen documentos decididos y auditados sin huecos materiales, esquema/versionado, frontera de identidad, UX y contratos/paths concretos; writer RELEASED y guard del SHA final SUCCESS. Hasta entonces: `W01_ARCHITECTURE_OPEN`; permiso funcional BLOCKED; NO código funcional W01 nuevo, NO arquitectura W02. La decisión sobre una incertidumbre material se resuelve durante arquitectura, no se finge cerrada.

## B5 — Riesgos y pruebas adversariales que arquitectura debe especificar
Revocación desde proceso 2 entre verificación y COMMIT; mismo sujeto/otro sujeto; restore y ABA; migración cortada 7/8; backup G4/legacy5; fingerprint cambiado; doble tap y receipt único; transferencia sin segundo efecto; cuota vencida sin pago; ARS/USD sin FX; M+36 retenido; lectura negada visible PARCIAL/NULL; accesibilidad 200%/TalkBack y sin red. Estados `NOT_RUN` hasta suites posteriores; ningún resultado técnico se inventa por la decisión de orden.

## B6 — Cambio documental de secuencia, no código
Publicar esta decisión con puntero/handoff en tres rutas documentales solamente. No READY, no Kotlin, SQL, Android, catálogos, workflows o suites funcionales nuevas.

## B7 — Verificación de este checkpoint
Exigir diff documental exacto, HEAD remoto, protocolo guard SUCCESS en ese HEAD, permiso sigue BLOCKED, writer CAS RELEASED con último checkpoint idéntico. **Este B7 verifica el ORDEN de trabajo, no certifica arquitectura W01 completa.**
