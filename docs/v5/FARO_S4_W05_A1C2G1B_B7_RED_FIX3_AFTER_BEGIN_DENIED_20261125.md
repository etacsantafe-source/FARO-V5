# FARO V5 · W05-A1C2G1B · B7 RED_FIX3 (2026-11-25)

## B7 — Diagnóstico determinista de la tercera CI

Código `879b9fb2d3e11775165a4d94059354712f348086`, guardia `45664677931` **SUCCESS**, CI dirigida `45664677892` **FAILURE**, JUnit original artefacto `15046753816`.
17 casos: regresión N-035 **8/8 PASS**; Undo G1B **4/9 PASS** y **5 FAIL**, cero errors/skips.

La suite diagnóstica identificó la misma secuencia en los 5 casos fallidos:
`BEFORE_INITIAL_READY > INITIAL_READY > PREFLIGHT_ENTER > PREFLIGHT_ACCEPTED > AFTER_BEGIN_DENIED > RESULT_RecoveryRequired`, `Review.state=RECOVERING_UNKNOWN`, `transactionId` presente, recibo físico original aún visible. Es decir, **no se entró a UndoBinding.execute ni se ejecutó SQL13**. La hipótesis previa de lecturas anidadas SQL no está demostrada como causa actual. No se requiere migrar/cambiar G0.

## B6 — Corrección acotada, preservando autorización

Determinar qué predicado exacto de `afterBeginApply` dio false, sin cambiar el protocolo de consentimientos:
- `fresh?.review == applying`;
- envelope idéntica;
- estado APPLYING;
- transacción exacta;
- contexto exacto;
- `ready(intent, pinnedRevision) == initial` (revalidación de fuente COMMITTED, G1A y posición SQL13);
- Workspace selected/current foreground y N-016 policy vivos.

En la misma ruta host DEV y tests, emitir solo **máscara booleana diagnóstica** sin tokens, receipt IDs, payload ni identificadores personales; no alterar `ApplyTransactionExecutor`. La próxima CI debe localizar el predicado falso; si no hay causalidad demostrable, no dar B7 PASS.

**Tres rutas exactas** de permiso G1B sin ampliar (host/test/workflow). Retirar instrumentación temporal antes de cierre final, conservar una verificación equivalente con pruebas. Mantener G0/G1A certificados, CAS, recibos, UNKNOWN sin replay, V2, stock real, pagos, Auto/Noche OFF; G15 NOT_RUN.
