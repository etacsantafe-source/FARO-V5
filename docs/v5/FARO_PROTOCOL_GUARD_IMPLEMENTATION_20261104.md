# FARO V5 — BLOQUEO TÉCNICO DEL PROTOCOLO (04/11/2026)

**MANDATO HUMANO:** el protocolo no debe depender de que el asistente se acuerde. Este control añade una barrera verificable por GitHub Actions y un preflight ejecutable para todos los cambios funcionales en `work/s3-night-20261102`. **El protocolo NO sustituye revisión de código o criterio humano; tampoco se amplían permisos.**

## Tres mecanismos, no otro documento de intención
1. **Preflight antes del writer:** `python3 scripts/quality/faro-protocol-gate.py --mode preflight --unit M2A --path v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`. Fallará `STOP` hasta completar auditoría y emitir permiso durable por unidad. Para escrituras exclusivamente de documentación destinada a recuperar el protocolo, no se necesita permiso funcional. Un agente que no pueda probarlo DEBE abstenerse de escribir código.
2. **CI obligatoria para evaluar:** `.github/workflows/faro-v5-protocol-guard.yml`, job `seven-block-evidence`, se ejecuta en cada push/PR contra la rama; usa checkout completo y recorre **cada commit desde baseline `197e68bc1d9c52917a9066a27e6a0fa235a28217`**. Un commit posterior solo de documentación no oculta un cambio previo de código sin permiso. Los cambios protegidos son `v5/**`, `scripts/v5/**` y workflows funcionales (excluido el propio guardia).
3. **Permiso de una unidad:** `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` comienza **BLOCKED**. Solo cambiarlo mediante **commit independiente documental**, después de verificar B1 evidencia, B2 comparación V4.4, B3 investigación de alternativas, B4 unificación y B5 auditoría adversarial. Cada etapa exige `VERIFIED` y referencias `docs/**` existentes con **SHA exacto de blob** y marcador de sección. El permiso limita `unit_id` y rutas `paths`; B6 `READY_TO_IMPLEMENT` y B7 `PENDING_FINAL_AUDIT`. El commit funcional lleva `FARO-UNIT:<id>` y **NO** puede modificar simultáneamente el permiso/puntero/estado de auditoría. Antes de OTRO commit funcional debe existir un B7 del anterior, con `PASS` o `RED_FIX`, prueba con SHA real; `RED_FIX` solo continúa la misma unidad.

## Alcance y evidencia, sin nuevos círculos
- El gate no exige repetir B1–B5 para pruebas históricas exactas y PASS de SHAs previos; conserva bloques validados por la misma unidad y alcance. **Si cambia la hipótesis, revisar SOLO el bloque afectado** y actualizar evidencia.
- El primer cometido, antes de autorizar más código de V2, sigue siendo la auditoría retrospectiva acotada M0/M1/M2A/M2B/M3, según `FARO_V2_PROTOCOL_RETRO_AUDIT_20261104.json`.
- Un `M2_M3_JVM_PASS 4/4` o `T4_DEBUG_PASS 7/7` no demuestra que el manifest final esté bien ni elimina la auditoría restante.
- El guardia genera **STOP en CI**, no revierte commits ni impide técnicamente que GitHub reciba un push. Para que el servidor **impida integrar** commits rojos hace falta marcar `seven-block-evidence` como **required status check** en las reglas de protección de la rama, acción administrativa distinta a escribir el workflow. **No afirmar que está protegido a nivel servidor si esa regla no fue observada.**
- El propio guardia vive en `scripts/quality/` y se comprueba con 14 tests sintéticos; la excepción de bootstrap abarca únicamente el archivo propio `faro-v5-protocol-guard.yml`. Las modificaciones futuras al guardia o CI deben ser auditadas; esta excepción no es una garantía antifraude contra administradores que puedan alterar las reglas.
- Para el paso a otra rama se deben copiar/ajustar explícitamente triggers y baseline; no asumir que el guardia protege `main` o repos históricos. Ningún cambio en `main` está autorizado por esta instalación.

