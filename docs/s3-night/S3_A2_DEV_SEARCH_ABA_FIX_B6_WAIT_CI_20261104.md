# FARO S3.A2 DEV Search — ABA RED_FIX B6 WAIT_CI (04/11/2026)

**Código corregido exacto:** `c3f5e5d1238556988fe7cec7164641c1840d8e40`; **unidad** `S3_A2_DEV_SEARCH_SESSION_OWNER` (misma del RED_FIX).
**Permiso separado y preflight:** `c1cf987c43bb5c567b0d5a583b1feb398595718c`; `#41933233191 SUCCESS`, `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_DEV_SEARCH_SESSION_OWNER c1cf987c...`.
**SHA inicial:** `8c01f4c4`, CI dirigidos todos SUCCESS pero B7 **RED_FIX** por pointer ABA sin subscriber (doc `S3_A2_DEV_SEARCH_INITIAL_B7_REDFIX_20261104.md`), no repetir a ciegas.

## Delta correctivo acotado
- `FaroV5V2CoreDevAuthority.kt`: fija secuencia durable de FileActiveGenerationPointer antes de abrir graph, exige igualdad en `ready()`; A→B→A no puede revalidar sesión vieja aun sin colector Flow.
- `FaroV5V2CoreDevAuthorityTest.kt`: añade negativa sin listener, CAS A→B→A, old search/locator DENIED; total esperado 10/10, preserva 9 anteriores.
- `.github/workflows/faro-v5-v2-core-directed.yml`: oráculo XML exacto 10/10.
- No modifica V2 productiva, FBS2, persistencia, datos, Activity, permiso Android, paquetes ni catálogo.

## Verificaciones nuevas pendientes, NO PASS aún
- Guard protocolo `#41933426669`.
- V2 core directed `#41933426446`: exigir compilación, XML 10/10, T4 7/7, ID y no permisos.
- S3 Home directed `#41933426482`: exigir Home 9/9, Workspace 7/7, Bridge 5/5, Avisos 4/4.
- APK isolated directed `#41933426599`: exigir SHA del APK generado, firma scheme v2, paquete DEV, launcher exclusivo, no permisos y sin cambios de firma instalable. **No instalar sin revisar firma/identidad.**
- B7 final nuevo: reinspeccionar diff exacto y resultado de test ABA; solo si todo verde declarar PASS para Search DEV, nunca para ACL shipping G1.

**Estado:** B6 WAIT_CI; B7 PENDING; S2 100% 8/8, S3 ~40% estimado / 0/5 formal, S3.A2 0/5 gates; V2 shipping, S4, Auto, Noche OFF.
