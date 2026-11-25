# FARO V5 · WORK TURBO · terminar W05-A1C sin duplicar trabajo
Checkpoint de entrada a verificar remotamente al comenzar. Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. **El HEAD final y el writer exacto se toman del puntero vivo; no se usa este documento como autorización por sí solo.**

## Recorrido operativo
1. Leer `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`, preauditoría `docs/v5/FARO_S4_W05_A1C0A_STOCK_RECEIVE_DEV_ADMISSION_B1_B5_20261125.md` y cadena `docs/v5/FARO_S4_W05_A1C0B_A1C4_WORK_CHAIN_20261125.md`.
2. C2C0 journaled cutover B7 PASS scoped XML26/26 CI45528363926; C2C1 Android Evidence2 B7 PASS scoped XML9/9 CI45531217759; **NO repetir**. Revalidar guard del HEAD, writer global CAS, permiso READY **A1C0A** exact3. Si READY válido y guard SUCCESS, comenzar directamente B6; no repetir B1–B5. Si no, STOP hasta reconciliar.
3. A1C0A: contrato tipado `stock.receive` N-035 DEV, fuente de compra CONFIRMED confiable como puerto sin implementación privilegiada; 3 rutas nuevas, JUnit XML 8/8, guard, B7, permiso consumido, writer liberado.
4. A1C0B: cerrar origen durable de compra confirmada manualmente antes de permitir recepción, **nunca carrito/presupuesto**. Investigar plan mínimo seguro de persistencia/versionado + FBS2v8; B1–B5 y READY propios, B6/CI/B7.
5. A1C1: recepción Review/Apply + receipt real en DEV; A1C2 recovery/Undo; A1C3 Android DEV; A1C4 aceptación global. Cada unidad requiere B1–B7, rutas exactas, preflight del padre, guard y XML reales; encadenar sin rondas inútiles de preparación. Tests obligatorios compra10/recibe7/pendiente3, replay idempotente, receive3 y Undo, crash/recovery, ACL, ABA, WAL, restore.
6. Si se agotara sesión: dejar la unidad actual cerrada B7 si las pruebas alcanzan, continuar solo hasta checkpoint seguro, permiso consumido y writer RELEASED; no proclamar cierre A1 con etapas OPEN.

## Prohibiciones y límites
N-035 ya catalogado BINDABLE no equivale a binding AVAIL; no permitir `stock.receive` de producción. V2 productiva, stock real, pagos, Auto/Noche OFF; no main ni V4.4, no APIs pagas, ni ejecución física G15 sin permiso. Writer único CAS y protocol guard prevalecen. Ninguna aprobación del grant de necesidades autoriza recepción. Emulador/Android CI no es prueba física.

## Entrega final de Work
HEAD, estado de cada unidad, B7 con SHA/CI/XML/guard, bloqueo remanente y razón concreta, estado del permiso, writer RELEASED/época.