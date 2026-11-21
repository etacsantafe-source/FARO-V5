# FARO V5 — W04-C1B2H2A · JSON local comercial tipado Evidence2 — B7 PASS

2026-11-20. Unidad `S4_W04_C1B2H2A_LOCAL_TYPED_JSON_CODEC_DEV`; B1–B5 documentado en `docs/v5/FARO_S4_W04_C1B2H2A_LOCAL_TYPED_JSON_B1_B5_20261120.md`, blob `22c75793eb7822a9f19d7de96458c6dbfed2ddf3`, gate documental `44848082686` SUCCESS. El permiso inicial falló gate por estados B6/B7 incompletos y fue corregido documentalmente en `07ed6f59610768f608678b3e87418de975715593`, gate de permiso `44848285089` SUCCESS (NO ocultar fallo previo `44848181371`).

## B7

**PASS_SCOPED_STAGED_DEV_LOCAL_JSON.** Código funcional SHA `d4199af06dda06b947e4bbed254859e07c23386e`, parent READY `07ed6f59610768f608678b3e87418de975715593`. EXACT3 rutas: `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/CommercialTypedPortabilityJsonCodec.kt`, `v5/maintenance/src/test/kotlin/ar/com/faro/v5/maintenance/CommercialTypedPortabilityJsonCodecTest.kt`, `.github/workflows/faro-v5-s4-w04-c1b2h2a-local-typed-json.yml`.

- Guardia de protocolo en commit de código `44848421273`: COMPLETED SUCCESS, exact3 preflight y política B1–B5 validada.
- CI JVM `44848421308`: COMPLETED SUCCESS, job `136673755396`, `:maintenance:test` `BUILD SUCCESSFUL in 3m 22s`, JUnit XML `(tests=8,failures=0,errors=0,skipped=0)`, salida `W04_C1B2H2A_LOCAL_TYPED_JSON_JVM_XML_PASS 8/8`.
- Artefacto XML `14684940389` `w04-c1b2h2a-typed-json-xml`, 22561 bytes, `expired=false`. Regresión de `:maintenance:test` completa en el mismo workflow.
- El codec es puro en memoria, UTF8 determinista acotado a 8MiB, schema2/perfil distintos, hash H1 comprobado, records ordenados, 14 campos completos por fila, terms físico null distinto de fila UNKNOWN, Unicode/escapes, sourceRef literal, ninguna identidad operativa ni FX. Ocho pruebas nuevas ejercitan integridad, orden, límites, cadenas hostiles, locales/promo, compatibilidad de perfil V1, y determinismo.
- No cambió `PortableBundleJsonWriter.kt`, `PortabilityArtifact.kt`, permisos de egress, V2 productiva, Finance, ACTIVE, FBS1/2, main ni Android. No se escribió archivo/URI ni se hizo transferencia externa. H2A **no es backup** ni exportación del usuario.
- B7 independiente **PASS dentro de este alcance**. Consumir permiso READY H2A→BLOCKED y avanzar con H2B sólo con propio guard documental y READY exact5. La entrega final C1B2 sigue OPEN hasta H2B B7. G15/SAF NOT_RUN.
