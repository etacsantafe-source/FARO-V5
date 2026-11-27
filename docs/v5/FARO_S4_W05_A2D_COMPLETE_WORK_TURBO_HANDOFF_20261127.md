# FARO V5 — Work Turbo · terminar W05-A2D completo · prework

Baseline HEAD al preparar: `29216d6d403e9d5e590551a92f0b68073350118e`. Scope A2D DEV aislado, NO A2E, NO G15. A2C B7 PASS 91/91 run 46045293622, guard final 46046431091 SUCCESS; NO repetir A2A, A2B, A2C.

## Orden de trabajo exacto, dependencias y documentos

1. **A2D0A:** Proyector tipado de existencias SQL15 (JVM puro). Unidad `S4_W05_A2D0A_SQL15_STOCK_POSITION_PROJECTION_DEV`, previa A2C_GLOBAL_B7_PASS_XML91/APK. docs/v5/FARO_S4_W05_A2D0A_SQL15_STOCK_POSITION_PROJECTION_B1_B5_20261127.md. Candidatas 3 rutas. Estado inicial B1–B5 DOCUMENTED (no B6/CI/B7).
2. **A2D0B:** Host lectura SQL15 ACTIVE con lease/grant y frescura. Unidad `S4_W05_A2D0B_SQL15_ACTIVE_STOCK_READ_HOST_DEV`, previa A2D0A B7 PASS. docs/v5/FARO_S4_W05_A2D0B_SQL15_ACTIVE_STOCK_READ_HOST_B1_B5_20261127.md. Candidatas 3 rutas. Estado inicial B1–B5 DOCUMENTED (no B6/CI/B7).
3. **A2D1A:** Panel Compose accesible y estado de stock, sin montar productividad. Unidad `S4_W05_A2D1A_STOCK_ACCESSIBLE_PRESENTATION_DEV`, previa A2D0B B7 PASS. docs/v5/FARO_S4_W05_A2D1A_STOCK_ACCESSIBLE_PRESENTATION_B1_B5_20261127.md. Candidatas 3 rutas. Estado inicial B1–B5 DOCUMENTED (no B6/CI/B7).
4. **A2D1B:** Enlace real DEV de SmartCompra y pruebas Android API35. Unidad `S4_W05_A2D1B_SMARTCOMPRA_ANDROID_READ_BINDING_DEV`, previa A2D1A B7 PASS. docs/v5/FARO_S4_W05_A2D1B_SMARTCOMPRA_ANDROID_READ_BINDING_B1_B5_20261127.md. Candidatas 9 rutas. Estado inicial B1–B5 DOCUMENTED (no B6/CI/B7).
5. **A2D2:** Aceptación integral A2D DEV y enlace a A2E. Unidad `S4_W05_A2D2_GLOBAL_STOCK_READ_ACCEPTANCE_DEV`, previa A2D1B B7 PASS. docs/v5/FARO_S4_W05_A2D2_GLOBAL_STOCK_READ_ACCEPTANCE_B1_B5_20261127.md. Candidatas 2 rutas. Estado inicial B1–B5 DOCUMENTED (no B6/CI/B7).

**IMPORTANTE:** solo D0A puede recibir permiso READY en esta preparación tras guardia del documento; D0B/D1A/D1B/D2 deben obtener **nuevos** permisos READY tras B7 precedente y guardia propia. No heredar permisos de otra unidad ni hacer batch de código y permiso. Mover HEAD entre unidades con recuperación CAS, actualizar referencias de origen y verificar B1–B5 vigentes de misma unidad.

## Reglas de ejecución sin desperdiciar créditos

- Arrancar directamente en **B6 de A2D0A** solo si permiso exact3 READY + guardia SUCCESS + preflight + writer CAS vigente. En otro caso completar gate documental/permiso primero. No reescribir B1–B5 ya válidos de la unidad.
- Para cada unidad: commit B6 `FARO-UNIT`, liberar writer durante CI, CI por HEAD exacto con XML original y artifact; B7 independiente; consumir permiso BLOCKED, checkpoint, guardia documental, writer RELEASED. Repetir cadena en orden hasta A2D2 global.
- Negativas esenciales: no existe habilitación productiva de stock, incluso con cálculo `available`; SQL15 restringe `reservationRef != null` para CONSUME (debe liberarse reserva con operación separada); no copiar lista de compras de Evidence como stock; pertenencia principal/ACL explícita; NULL o desconocido nunca se convierte en cero.
- Solo SmartCompra ya raíz, subpanel stock, Cocina dentro SmartCompra. Nada de nueva root Inventario. No activar V2 productiva, pagos, FARO Auto/Noche o red, sin APIs pagas.
- CI Android: API35 emulador para D1B con XML/artifacts, fuente 200% y prueba de accesibilidad real en runner; no llamar TalkBack físico PASS sin prueba en G15. El wizard SDK/flags/sourceSet nuevo es solo DEV.
- Si unidad pide ruta adicional o API inexistente, NO improvisar: justificar delta B1–B5, guardia, permiso propio actualizado y continuar. No instalar G15.

## Oráculo funcional antes de aceptar UX

Compra10/recibo7/faltan3; reserva2 (físico7/disponible5); consumo1 **no reservado** (físico6/reservado2/disponible4); Undo consumo1 (físico7/reservado2/disponible5); release reserva2 (físico7/reservado0/disponible7). Pagos y preview de receta no mueven nada. Por cada SKU+unidad, sin sumas de unidad distinta. Evidencia de restauración autenticada y pointer/full epoch, y texto “NO DISPONIBLE” si ACL/restore/staleness; no anunciar cero faltante como disponibilidad sin ledger certificado.

## Cierre

A2D DEV CLOSED solo después de A2D2 B7, guardia final, writer RELEASED, checkpoint y permiso BLOCKED. A2E inventario global y G15 físico quedan FUERA (A2E siguiente). Handoff exacto del estado debe poder leerse desde `docs/v5/FARO_CONTINUITY_POINTER.json`.
