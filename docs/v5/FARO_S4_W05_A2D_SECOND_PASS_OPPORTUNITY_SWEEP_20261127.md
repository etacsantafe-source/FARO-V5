# FARO V5 — A2D · segunda auditoría del pretrabajo

**Baseline:** `29216d6d403e9d5e590551a92f0b68073350118e`. Solo documentación; ningún resultado de los tests diseñados se declara ejecutado.

## Lo que ya está aprovechado y NO se rehace
- A2A físico SQL13 5/5, A2B reserva SQL14 9/9 unidades y A2C SQL15 global 91/91 + APK. FBS2v12/journaling, Undo y root pointer no se tocan.
- `FaroV5GenerationStoreGraph.inspectActiveHousehold15DevReadOnly` ya sabe leer SQL15 ACTIVE WAL y 5 stores pero es un inspector de seguridad interno, no un read-model UX. El render no debe acceder directamente a `RealityStore`.
- SQL15 `RealityHousehold15ConsumptionRowsPort.validate` valida eventos y prohíbe consumir reserva sin contrato nuevo. Cálculo A2D debe respetarlo y no abrir Apply en la UI.
- `FaroV5ShellNavigation` ya tiene SmartCompra: eliminar creación innecesaria de root; `FaroV5FinanceReadSurface` ya distingue coverage/denied/stale; `FaroV5BootstrapActivity` y `FaroV5SurfaceHost` ya proveen mecanismo de montaje.
- `v5/app/build.gradle.kts` ya tiene flags Android de instrumentación aislados (por ejemplo W04); evitar sobrescribir sourceSets previos.

## Riesgos encontrados que se adelantan al plan
1. **Fuga de identidad:** no proyectar compras de otro principal; `purchaseRef`, SKU y unidad solo se agregan dentro de principal confiable. Test negativo antes de UI.
2. **Cero falso:** ausencia/revocación/restore UNKNOWN debe bloquear todos los números. `EMPTY_CONFIRMED` es distinto de `UNAVAILABLE`.
3. **WAL ACTIVE:** no reutilizar `immutable=1` del STAGED; leer final desde snapshot coherente y revalidar FULL pointer/ACL.
4. **Available vs reservado:** `available=physical-reserved`; evento CONSUME de reserva está prohibido hoy hasta release explícito, incluso con físico suficiente.
5. **UI stale:** Compose no retiene `READY` al reanudar de background o cambiar generación; invalidación desde host, no solo un `remember`.
6. **Cambio de rutas:** A2D1B toca los puntos necesarios para montaje DEV; si faltara una clase/config concreta, delta B5 y permiso nuevo. Nunca añadir rutas silenciosamente.
7. **Android API35 vs físico:** emulador y XML sí pueden verificarse con Work; G15 queda NOT_RUN.
8. **A2E:** preparar matriz de aceptación como prerequisito, no B6 anticipado. A2E debe comprobar inventario global 10/7/3 y A2D read-model luego de A2D B7, no saltar por suma de B7 anteriores.

9. **CompositionRoot real descubierto en barrido posterior:** `FaroV5CompositionRoot.java` monta/autoriza los puertos activos de la app y faltaba en el scope A2D1B. Añadido como décima ruta candidata; evita falso contrato con callbacks inventados desde la Activity. B5 exige test DEV gate antes de mostrar stock.

## Estado y acción de salida
Preauditorías A2D0A, A2D0B, A2D1A, A2D1B, A2D2 todas documentadas. B6/B7 permanecen NOT_RUN. Solo emitir READY independiente para A2D0A después del guard documental; unidades siguientes exigen B7+READY propio. No gastar crédito Work en rehacer pruebas certificadas ni arreglar scopes ajenos.
