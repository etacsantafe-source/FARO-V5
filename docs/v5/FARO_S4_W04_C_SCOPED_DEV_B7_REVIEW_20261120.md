# FARO V5 — W04-C · auditoría de conjunto de código DEV, alcance limitado

## B7

**PASS_SCOPED_DEV_CORE / PRODUCT_ACTIVE_INTEGRATION_OPEN.** No repetir certificaciones anteriores:
- C0A enlace necesidad-producto: B7 CI `44782895246` 12/12.
- C0B lectura con doble grant: B7 CI `44789820660` 9/9.
- C1A registro manual schema1: B7 CI `44803997142` 9/9.
- C1B1 terms schema2 STAGED: B7 CI `44813495451` 9/9.
- C1B2 E/F/G/H1/H2A/H2B backup/FBS1v4/cinco stores/portabilidad tipada/JSON/aprobación: `docs/v5/FARO_S4_W04_C1B2_GLOBAL_B7_PASS_20261120.md`, H2B CI `44849294556` 10/10 y regresión Guardados `44849294456` SUCCESS.
- C2 comparación histórica tipada sin FX, separación de localidad/moneda/packs y cobertura no confirmada: `docs/v5/FARO_S4_W04_C2_TYPED_PRICE_COMPARISON_B7_PASS_20261120.md`, CI `44850685841` 14/14, guard `44850685830`.
- C3 refresco append-only con Review, consentimiento, CAS/fences y términos: `docs/v5/FARO_S4_W04_C3_TYPED_APPEND_B7_PASS_20261120.md`, CI final `44852084693` 12/12, guard `44852084952`, tras corregir un único fixture de ACL.

**V5 preservado:** Evidence≠Reality≠Finance, observedAt nullable distinto de acquiredAt, términos UNKNOWN sin certeza inventada, no mezclar ARS/USD ni presentaciones, sourceRef literal, historia intacta, no stock ni pago por registrar precio. El comparador C2 y el refresco C3 son servicios STAGED DEV.

**BRECHAS NO CERTIFICADAS:** montaje ACTIVE real de Evidence2 y acceso desde app, UI carrito/precio, Android API35 nuevo, G15 físico, SAF real y cotización actual confirmada. C0B/C1A legacy siguen schema1, C2/C3 son STAGED-only. Por tanto **W04-C como producto end-to-end sigue OPEN**; no declarar B7 productivo ni W04-F. El siguiente W04-D inicia sólo diseño, con auditorías y permisos independientes, no hereda READY.
