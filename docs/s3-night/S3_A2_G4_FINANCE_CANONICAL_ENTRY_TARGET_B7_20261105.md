# FARO V5 — S3.A2 G4 Finance target · B7 exact (05/11/2026)

Unidad `S3_A2_G4_FINANCE_CANONICAL_ENTRY_TARGET`; functional SHA `6303c6f77db08832b427bedefa1d718df3cc08c5`; preaudit B1–B5 at `docs/s3-night/S3_A2_G4_FINANCE_CANONICAL_ENTRY_TARGET_B1_B5_20261105.md` blob `74c15ecc1ccb619c44aa924903e9b9904dfb962d`; READY parent `d3f381813c8203c6ff65db94aed22f68055a6958`.

## B7 AUDITORÍA FINAL
- Sólo `FaroV5FinanceSearchReadSource.kt`, su test Kotlin y el workflow dirigido fueron modificados. La resolución del ID privado exige namespace correcto, stamp de publicación previo exacto (generación + ACL epoch + owner revision), lectura canónica y nuevo stamp idéntico. Toda revocación/regrant, cambio de revisión, pérdida de generación, fallo o cierre devuelve `null`, nunca `COMPLETE`.
- El ID devuelto NO autoriza navegación. Faltan portal real de Finance, emisión validada y destino financiero tipado; `FaroV5SearchReadPort` continúa sin destino financiero y no se ha instalado nueva fuente. No se expuso monto/cuenta y no se tocó Reality, manifest productivo, base de datos, S2 ni catálogo.
- Guard obligatorio run [#41969650411](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41969650411) SUCCESS, 14 casos SELFTEST, `FARO_PROTOCOL_GATE_PASS` incluye este commit; permiso preflight READY [#41969474450](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41969474450) SUCCESS y `FARO_PROTOCOL_PREFLIGHT_PASS`.
- Dirigido run [#41969650431](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41969650431) SUCCESS, `BUILD SUCCESSFUL`, Finance **6/6 XML** `S3_G4_FINANCE_SOURCE_XML_PASS 6/6_UNMOUNTED`, Home 9/9, G2 host 10/10, merged manifests single-process 2 PASS. Dos tests nuevos son locator legítimo y revocación/regrant/revisión/owner cerrado.
- Runs adicionales de regresión sobre este SHA iniciados automáticamente: `41969650437` V2 core y `41969650417` APK M5; estaban aún IN_PROGRESS al cierre inicial B7. No declarar su SUCCESS anticipadamente ni cancelar CI. Si alguno es rojo, investigar causa y reabrir sólo unidad afectada antes de nueva implementación.
- No hay Android físico, no se activa V2 productiva, no se crea permiso host. ABA requiere ACL única y nuevo host después de restore, no se presume por cuenta de este resolver.

**Dictamen:** PASS exclusivo para `FINANCE_CANONICAL_ENTRY_TARGET` no montado. **G4 global OPEN** por propietarios Reality durables de Objetivos/Asuntos y host/ACL/nav Finance + federación tipada. G1/G2/G3 CLOSED, G5 físico pendiente, S2 8/8, S3 formal 0/5 y estimación ≈40%, S4, Auto/Noche y V2 productiva OFF.
