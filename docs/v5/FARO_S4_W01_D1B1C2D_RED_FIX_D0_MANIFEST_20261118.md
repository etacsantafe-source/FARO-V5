# D1B1C2D — compatibilidad del auditor D0 con el launcher W01

## B1
Run 44292276661 job 134760213532 llegó al verificador con 8/8 JVM y emitió W01_D0_LAUNCHER_MISSING w01FinanceDev. El verificador histórico exige CoreDevActivity para las dos variantes; el manifiesto W01 ya declara FaroV5W01FinanceDevLauncherActivity (la regresión D1B0 comprueba esa clase). D1B1C2D no cambió manifiestos.

## B2
Reusar verificación de paquete, provider privado, ausencia de Internet y resultados JUnit. Validar la entrada MAIN/LAUNCHER exportada de la clase exacta por variante sin modificar el paquete.

## B3
ADAPTAR auditoría de test al launcher canónico por variante. DESCARTAR agregar el launcher obsoleto al manifiesto, aceptar cualquier Activity o excluir el workflow.

## B4
Agregar solo .github/workflows/faro-v5-s4-w01-d0-isolated-launch-allowlist.yml al permiso RED_FIX, exclusivamente las comprobaciones de launcher/paquete. Scope final exact4 con el trigger Android y los dos archivos del bootstrap.

## B5
Requerir una sola Activity exportada con MAIN y LAUNCHER cuyo nombre coincide con la variante; preservar negativas de provider, conectividad y casos exactos. Exigir D0, guard y XML Android en el nuevo SHA.
