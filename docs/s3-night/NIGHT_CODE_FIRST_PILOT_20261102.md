# FARO Noche — piloto CODE-FIRST preparado (sin activar Noche)

## Alcance y estado
Fecha de preparación: 2026-11-02. **Noche A/B e informe OFF, cola CLOUD STAGED, activation_authorized=false.** La autorización de Noche V2.2-R1 fue cancelada por el usuario. Este trabajo es mantenimiento manual del mecanismo de ejecución, NO una reactivación, NO una prueba física, NO una certificación de la autonomía.

Objetivo: que una sesión con trabajo disponible **seleccione una prueba/cambio concreto antes de responder**, y no vuelva a describir OPP-A1-ADV como hallazgo nuevo.

## Componente añadido
- `scripts/v5/faro-night-mission-gate.py`: planificador READ_ONLY basado en la cola compartida, el writer, el banco, el HEAD verificado y el horario programado. Nunca muta GitHub, adquiere writer ni lanza Actions.
- `scripts/v5/test_faro_night_mission_gate.py`: 17 pruebas Python de fail-closed, ventana de tiempo, writer ocupado, deduplicación y verificación del formato de evidencia; **17/17 ejecutadas localmente en contenedor**, no GitHub Actions/XML ni pruebas Android.
- Una ejecución con cola STAGED debe devolver STOP. Con datos de autorización simulados y writer libre, la primera unidad es TEST_FIRST sobre OPP-A1-ADV, no un informe duplicado.
- La validación de JSON puede confirmar **FORMATO** de commit/run/xml, pero **no certifica la CI**. La certificación exige consulta independiente al run real, SHA, estado y XML.

## Contrato del ejecutor para un futuro piloto HUMANO
1. Leer repo/cola/writer/puntero/HEAD reales, validar autorización NUEVA y deadline. Cuando STAGED, STOP sin Git writes/Actions.
2. Seleccionar **UNA unidad concreta** con fichero y aceptación; para la primera prueba futura: OPP-A1-ADV, `FaroV5HomeIndicators.kt`. Caso: Avisos visible=3, revocar permiso sin cambiar identidad de puerto, recomposición y exigir No disponible. **No está reproducido ni arreglado**.
3. Si hay mutación autorizada, adquirir un único writer mediante expected SHA CAS, verificar owner/epoch/HEAD y escribir solo rama S3; liberar writer propio inmediatamente al terminar y verificar RELEASED. Un timer por sí mismo NO concede writer.
4. Ejecutar test dirigido si existe entorno seguro, revisar RUN + XML del SHA nuevo. Si no existe infraestructura de test Compose, registrar bloqueo **nuevo** con referencia exacta y experimento falsable, no inventar PASS.
5. Tras un PASS verdadero, intentar otra unidad READY independiente dentro del tiempo restante, sin saltar gates ni repetir SHA/XML. Una ejecución de segundos sin código ni bloqueo nuevo **NO es éxito** y no justifica automatizar otras ocho horas.
6. Antes de una noche completa realizar **dos sesiones piloto supervisadas**, medir started/completed reales y commits/CI/duplicaciones; el piloto también requiere nueva autorización expresa. Parar si no produce mejoras.

## Límites importantes
- El código actual es **selector/validador**; no se transformó ChatGPT Automations en un agente persistente ni se garantiza que ejecute 20 minutos. Un resultado `EVIDENCE_FORMAT_VALID_REMOTE_UNVERIFIED` no es CI PASS.
- No se modificó el catálogo 172 ni Guardados +8; no se abrió S4 ni activó V2 app.
- No se rehízo S2 8/8; main/V4.4 intocables. El banco conserva 7 tareas.
- V22-01..19 y X01..12 siguen NO_RUN salvo futura prueba/artefacto específico.
