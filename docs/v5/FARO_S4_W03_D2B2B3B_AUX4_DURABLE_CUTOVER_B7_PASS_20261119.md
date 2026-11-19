# FARO V5 — W03-D2B2B3B · B7 del cambio durable a Auxiliary4 DEV

Unidad S4_W03_D2B2B3B_AUX4_DURABLE_CUTOVER_DEV, código 032bbea4e8e9819a158dd142f6b8a84828cd0f54. Scope DEV JVM, sin host/UI. W03 sigue abierto.

## B1–B5
docs/v5/FARO_S4_W03_D2B2B3B_AUX4_DURABLE_CUTOVER_B1_B5_20261119.md, guardia 44608397244 SUCCESS. READY exacto 04a99734851e16a006d96bea3edc1ca8fc5563da, guardia 44608633305 SUCCESS, preflight ocho rutas.

## B6
Runtime DEV usa el mismo root lease de Finance9 y un ticket Auxiliary4 distinguible y durable. La copia de cinco stores se verifica sellada, conserva las otras DB y migra solamente Auxiliary en la copia. Aceptación foreground antes de preparar y antes de autorizar switch, binding de puntero y huellas origen+destino antes de publicar, journal, replay fence antes de escrituras, recuperación por secuencia. Rollback reabre Aux3 sobre Finance9. La ruta W1 original se mantiene y sus jobs exactos de certificación no se repiten en un cambio de W03.

CI 44609511296, job 135862036877 SUCCESS. Guardia 44609511316 SUCCESS en código. Artefacto 14571832643, ZIP sha256:ae85e2fb5953cfc60857b7b50c03e5c6fa441438a9178b44fa90a9f88327edcb verificado, XML 6/6 sin fallas, errores ni skips. Casos: migración solo copia y reabrir, deny inicial y revocar tras preparar, crash exacto después de publicar puntero sin segundo switch, rollback si falla salud y ALTER de la copia después de autorizar bloqueado antes de switch.

## B7
PASS acotado de cutover y recovery DEV. READY consumido; verificar guardia del checkpoint y writer RELEASED. Siguiente docs/v5/FARO_S4_W03_D2B2B3C_ACTIVE_NOTICE_RECEIPT_HOST_B1_B5_20261119.md: recomponer la intención local suprimida tras restore y conectar Avisos con receipts y grants en Session. Después cierre D, UI E y certificación G. W3-F diferido y V2 productiva OFF; no pruebas Moto G15.
