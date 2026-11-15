# FARO V5 — S3 U05 · B7 de aceptación física Moto G15 (alcance comprobado)

Fecha de evidencia humana: **2026-11-14/15**, horario Argentina. Auditoría documental: **2026-11-15**.
Rama `work/s3-night-20261102`; HEAD de entrada `78aa1a7e314be1568116b913e23ee88971d01e5f`; writer CAS adquirido epoch **907**, unidad **DOC_ONLY**.
APK exacta: `ar.com.faro.alpha.s3integrateddev`, versión `5.0.0-dev-s3integrateddev`, `versionCode=66`, SHA-256 `de6c7f24d684453e6d6fffda9c51dfe4efa697540c37aade98172bc6968d7af0`.
Origen binario previo B7: `docs/s3-night/S3_FINAL_INTEGRATED_ARTIFACT_VERIFICATION_20261114.json`; Android 15, moto g15. Evidencia de teléfono aportada **por el usuario en conversación**, sin archivos privados ni secretos exportados al repositorio.

## B1 — Evidencia física, precisión y límites

| Caso | Resultado auditable | Evidencia de esta campaña | Limitación |
|---|---|---|---|
| R00 Reality8 | **PASS_TECNICO** | ADB `run-as` registró origen, active seq=3, journal `COMPLETED`, replay fence `RESTORE`; se verificaron magic SHA256 y bytes; aislado de S2/CORE/X03 | Sin confirmación visual inicial; no repetir botón |
| H01/H02/H04 | **PASS_PREVIO_REUTILIZADO** | Aceptación humana histórica de Chat local, Guardados, Buscar/revocación; remitirse a campaña original | No extender el PASS histórico automáticamente al paquete integrado |
| H03 Actividad | **PASS_FISICO_ACOTADO** | Tras Apply, al reabrir app, entrada **«Aplicación confirmada»** a las `2026-11-15T05:37:04.822Z`, mapeada por `FaroV5ActivitySurface` a fuente `REALITY_COMMITTED_RECEIPT`; indica cobertura parcial explícita | Lectura revocada en pantallas posteriores («Actividad no disponible. No se consultaron datos»); eso no significa pérdida de datos |
| H03 Avisos | **PASS_LECTURA_FUENTE_VACIA** | Pantalla «Avisos», «No hay avisos abiertos»; resumen local **0 atención, 0 informativos**, «cobertura completa de la fuente autorizada» | Sin fixture de aviso positivo; nunca afirmar cero global |
| H03 Pendientes | **PASS_LECTURA_FUENTES_VACIAS** | Vista DEV local de vencimientos, sin registros en Asuntos, Recordatorios y Plan mensual, cobertura completa dentro de ventana almacenada de hasta 7 días | No incluye todos los Pendientes ni expansión de recurrencias; positivo sin fixture **NOT_TESTED** |
| H05 Android 200% | **PASS_VISUAL_FISICO** | Dos capturas del dispositivo: cabecera, botones, scroll y parte inferior legibles, sin superposición de barras Android | Ancho expandido G15 **N_A**, no PASS |
| H05 TalkBack | **PASS_HUMANO_ACOTADO** | Foco verde en «Actividad», lectura verbal confirmada por usuario; navegación de menú y retorno HOME reportados correctos | No sustituye auditoría universal de semántica en todos los componentes |
| H06 Groq | **PASS_RESPUESTA_REAL_OBSERVADA** | Enviar guardó mensaje sin respuesta automática; «Consultar FARO» apareció en sesión nueva, respuesta `Hola, ¿en qué puedo ayudar?`; segundo turno produjo propuesta de cambio | Casilla **«Verifiqué personalmente el plan Free»** marcada por usuario; es atestación humana, NO comprobación independiente de facturación; consentimiento «IA remota autorizada» observado |
| H08 Review/Apply | **PASS_FISICO_ACOTADO** | Groq propuso nombre declarado `Prueba S3`; revisión desplegada, confirmación separada («Todavía no se aplicó»), «Aplicar cambios» una vez, estado «Aplicado y registrado»; tras reapertura seguía; entrada Actividad «Aplicación confirmada» de fuente Reality | UI no mostró hash/fingerprint crudo, cuya protección sigue respaldada por CI anterior; no hay segundo Apply |
| H07 interrupción | **PASS_CIERRE_EN_VUELO_ACOTADO** | Usuario declaró enviar consulta y cerrar FARO mientras esperaba respuesta; al reabrir se vio «No se pudo completar», no publicación tardía observada ni reenvío observado | **NO_CERTIFICAR** la variante exacta HOME mientras vuelo inequívoco: se cerró la aplicación en lugar de documentar HOME específicamente |
| H07 revocación | **PASS_BLOQUEO_REAPERTURA_FISICO** | Usuario desactivó IA y borró clave en Settings; siguiente «Abrir Chat IA integrado» fue rechazado con «No se abrió: IllegalStateException. Los datos permanecen intactos» | Screenshot no prueba por sí solo la causa interna exacta; mensaje de error técnico es defecto UX menor, no prueba de datos borrados |
    
