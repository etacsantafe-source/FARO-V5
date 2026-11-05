# FARO V5 — S3.A2-G1 host-scoped Search grant · B7 PASS acotado (04/11/2026 AR)

## B7 AUDITORÍA FINAL
**Unidad:** `S3_A2_G1_HOST_SCOPED_SEARCH_GRANT`.
**Código inicial:** `e72b6471a50d9d9482dcfcf0040781d94efed3ee`, B7 `RED_FIX` por readiness false→true sin subscriber (auditoría en `docs/s3-night/S3_A2_G1_HOST_SEARCH_GRANT_REDFIX_B1_B5_20261104.md`).
**Código final auditado:** `9257e91fb5530955bdb1ade9fa2a9e8450611ca1`.
**Permiso RED_FIX:** `ccb28cc90a7bb0e3c4dc72ccad63b56bded281cb`, basado en preauditoría de misma unidad. **Dictamen: PASS limitado al host de sesión local, NO certificación G1 completa ni activación V2.**

### CI exacta sobre `9257e91f` — no confundir con otras revisiones
| Control | GitHub Actions run | Evidencia |
| --- | --- | --- |
| Mandatory seven-block protocol guard | `41951964930` | **SUCCESS**; cuatro rutas acotadas, B7 previo RED_FIX y mismo `FARO-UNIT` |
| S3 Home/Workspace/Search/Chat directed | `41951964795` | **SUCCESS**, `BUILD SUCCESSFUL`; XML Home 9/9, Workspace Search 7/7, Bridge 5/5, Avisos 4/4, Chat pointer ABA 6/6, active host ABA 1/1, **Search host grant 6/6** = **38/38**; sin fallos, errors ni skipped |
| V2 core M1/M2 DEV directed | `41951964920` | **SUCCESS**; `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, debug T4 7/7, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4` |
| M5 isolated V2 DEV APK | `41951964962` | **SUCCESS**; paquete `ar.com.faro.alpha.v2coredev`, launcher exclusivo, no OS permissions prohibidos, verificación APK signature v2 PASS; SHA256 `fc7ab3b4e945289a2488889fd0ccb83e14da0bf50c1c3ab219ea4c727cd6aa53`, 62,147,763 bytes |

**Sin instalación Android física ni activación productiva.** Writer CAS se liberó epoch 204 antes de CI. El B7 documental debe liberar posteriormente su propio epoch.

### Verificación adversarial del delta final
- `FaroV5ActiveWorkspaceSearchGrantOwner.epochOrNull()` revoca permanentemente la identidad vieja si `hostAdmitted` pasa a false, antes y también dentro de la segunda comprobación bajo lock; la próxima admisión true **no reanima** el grant. Un regrant exige acto explícito del host y produce un token nuevo.
- `FaroV5CompositionRoot.java` adjunta al host un listener real de `FaroV5StartupReadinessGate`, registrado **antes** de emitir el grant; revoca al perder readiness, revalida snapshot al suscribirse, además de mantener el listener del puntero durable y su sequence. El cierre/excepción elimina listeners y el owner. Search no se entrega a `FaroV5BootstrapActivity` ni habilita la UI.
- Dos tests negativos nuevos: (1) pérdida de admisión observada y retorno a true no revive epoch; (2) ciclo real `FaroV5StartupReadinessGate` false→true **sin Search Flow collector ni lectura intermedia** invalida el grant. Sumados a denegación inicial/cierre, aislamiento A/B, búsqueda canónica con revocación/regrant y puntero A→B→A sin observador = 6/6.
- Diff exacto de `9257e91f`: sólo las cuatro rutas incluidas en el permiso: owner, Java CompositionRoot, test de seis casos, workflow exact-XML. Sin nuevo owner persistente, SQLite schema, FBS2, main, V4.4, egress ni APIs pagas.
- Comparación V4.4 y otras apps previa reutilizada; no se implementa otra fuente de verdad ni se cambia `Workspace` canónico.

### Límites explícitos — NO CERTIFICAR MÁS DE LO PROBADO
- G1 solo tiene **subcomponente de grant/revoke de host local** B7 PASS. No hay todavía verificación de revocación **cross-process** de ACL, restore entre políticas/cuentas, cierre y readmisión productiva real ni una autoridad durable de política multiusuario; `FileActiveGenerationPointer` publica listener local y el material gate es proceso-local. Una colección UI de `Flow` sigue siendo auxiliar, no autoridad.
- `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`; `FaroV5BootstrapActivity` sigue instalando Search `null` en shipping. G2 montaje productivo, G4 federación real de varios owners, G5 Android físico/E2E no terminados. G3 lectura canónica real de texto tiene tests parciales ya aprobados, no reinventarla.
- En recuperación sin cambio de generación, la concesión vieja queda revocada; una nueva concesión tras recuperación debe salir de un *nuevo acto explícito del host*. No se ha demostrado un mecanismo automático de regrant tras mantenimiento sin reapertura de graph: **punto de UX/continuidad a estudiar antes de G2**.
- No usar estos cuatro SUCCESS para marcar 1/5 G1 o 1/5 S3 sin evidencias completas del gate. **S3 ≈40% desarrollo estimado, 0/5 subetapas formalmente cerradas; S3.A2 0/5 gates formalmente cerrados.** S2 8/8 CLOSED, S4/Auto/Noche OFF.

### Próximo paso trazable, sin repetir B7
**S3.A2-G1 policy/restore/observability boundary READ_ONLY:** identificar si la app shipping requiere ACL persistente o sigue single-user/single-process; mapear grant owner ante restore/switch, invalidación entre instancias/procesos y regrant explícito tras mantenimiento. Revisar `AndroidManifest.xml` (no `android:process` propio demostrado en manifests de app) y owner del grafo, pero **no** asumir que otros procesos nunca existirán; pruebas negativas que cubran el alcance real antes de certificar G1. Una nueva unidad funcional requiere B1–B5, `READY`, preflight y writer CAS separados.

**Termómetro:** contexto VERDE; productividad temporal real NO_MEDIDA. No confundir pasos de CI con tiempo efectivo humano.
