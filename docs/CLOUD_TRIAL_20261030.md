# FARO V5 — Prueba acotada CIERRE Nube (2026-10-30)

## Alcance y autoridad
- Repositorio objetivo EXCLUSIVO: `etacsantafe-source/FARO-V5` (PRIVADO).
- Rama de prueba EXCLUSIVA: `work/cloud-afternoon-pilot-20261030`; base de importación: `9bae214541f466a62e149853931b96abb8d74ed9`.
- El repositorio `etacsantafe-source/FARO`, su rama V5, sus checkpoints C138 y las autoridades originales permanecen **solo lectura**. No cambiar main, publicar repositorios, hacer merge, activar V2, S3/S4, modo noche permanente ni firmar APK.
- La autorización conversacional del 30/10/2026 habilita solamente esta prueba de tarde, no es una autorización para publicación, cutover de Reality ni gasto.
- No copiar al repositorio nuevo credenciales, `ci/`, binarios de firma, fuentes V3/V4, historiales ni datos personales.

## Estado heredado (solo referencia; siempre revalidar)
- Origen: C138 CLOSED / writer NONE / S2.A6 OPEN / 3 de 8 criterios certificados; F1/F2/F3 en curso, F4 pendiente, T4 NO EJECUTADO; V2 OFF.
- Conflicto F3: `V5_S2_A6_FINISH_LINE.md` exige consentimiento/UX de exportación dentro de S2.A6, mientras `V5_SURFACE_CUTOVER_DECOMMISSION_PLAN.md` asigna Settings backup/restore a S5. **No decidir este cambio material automáticamente.**
- Los resultados de Actions del original no constituyen certificación del nuevo repositorio.

## Reglas del piloto
1. La automatización puede revisar, auditar, preparar validaciones, y realizar cambios **solo en la rama de prueba**, limitados a CI/seguridad y correcciones acotadas de S2.A6 que no alteren contracts/owners.
2. Antes de escribir: volver a leer HEAD de la rama y `main`, comprobar que no haya ejecución concurrente ni tarea anterior pendiente, obtener evidencia de exclusividad. Si no puede probarse unicidad, quedar READ_ONLY; no crear un segundo writer.
3. Un ciclo por ejecución; nunca asumir trabajo continuo de una hora, ni certificar sin log, XML y commit exactos. Si falla un gate: diagnóstico, como máximo una corrección segura y una repetición; luego detener.
4. La programación original de tarde fue DESACTIVADA a petición expresa del usuario el 30/10/2026; las verificaciones se ejecutaron inmediatamente en esta conversación. No reactivar automatizaciones.
5. Configurar `GITHUB_TOKEN` con permisos mínimos, por defecto `contents: read`; nunca agregar secretos ni acceso de escritura a jobs de compilación. Controlar el consumo de minutos; no iniciar gastos.
6. Nunca usar Termux como ejecutor permanente ni heredar permisos históricos D-509 del repositorio antiguo. Si una operación requiere autorización que esta prueba no otorga, STOP/NO WRITE.
7. La rama de prueba no es autoridad durable del producto. Sólo tras revisión y autorización explícita se podría fusionar o hacer un handoff. Mantener V1 activo y T4 físico pendiente.

## Salida esperada
Informe por ejecución: HEAD exacto, cambios realizados o ninguno, gate/artefactos verificados, incidencias, estado S2.A6 3/8 salvo certificación completa, y siguiente acción segura. Al final del piloto, no dejar ninguna nueva automatización recurrente activa.

## Resultado verificable de ejecución inmediata — 30/10/2026
- **Programación de tarde cancelada / desactivada.** Ningún modo noche permanente activo.
- Rama exclusiva de prueba; source commit certificado: `4325dd5c603b944ca29b5da772862840db5fc58c` (solo CI + 3 especificaciones congeladas).
- GitHub Actions **41058435443**: terminal **SUCCESS**, enlace https://github.com/etacsantafe-source/FARO-V5/actions/runs/41058435443 .
- `v5StructuralCheck` → `FARO_V5_T0_PASS`, `appCompositionPolicyCheck` → `FARO_V5_APP_P5_A8_PASS`.
- Spec bridge → `FARO_V5_SPEC_BASELINE_PASS`.
- `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac` → BUILD SUCCESSFUL.
- XML real `BackupExternalWriteT2Test`: tests=4, failures=0, errors=0, skipped=0.
- XML real `FaroV5ReviewedApplyHostAdapterTest`: tests=3, failures=0, errors=0, skipped=0.
- XML real `FaroV5ReviewedApplyHostIntegrationTest`: tests=2, failures=0, errors=0, skipped=0.
- Marker exacto: `FARO_V5_NEW_REPO_DIRECTED_XML_PASS 9/9`.
- **NO** fue ejecutada la prueba física T4; no promover por ello F1/F3 ni S2.A6 a CERTIFIED.
- Los tres documentos necesarios por `faro.spec-bridge` se importaron exactamente desde el origen C138: `V5_CAPABILITY_TAXONOMY.md` (blob `b272f50aba9aeae525c8baf19a87b65f08bb8bf7`), `V5_OPERATION_CONTRACT_INVENTORY.md` (`a161f7f57eaf6285edb1f250ce691d07725b32dc`) y `V5_RG06C_07_GLOBAL_REFREEZE.md` (`34be34a82c7a96357048ac64a1a8c1b2fdfc5418`).
- Árbol `v5/` idéntico al origen en ambos repositorios: `917aad35fbcf8d4c82e9170f5a615814894da8f6`.
- Estado producto: S2.A6 OPEN 3/8, F1/F2/F3 IN PROGRESS, F4 PENDING, V2 OFF, S3/S4 no iniciados.
- Handoff pendiente: revisar seguridad global, decidir conflicto F3 UI S2 vs S5, completar integración/regresiones/T4, y autorizar por separado el cambio de autoridad.
- **Sin merge, sin publicación del repo, sin cutover, sin permisos de producción, sin firma, sin cambios de V4.4/FARO Auto.**
