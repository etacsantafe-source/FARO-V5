# FARO V5 — Moto G15 isolated DEV navigation — 5/5 physical bounded PASS

Fecha local de ejecución: 2026-11-12 15:21 AR. Documentación exclusiva de evidencia humana, **no nueva autorización de código**, sin repetir pruebas CI ya certificadas.

## Evidencia exacta del usuario

Luego del primer arranque físico visual PASS de la APK corregida, el usuario ejecutó:
`python3 "$HOME/faro_g15_auto.py"`
El script Termux maneja el G15 por ADB con serial específico y confirma botón de paquete `ar.com.faro.g15isolated.v2coredev` en XML UiAutomator, lo toca, comprueba PID, ausencia de pantalla de inicio y ausencia de `No se abrió:`, inspecciona AndroidRuntime, y guarda captura/log/texto por caso. Todos los resultados impresos en el chat:

```text
1/5 PASS_NAVEGACION: ABRIR CHAT LOCAL DE DESARROLLO
2/5 PASS_NAVEGACION: ABRIR CHAT Y ACTIVIDAD LOCAL
3/5 PASS_NAVEGACION: CONSULTAR AVISOS REGISTRADOS (DEV)
4/5 PASS_NAVEGACION: CONSULTAR VENCIMIENTOS REGISTRADOS (DEV)
5/5 PASS_NAVEGACION: CONSULTAR GUARDADOS LOCALES (DEV)

ARCHIVOS DE PRUEBA: /data/data/com.termux/files/home/faro-g15-isolated/pruebas-20261112-152107
PASS_NAVEGACION no verifica operaciones internas. Reality8 NO fue tocada.
```

La evidencia original permanece **sólo en teléfono/chats**, no copiar al repo público los logs, capturas, ni textos de base personal. Este documento registra un resumen no sensible. El programa `faro_g15_auto.py` se entregó en el chat como herramienta local, no fue agregado al código FARO, y no requiere writer de app.

## Interpretación certificable y límites

**5/5 PASS_NAVEGACION (físico, visual/UIAutomator y PID)**: cada botón fue localizado de forma exacta y pulsado, el UI tree salió de la pantalla inicial y el PID siguió vivo durante la ventana observada; el script no informó cierre de proceso ni excepción `AndroidRuntime:E` del paquete.

**No implica 5/5 PASS funcional:** Script NO verifica contenido de consultas ni integridad de datos, permisos ACL, filtrado owner, guardados, resultado del buscador, persistencia, backup/recovery, Undo, prioridad, visibilidad al mover aplicación a segundo plano, regeneración ni cobertura completa. No demuestra ausencia absoluta de crash fuera del periodo. `Consulta de avisos/vencimientos/guardados` navegó, no necesariamente devolvió filas. La primera prueba puede crear selección/conversación local DEV si la tienda estaba vacía; no afirmar modo READ_ONLY global.

La acción `CREAR REALITY8 VACÍA (DEV)` fue explícitamente **omitida**, sin wipe, uninstall, pm clear, ni cambio de firma. La app habitual `ar.com.faro.alpha.v2coredev` permaneció fuera del alcance de esta automatización. V2 productiva OFF, S4/Auto/Noche OFF. `S3.A5` integral, `X03` live, aceptación física avanzada de `S3.A3` siguen OPEN.

## Siguiente paso sin repetir trabajo

La carpeta de evidencia existente `$HOME/faro-g15-isolated/pruebas-20261112-152107` contiene 5 archivos `*-pantalla.txt`, PNG y registros AndroidRuntime; el usuario puede permitir auditoría local **sin exponer datos personales** de semántica/resultado en cada pantalla. Proponer un verificador offline que reconozca encabezados/estados y no publique contenido de mensajes. Esto no necesita reclicks 5/5 salvo que una anomalía específica lo requiera. No certificar el contenido sin inspección y criterios específicos. No reejecutar 5/5 pasados.
