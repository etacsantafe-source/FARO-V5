# FARO V5 — S4 W01 Finanzas · decisión explícita: seguridad de acceso optativa, apagada por defecto
Fecha: 2026-11-17 · Alcance: **decisión de producto / SOLO DOCUMENTACIÓN**. No es permiso B6 ni modificación de código. Base: `a7320148a5ec6c289c8a314320f25a9f140d4b58`.

## Decisión de usuario vigente
El usuario rechaza que FARO exija biometría para entrar en Finanzas, y también rechaza que sea requisito para modificar o borrar registros. Si se ofrece un bloqueo local, debe aparecer **sólo como opción en Configuración**, **OFF por defecto**, sin huella, PIN, contraseña ni solicitud de activación automática en el uso normal. El usuario no piensa activarlo. No volver a preguntar ni imponer opciones A/B/C. La configuración de seguridad voluntaria podría ofrecer mecanismos de bloqueo sólo mediante activación explícita, en alcance posterior.

## Efecto funcional de diseño para W01
- La consulta ordinaria de cuentas, saldos, movimientos, gráficos y proyecciones dentro de FARO no debe estar condicionada a BiometricPrompt, AndroidKeyStore de presencia humana o PIN cuando la preferencia opcional es OFF.
- Registrar o editar movimientos locales debe conservar **confirmación explícita de la operación**, validación de alcance, AtomicApply/receipts/Undo/recovery, SQL y writer único según contrato; **una confirmación de revisión NO es biometría obligatoria**.
- No implica conexión bancaria, pagos reales, transferencias reales, exposición al sistema ni nuevas APIs; Finanzas sigue siendo local.
- La protección de datos de Android (UID, sandbox), ACL internos, aislamiento de processes, integridad de Reality, sesiones y revocaciones técnicas **NO son configurables ni se desactivan** por este ajuste. Seguridad opcional = bloqueo visible de entrada/uso, no renuncia a medidas internas.
- El host biométrico READ `S4W01FinanceReadAuthHost` y sus policies previas fueron certificados en alcance **DEV DORMANT**, nunca como requisito de UX validado. **No llamar al host biométrico cuando el ajuste esté OFF**. No crear autorizaciones ficticias o tratar `ON_RESUME` / ContentObserver / DTO de JVM como identidad humana para saltar ACL.
- El dueño real de lectura Finance9 necesita un diseño nuevo de **admisión local sin fricción** compatible con sandbox/UID/generación/epoch y fuente SQL autorizada. Implementar sólo después de preauditoría B1–B5 **nueva**, auditoría independiente y permiso READY por rutas; no eludir la puerta vigente escribiendo un `true` o inventando firmas.
- No confundir "huella no requerida" con "lectura ya implementada": D1B0 sólo muestra pantalla aislada con lector denegado, B7 PASS Android API35 2/2, no datos.

## Siguiente paso acotado
**D1B1 revisado**: diseñar el dueño de lectura real sin solicitud biométrica por defecto, con guardias de sandbox y SQL genuinos, política local de acceso, transición de foreground, reinicio/revocación y controles de no exposición. Después implementar en alcance mínimo y probar Android. La preferencia debe persistirse como `security_lock_enabled=false` por defecto cuando se cree la pantalla de Configuración; no afirmar que ya existe.

## Garantías / exclusiones
V2 productiva OFF, W2 detenido, ningún cambio a S2/S3/Guardados/Avisos, `main`, V4.4, no API paga y no movimientos bancarios. D0, D1A y D1B0 certificados y **NO REHACER**. Este documento cambia la decisión de producto, no altera los gates de integridad ya implementados.

## Checkpoint documental
HEAD previo `a7320148a5ec6c289c8a314320f25a9f140d4b58`; writer único con CAS. No nuevas pruebas funcionales. Guard de protocolo final obligatoria para este checkpoint.