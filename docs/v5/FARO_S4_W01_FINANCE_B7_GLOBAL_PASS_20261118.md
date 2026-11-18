# FARO V5 · Auditoría final W1 Finanzas F02–F06

## Alcance

Código final `6b4bf55ad002e50287fcad5616fcc5c6d6b72f0f`, rama `work/s3-night-20261102`. El código se ejecuta en la variante Android DEV aislada y en Reality ACTIVE9. V2 productiva OFF y W2 STOP. La campaña física queda fuera de esta misión.

## Evidencia funcional

| Funcionalidad | Evidencia auditada |
| --- | --- |
| F02 cuentas | Alta revisada, saldo con apertura confirmada y separación de moneda. Review/Apply JVM 44383521628 y Android API35 44385933626. El descarte no crea una cuenta. |
| F03 ingresos y gastos | Operación revisada, saldo y recibo sobre el Workspace activo y Reality. JVM 44383521628 y Android 44385933626. El ingreso fue probado por formulario y el gasto por pago parcial. |
| F04 transferencias | C0 44325818697, 5/5, ambos efectos y vínculo en una sola transacción, con rollback de segundo efecto. Review/Apply JVM 44383521628 y Android 44385933626 probaron la integración. |
| F05 compromisos | Altas 44374508754, pagos 44376679445 y calendario 44380090879, cuatro casos en cada suite. Se conservaron cuotas M+7/M+12/M+36, vencimientos sin asientos, edición versionada, pagos parciales y reales y recuperación. Android 44385933626 ejercitó alta de deuda, calendario, cuota y pago parcial. |
| F06 presupuestos | 44378381509, 4/4, límite mensual, gastos reales, ARS/USD separados y transferencias internas excluidas. Las cuotas sin categoría se muestran como compromisos por moneda y el disponible por categoría queda indeterminado. Android 44385933626 verificó el alta y su consulta. |
| W1-F integración | Android 44385933626, 1 escenario completo. Incluye navegación, formularios, confirmación repetida, recibos al reabrir, fuente 200% y etiquetas accesibles. Regresiones de sesión 44385933330, pantalla 44385933256 y ciclo de vida 44385933398. |

La auditoría contrastó el diff exacto de seis rutas de F2 y de dos rutas para importes heredados con sus respectivos permisos y revisó los XML de las unidades de esta misión. Android integra la sesión existente y la autorización temporal se revoca al salir. Se preservan el catálogo congelado de 180, el esquema Finance9 y los puertos de respaldo. No se activaron proveedores externos ni pagos bancarios.

## B7

**B7 GLOBAL PASS en la variante Android DEV aislada.** El último SHA pasó la prueba integral API35 44391048218, la guardia 44391048200 y la regresión de Avisos de Inicio 44391048317. El escenario integral confirmó también que ARS y USD se editan con dos decimales y que otras monedas importadas se muestran en unidades menores sin convertirlas. Artefacto 14467682623. ZIP SHA-256 `2f57de766a3d05b5c797632d4bd82e2e5141e1ad8af9a13e20d72bcbbe3b2e19`. XML SHA-256 `2001d5b25275b2328596b1d688a0c4b2b5a7ce35bbc1e8c6331c406155de9fbc`. JUnit 1/1 sin fallos, errores ni omisiones, verificado fuera del workflow.

La regresión compartida de S3 44385933212 pasó en el intento 3 sobre el SHA de la integración Android original `447afb3cc941f2726ac722a0226c296d72952f1f`, con los dos casos instrumentados y su control XML en verde. El primer intento falló en un caso con precedente en 44168268363 y el segundo se interrumpió instalando la imagen del emulador. El diff funcional posterior contiene sólo el formulario W01 y su prueba aislada. Los fuentes y los tests de S3 permanecen en el mismo alcance ya certificado.

La auditoría global cierra F02–F06 y W1-F. Writer de código RELEASED epoch 1256 comprobado. V2 productiva OFF, seguridad opcional desactivada por defecto, W2 STOP y ADB limitado a emuladores de GitHub Actions.
