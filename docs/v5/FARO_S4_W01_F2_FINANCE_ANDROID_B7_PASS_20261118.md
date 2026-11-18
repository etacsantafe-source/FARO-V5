# FARO W01 F2 · Finanzas integradas en Android DEV

## B6

SHA `447afb3cc941f2726ac722a0226c296d72952f1f` y permiso anterior de seis rutas. Coinciden el diff y las rutas verificadas por preflight. La Activity aislada usa la sesión Finance9 ACTIVE9 que prestó el launcher. La implementación cubre lectura de cuentas, saldo con procedencia, movimientos, transferencias, calendarios, cuotas, pagos y presupuesto. Cada mutación se prepara para revisión en Workspace y se confirma en Reality por el host de Review/Apply certificado. La lectura muestra compromisos sin categoría y disponible indeterminado cuando corresponde. La reapertura recupera recibos desde Reality. El escenario ejecutó una confirmación repetida y un descarte explícito. El préstamo se revoca al salir.

## B7

PASS acotado a W01 Android DEV. CI API35 44385933626 y guard 44385933335 SUCCESS en el SHA de código. El XML se analizó aparte del workflow y corresponde al escenario de la Activity real, 1/1 sin fallo, error ni omisión. Fueron probados cuentas, ingreso, transferencia, presupuesto, deuda, calendario, vencimiento, pago parcial, reapertura de recibos y controles a fuente 200%. Artefacto 14466358296. ZIP SHA-256 `844435e635ead85c2eef13b98a17bfb70734242d9e4ab4cb7e3880ab0b215384`. XML SHA-256 `61cb5b78b0d46edc69711a70609216f87e5ee7e610ba5f6235de492cbdcbbd54`. Regresiones del mismo SHA en sesión Finance9 44385933330, pantalla 44385933256 y ciclo de vida 44385933398 SUCCESS. El Android principal y el catálogo de 180 no cambiaron. Writer del código RELEASED epoch 1252.

El cierre global espera la segunda ejecución de 44385933212, regresión automática del host S3. El primer intento falló buscando un elemento del Workspace en el caso `nativeSourcesShareCanonicalWorkspaceRealityAndGeneration`. El mismo caso y excepción ya aparecen en 44168268363, anterior al código de este turno. El reintento conserva exactamente el SHA. V2 productiva OFF, W2 STOP y sin campaña física.
