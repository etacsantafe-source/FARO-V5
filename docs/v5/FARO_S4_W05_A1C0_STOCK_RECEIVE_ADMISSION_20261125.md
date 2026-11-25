# FARO V5 · W05-A1C0 · admisión de stock.receive DEV

El catálogo generado tiene 180 operaciones. N-035 registra `stock.receive` como `household / REALITY_MUTATION / BINDABLE / MERGE`. La clasificación BINDABLE habilita declarar un binding con autorización propia. El código funcional del checkpoint no contiene un binding de ejecución ni un grant específico para esa operación.

El host W04 `S4W04HouseholdNeedApplyHost` solo ofrece las operaciones listadas en `HouseholdNeedReviewAction` y Undo de necesidades. Su grant privado fija el contexto, la operación y el ID de necesidad. No habilita una recepción física. `RealityHousehold11ReceiptSchema` contiene una revisión y un registro de recepciones, con `intent_key UNIQUE`; `RealityHousehold11ReceiptRowsPort` es de lectura/restauración sellada. El preview A0 no concede ningún grant.

Para emitir permiso A1C funcional se debe acreditar un grant DEV independiente para `stock.receive` y una fuente confiable para identificar el renglón y la cantidad comprada. El futuro permiso debe acotar la revisión y la confirmación foreground al puntero y generación exactos y a una clave de intento. El criterio principal seguirá siendo 10 compradas, 7 físicas incorporadas, 3 pendientes, y replay del mismo intento sin duplicación.

Estado de admisión A1C: BLOCKED por alcance del grant no acreditado. No se modifica el catálogo ni se activa stock. V2 productiva, pagos, Auto y Noche OFF.