## B2–B5 — Reutilización, criterio adversarial y no repetición

Se reutilizan **sin ejecutar nuevamente** B1–B7 de A1–A5, X01–X12, H01/H02/H04 y Android API35 ya certificados en `docs/s3-night/S3_FINAL_PROGRAMMING_GLOBAL_B7_20261114.md`. B2 compara solo contra propietarios y flujos V5/V4.4 previamente revisados, sin abrir cambios de V4.4. B3 previamente documentado sobre lifecycle, Android sandbox/Keystore y Groq; esta unidad no propone código ni dependencias nuevas. B4: solo conciliación de los resultados observados y cierre de diferencias. B5: no asumir pago Free demostrado por checkbox, no equiparar cierre de proceso con HOME, no inferir un fixture positivo desde un vacío, no exponer clave, no repetir Apply incierto.

## B6 — Cambios y pruebas

**Solo documentación y puntero.** Código funcional: **0 rutas**; APK: sin cambios; no nueva campaña física ni llamadas a Groq. Se conserva la guardia `43590131528` SUCCESS sobre HEAD inicial, y el APK de `43588792359` SUCCESS con SHA exacto de producto. La guardia **del nuevo SHA documental** debe verificarse después de publicar.

## B7 — Decisión de cierre

- **PROGRAMACION_S3_DEV:** `5/5` verificado por B7 previo; sin repetición.
- **CAMPANA_FISICA:** resultados anteriores registrados **PASS dentro de alcances explícitos**; UI de Review/Apply/Activity y revocación reales observadas.
- **U05 B7:** **PASS_SCOPED_EVIDENCE_RECONCILED**, **NO es S3_CLOSED**.
- **S3_CERRADA_FORMALMENTE: NO**; conservar `0/5` como estado formal hasta resolver el criterio de equivalencia/aceptación del caso H07 (HOME exacto vs cierre en vuelo), y decisión humana de cierre. La cuenta Groq Free quedó *atestada en UI*, sin prueba independiente de billing (no exigir ni publicar credenciales/facturas). Los positivos no disponibles de Avisos/Pendientes son límites explícitos de fixture, no falsos PASS.
- **S4 no abierta**; S2, V2 productiva, Auto/Noche, main y V4.4 intactos; permiso funcional BLOCKED. No exigir más código sin B1–B5 de nueva unidad y permiso READY propio.
- **Próximo trabajo:** conciliación humana sobre el alcance H07 y decisión formal S3; solo si resulta indispensable, prueba mínima de HOME en vuelo una vez, sin script ni otra campaña repetitiva. Mantener Groq desactivado si no se autoriza prueba.
- **Writer:** liberar tras checkpoint; no declarar RELEASED ni CI SUCCESS del commit nuevo antes de verificarlos.

## Chequeo rápido de evidencias y trazabilidad
- Hora de recibo Reality: `2026-11-15T05:37:04.822Z` = 02:37:04 Argentina; foto de «Actividad» el mismo día.
- Datos de Avisos/Pendientes: capturas `2026-11-15 11:23–11:24` Argentina; fuente vacía y acotada.
- TalkBack/fuente: capturas y relato `2026-11-15`; H07 interrupción y denegación `2026-11-15`.
- Versión/hash ya verificados por el kit Termux al instalar; no trasladar UID real ni claves al repo.

**Estado U05 al publicar:** DOC_CHECKPOINT_PENDING_GUARD_RELEASE. Se debe revalidar HEAD, writer RELEASED y guard por SHA.
