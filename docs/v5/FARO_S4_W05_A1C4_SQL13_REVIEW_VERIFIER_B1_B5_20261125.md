# FARO V5 · SQL13 Evidence2 · Verificación de Reviews durante cutover DEV

Unidad `S4_W05_A1C4_SQL13_REVIEW_VERIFIER_DEV`. Base `6c30782f333712f8c9ed8db4f07496be526267a4`. A1C4 queda suspendido mientras se certifica esta corrección independiente.

## Contraejemplo de integración

1. N-036 y N-035 persisten Reviews COMMITTED con sobre canónico y referencia al ExecutionReceipt. N-016 requiere el Review original, el intento COMMITTED, el ExecutionReceipt canónico y el recibo físico correspondiente.
2. Un FBS2v10 con esos Reviews, autenticado y restaurado a un stage SQL13, debe pasar por `authorize`. En [FaroV5Household13Evidence2DevJournaledCutover.kt, línea 156](https://github.com/etacsantafe-source/FARO-V5/blob/6c30782f333712f8c9ed8db4f07496be526267a4/v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13Evidence2DevJournaledCutover.kt#L150), el cutover crea el replay con `planVerifier = null` por omisión. La verificación compara el stage mediante una captura de los cinco stores, que abre Workspace sin verificador.
3. [FaroV5GenerationStoreGraph.kt, línea 1262](https://github.com/etacsantafe-source/FARO-V5/blob/6c30782f333712f8c9ed8db4f07496be526267a4/v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt#L1262) usa el lector de Workspace que rechaza todos los sobres en esa configuración. La [lectura del snapshot, línea 1294](https://github.com/etacsantafe-source/FARO-V5/blob/6c30782f333712f8c9ed8db4f07496be526267a4/v5/workspace/src/main/kotlin/ar/com/faro/v5/workspace/ConversationWorkspaceStore.kt#L1283) exige verificar cada Review, incluso COMMITTED. La excepción ocurre en la primera verificación de `authorize`, antes de publicar el ticket y avanzar el journal. `guarded` la traduce a `BLOCKED`. La activación del destino (línea 221) y la reapertura pública (línea 318) también necesitan el mismo verificador.
4. En C2F el fixture arma un ledger físico al preparar los archives y deja Workspace sin Reviews COMMITTED. C3 sí partió de Reviews reales y preparó la selección SQL13 mediante una copia de los stores y un fixture de mantenimiento. Sus dos PASS cubren sus respectivos recorridos. La cadena anterior explica por qué queda pendiente el cruce de ambos recorridos por el entry point autenticado.

La evidencia del contraejemplo es una traza de código en el checkpoint. La prueba integral JVM/Android A1C4 y sus XML y APK quedan pendientes. El permiso A1C4 continúa READY exact3 en remoto. El turno fue de solo lectura sobre GitHub.


## B1 — Evidencia y criterio de la corrección

El bloqueo se origina antes de emitir el ticket por la ausencia de verificador canónico en la apertura del destino restaurado. El mismo parámetro falta en activación y reopen. La prueba dirigida debe demostrar el rechazo sin verificador y el éxito con verificador, incluido recovery tras SWITCH_PREPARED, puntero único y journal COMPLETED.

## B2 — Reutilización

El módulo de generación ya depende de `PreparedPlanEnvelopeVerificationPort`. Inyectar el puerto existente en el cutover y pasarlo al replay, al grafo activo y al lector read-only. La dependencia de `CanonicalPreparedPlanEnvelopeVerifier` queda en tests del módulo, donde ya está disponible. Mantener la política de Workspace, sus receipts y la versión del ticket.

## B3 — Alternativas

Reutilizar el puerto con valor opcional y rechazo predeterminado de sobres, porque los fixtures certificados sin Reviews siguen siendo casos válidos. Eliminar la validación o confiar en el ciphertext por sí solo debilitaría la verificación de los sobres después de replay. Preferir un nuevo workflow dirigido para conservar los registros de la CI histórica y hacer explícito el permit exact3.

## B4 — Rutas exactas

- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13Evidence2DevJournaledCutover.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13Evidence2DevJournaledCutoverTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1c4-sql13-review-verifier.yml`

## B5 — Pruebas y límites

Un stage autenticado FBS2v10 con Review canónico y su receipt debe bloquearse antes del ticket si falta el verificador. Con verificador, autorizar el cutover, interrumpirlo al preparar el cambio, recuperarlo con el mismo verificador y confirmar un único pointer, journal COMPLETED y reopen. Verificar rechazo de plan errado. CI ejecuta solo los casos nuevos y valida sus XML originales. El fixture de generación verifica el transporte y admite una prueba sintética de Reviews. La composición de Apply real y UI corresponde a A1C4 y depende de un entry point de mantenimiento accesible desde los tests de app, ya que replay y cutover son internos al módulo de generación. La exposición de ese entry point y el enlace del backup integral requieren su propia revisión de alcance. No se incorpora ningún acceso desde UI o producción.

Guardia documental antes de emitir READY. V2 productiva, stock real, pagos y Auto/Noche OFF. G15 físico NOT_RUN.
