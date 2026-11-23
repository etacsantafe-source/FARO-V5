# FARO V5 — W04 F2C1B1B1B0 — B7 RED_FIX3 · SESSION ABA revocation

Fecha: 2026-11-23. Unidad `S4_W04_F2C1B1B1B0_LEASED_OWNER_REAL_SOURCE_DEV`. Último commit funcional `df9885a43bab8f6897211e068a7f66b229bbdbdc`.
**RED_FIX 3 — NO B7 PASS.**

## B7 — Evidencia real y causa
- C1B1B1B0 preparó B1–B5 y 4 rutas, guard `45191915994` SUCCESS; permiso READY exact4 con guard `45192202241`. No repetir B0/C0 ni tests anteriores.
- Inicial `ce97438755eabcd0fb868d31673b5df68cfefb5b`: CI `45193030044` rojo 4/4, owner source vuelve a leer Household dentro de su propia transacción. RED_FIX1 `f0220502b761d79e4d3de8dbe64a01f441542ace` preservó grants antes/después, cortó recursion dentro del BEGIN DEFERRED. CI `45194704679` falló PRETEST por contador CI; RED_FIX2 workflow `df9885a43bab8f6897211e068a7f66b229bbdbdc` admitió 1–4 rutas preflight exactas.
- Guard del último código `45195400619` SUCCESS. CI dirigida `45195400769` **COMPLETED FAILURE** en `:store:generation:test`: 4 casos nuevos, **3 PASS / 1 FAIL**, artifact XML `14827851280`, SHA256 ZIP `d9748b1f9b13baf5f40d3e6d9b62c90a41b071d61545cee38ee926cab874d09b`.
- t07 REAL ACTIVE H10/E2 mismo lease y selección tipada PASS; t08 decisión humana ausente y lease no filtrado PASS; t09 legacy/missing fail closed PASS. Único fallo t10 `t10HostEpochConsentAndPointerAbaCannotResurrectOwnerGrants` en línea 307: tras observar `currentHostEpoch != admittedEpoch`, primera lectura se rechaza, pero reponer el valor string antiguo permite leer nuevamente; esto es un **ABA observable en el valor de sesión**, no una denegación de la DB.
- Diagnóstico de control de flujo: `readSelected` devuelve null al observar status falso sin cerrar permanentemente los dos owner scopes. Los métodos owner `admitted` no llegan a ser invocados porque la comprobación de epoch hace cortocircuito primero. Por eso no revocan internamente. La fuente ya es real; el fallo remanente es revocación terminal del permiso.

## RED_FIX acotado (1 ruta del READY)
`v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPort.kt`:
- Para la fábrica OWNER_BACKED nueva solamente, cualquier `readSelected` con resultado nulo o excepción revoca permanentemente ambos owner scopes de ese port, antes de cualquier lectura posterior. Los callbacks se verifican antes de entrar al SQL y después de salir, así que no hay retorno autorizado tras observar una interrupción.
- Preservar F2C0 certificado (sin `ownerScopeSqlAdmission`): sus semánticas anteriores de rechazo y retry quedan intactas. Nunca crear otro lease/DB ni habilitar UI, Apply, pagos, redes, stock, G15 o V2 productiva.
- No exigir detectar una transición epoch A→B→A **no observada**: requerir revocación cuando una lectura o Android lifecycle observa el cambio. Android B1B1B1B1 deberá cerrar sesión explícitamente en onPause/onStop, incluso si string epoch retorna al valor previo.
- Revalidar 4/4 JUnit reales t07..t10 con XML sin skips/failures y guard exacto. Si vuelve a fallar, STOP con evidencia.

B7 del alcance DEV solo tras evidencia completa. F2C1B1B1B1 Android positivo y F3 global OPEN.