## Orden en el chat nuevo
1. Recuperar HEAD, writer, cola, CI, este documento, puntero y permiso BLOCKED.
2. Cumplir B1–B5 retrospectivo de M0/M1 y M2/M3 sin escribir más función; documentar decisiones V4.4/reuso/negativas.
3. Publicar evidencia en un commit documental; emitir permiso de unidad en otro commit documental con blobs SHA reales. Verificar `--mode preflight` **PASS** antes de adquirir writer para código.
4. Implementar únicamente el delta permitido; CI y B7 antes de siguiente delta. Si FAIL, volver al bloque causal.
5. Cerrar checkpoint y liberar writer; S2 CLOSED 8/8, S3 ≈40% funcional (0/5 formal), S4 OFF, V2 productiva OFF, Auto/Noche OFF, catálogo 172.

**Advertencia de alcance:** el objetivo es hacer el cumplimiento observable y automarcado como FAIL, sin prometer protección de rama no configurada.

## Evaluación de esta propia implementación por los siete bloques (no excepción informal)
**B1 EVIDENCIA:** el código existente exigía `FARO_CONTINUITY_POINTER.json` y protocolos `FARO_DEVELOPMENT_UNIFIED_PROTOCOL_V2_20261102.md` / `FARO_S3_NOCHE_BLOQUES_DINAMICOS_V1_20261103.md`, pero hasta este checkpoint no existía un status-check por cada modificación funcional que pidiera B1–B5 previos en el padre del commit. Los fallos repetidos de M2 muestran que los documentos no bastan.

**B2 COMPARACIÓN:** V4.4 y el writer CAS siguen siendo las autoridades de comportamiento/ejecución y **no se modifican**; esto es un **control de desarrollo Git**, no una función que deba copiar interfaz ni datos de V4.4. Reusa el workflow CI ya instalado y el gate de exclusividad, sin crear otro writer.

**B3 INVESTIGACIÓN DE ALTERNATIVAS (acotada):** (A) recordatorio solo en el prompt: DESCARTAR por omisiones previas. (B) pre-commit local: COMPLEMENTO, pero se puede olvidar/desactivar y no hay instalación uniforme en cloud. (C) CI histórica sobre commits + preflight local: ELEGIDO como guardia automático verificable. (D) GitHub required status check/ruleset: MÁS FUERTE para bloquear integración; requiere configuración de protección con permiso administrativo, y `GET /rulesets` devolvió `[]`. No se afirma protección remota obligatoria. No hacen falta APIs de pago, secretos ni librerías externas para el gate.

**B4 UNIFICACIÓN:** `scripts/quality/faro-protocol-gate.py` único validador, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` único permiso, `faro-v5-protocol-guard.yml` único control CI, y **puntero canónico existente** como entrada. No se agrega autoridad de negocio ni nuevos forks de protocolos.

**B5 AUDITORÍA ADVERSARIAL ANTES DE ESCRIBIR:** casos de CI sin permiso, permiso agregado en el mismo commit funcional, permisos globales, cambio fuera de alcance, marcadores/EVIDENCIA ausentes, SHA de documento alterado, falta de `FARO-UNIT`, otro commit sin B7, FIX que invade otra unidad, documentación posterior que intenta esconder cambios, alteración simultánea de estado/función. Se incluyen pruebas sintéticas y baseline fijo. Límite residual conocido: el usuario/administrador puede modificar o deshabilitar la propia CI o hacer push a una rama sin status check required; evitar declararlo infalible.

**B6 IMPLEMENTACIÓN / PRUEBAS:** los artefactos Python y CI se publican únicamente como herramientas fuera de `v5/**` o `scripts/v5/**`, junto al permiso inicial `BLOCKED`. 14 pruebas sintéticas y ejecución en GitHub en este SHA. **STATUS: CI PENDING** al crear el documento.

**B7 AUDITORÍA FINAL:** exigir evidencia de run/test real, SHA de protocolo, que `FARO_PROTOCOL_GATE_PASS` aparezca en logs, writer RELEASED y puntero/permiso verificables. **NO CERRADO** hasta observar esos datos.

**Nota:** estas pruebas representan el protocolo de ejecución de un agente, no un tercero independiente con autoridad para otorgar permisos. El estado PREAUDIT de V2 continúa bloqueado.
