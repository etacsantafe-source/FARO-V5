# FARO V5 — Decisión humana F3 / S2–S5 (2026-10-30)

Estado: APPROVED / DESIGN DECISION ONLY / NO IMPLEMENTATION CERTIFIED.
Fuente de autoridad: aprobación explícita del usuario en la conversación FARO V5: «Autorizo la opción A», después de solicitar pausar Noche A, Noche B y el Informe final. Esta decisión resuelve el conflicto de RESPONSABILIDAD de UX entre S2.A6-F3 y S5; NO autoriza por sí sola reactivar automatizaciones, ampliar el corte temporal, realizar cutover, ni declarar PASS.

## Decisión aprobada: opción A

1. **S2.A6 es responsable de terminar y certificar F3** (respaldo/exportación y restauración de los cinco almacenes con integridad, autenticación, recuperación segura, compatibilidad histórica y políticas de privacidad/egreso vigentes).
2. Implementar en S2 **sólo el flujo mínimo de usuario necesario**: acción de exportar y restaurar, confirmación explícita específica para cada operación y selección de destino/origen usando SAF (selector de archivos nativo Android). Respetar la política de egreso: no exportar ni compartir automáticamente, informar alcance/destino y no permitir datos sensibles fuera de sus contratos.
3. **S5 conserva la propiedad de la superficie definitiva Ajustes/Seguridad/Mantenimiento.** Allí se integrarán los controles y el estado de respaldos utilizando los mismos contratos/capacidades de F3, sin duplicar su autoridad ni implementar por adelantado toda S5.
4. La UI mínima de S2 no puede convertirse en un super-owner ni alterar Reality fuera de los contratos autorizados. La lógica de respaldo/restauración pertenece a maintenance/capability correspondiente; la UI sólo presenta acciones y consentimiento.
5. Mantener restauración controlada, archivos cifrados/verificados, cinco owners consistentes, cuarentena y fences de recuperación/identidad, no doble efecto ante crash, V1 histórico protegido, V2 OFF, no modificar V4.4 ni FARO Auto, ni main ni repo histórico. No reactivar S3/S4 y no realizar T4 físico por automatización.

## Consecuencia de gobierno

- Bloqueo anterior `F3_S2_S5_CONSENT_OWNER = WAIT_HUMAN` **RESUELTO POR DECISIÓN**, no equivale a `F3 = CERTIFIED`. Registrar F3 como `AUTHORIZED_TO_IMPLEMENT / IN_PROGRESS` o `PENDING_IMPLEMENTATION_AND_CERTIFICATION` en los futuros checkpoints, distinguiendo estado de decisión y estado del criterio.
- Los **ocho criterios originales de S2.A6 siguen congelados**, y la cantidad de certificados sigue siendo **3/8** hasta evidencia completa. Los tests JVM/CI previos de SAF/backup no certifican el flujo UI Android ni T4 físico.
- Para cerrar F3: prueba funcional Android de exportación/confirmación/selector/resultado verificable; importación con autenticación, integridad y restauración controlada de los cinco owners, verificaciones de privacidad y no pérdida de datos; SHA, logs y XML correspondientes; respetar las pruebas físicas T4 aún no realizadas.
- La decisión es una autorización de **alcance/diseño**. Las tareas Noche A, Noche B y el Informe final del 30/10 quedaron PAUSADAS (is_enabled=false). No reiniciar ni reinterpretar el piloto 15:00–18:00 como vigente; requerir nuevo calendario/permiso de ejecución antes de continuar trabajo autónomo.

## Próximo paso

Planificar la siguiente ejecución autorizada de S2.A6 tomando este documento como decisión de producto y docs/cierre-noche/PROTOCOL_V1.md V1.1 como protocolo temporal. Comprobar estado durable y CI actuales, mantener writer único y pasar a F3 sólo al llegar a la brecha funcional prioritaria y con tiempo/permiso vigente. No iniciar trabajo desde este documento.
