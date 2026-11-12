# FARO V5 — G15 A3/A4: retest funcional acotado (human-supplied Termux summaries)

Fecha local: 2026-11-12 15:41 y 16:00 AR. Rama `work/s3-night-20261102`. Se hereda `S3_G15_X06_ISOLATED_NAVIGATION_FIVE_PATHS_PHYSICAL_20261112.md` (navegación 5/5 PASS, no repetir).

## Fuente y límite

Resultados originales de Termux compartidos por el usuario, no se recibió copia de archivos PNG/XML ni bases. Los informes completos y capturas permanecen **solo** en el teléfono:

- `$HOME/faro-g15-isolated/a3-a4-20261112-154141/RESUMEN.txt` (script inicial)
- `$HOME/faro-g15-isolated/a3-a4-v2-20261112-160016/RESUMEN.txt` (retoma corregida)

No afirmar una verificación independiente de esas capturas o un PASS de todas las funcionalidades desde solo la salida de consola. El script fue proporcionado para ADB remoto vía Termux, pero no crea ni borra bases, no instala APK y no ejecuta Reality8.

## Primera pasada 15:41

```text
A3-P01-PARCIAL NO_CERTIFICADO — la UI no confirmó el resultado esperado
A3-P02-GUARDADO NO_CERTIFICADO — sin mensaje de prueba confirmado: no guardar
A3-LECTURA PASS_ACOTADO — lector de activos respondió 'Vacío' sin modificar referencias
A3-P03-FUENTE NO_CERTIFICADO — la referencia sintética no fue confirmada; sin tocar referencias
A3-P04-P05-P08 NO_CERTIFICADO — la referencia sintética no fue confirmada; sin tocar referencias
A4-ACTIVIDAD NO_CERTIFICADO — la UI no confirmó el resultado esperado
A4-AVISOS NO_CERTIFICADO — la UI no confirmó el resultado esperado
A4-VENCIMIENTOS PASS_ACOTADO — estado semántico reconocido 'no disponible:'; los casos positivos sin fixture siguen pendientes
```

El usuario luego ejecutó diagnóstico READ_ONLY sobre los archivos guardados y reportó que A3 tenía marcadores `Chat`, `Enviar`, `G15A3`, `Abrir menú` y `Error Android: False`; A4-Actividad y A4-Avisos mostraban `Chat`, `Preguntale a FARO`, `Enviar`, `Abrir menú` y `Error Android: False`. Esa primera prueba de A4 solo abría Chat, **no entraba en las secciones del drawer**, lo que invalida certificar Actividad/Avisos en esa pasada; no interpretar como defecto de FARO.

## Corrección/segunda pasada 16:00

```text
REUTILIZADO: navegación inicial 5/5 PASS y Vencimientos PASS acotado; no se repiten.
A3-MENSAJE-PREVIO NO_CERTIFICADO — El identificador previo no está visible en timeline; no reenviar
A3-GUARDADO-PROPIO BLOQUEADO_SIN_MENSAJE — No hay mensaje sintético confirmado; no guardar ni quitar
A3-CARPETA-SINTETICA PASS_ACOTADO — Carpeta sintética: Crear→Revisar→Confirmar→Releer→Eliminar→Releer
A4-ACTIVIDAD PASS_ESTADO_ACOTADO — Actividad: no hay actividad en las dos fuentes autorizadas.; no demuestra datos positivos sin fixture
A4-AVISOS PASS_ESTADO_ACOTADO — Avisos: no hay avisos abiertos.; no demuestra datos positivos sin fixture
RESULTADOS: /data/data/com.termux/files/home/faro-g15-isolated/a3-a4-v2-20261112-160016/RESUMEN.txt
No hubo Reality8, borrados de datos, instalación, reenvío ni API remota.
PASS_ACOTADO/PASS_ESTADO_ACOTADO NO equivalen a cierre A3/A4 integral.
```

## Dictamen con precisión

- **A3 carpeta sintética PASS acotado** para crear, revisar, confirmar, releer, eliminar y releer. A3 activos vacío PASS acotado, pero no prueba referencias positivas.
- **A3 NO CERRADO:** mensaje USER sintético no fue localizado de forma inequívoca como enviado en la timeline actual; falta observar envío persistido, guardar/quitar/restaurar referencia, fuente original, cancelación, permisos al background, continuidad y recuperación. El marcador `G15A3` en primera lectura NO demuestra persistencia (podría ser borrador). La segunda prueba abortó operaciones sobre referencias no identificadas; no inventar FAIL o pérdida.
- **A4 estados negativos PASS acotados:** con navegación real a Actividad y Avisos vía drawer se observó texto de fuente autorizada sin registros, sin equiparar con FARO total. Vencimientos sin autorización muestra estado `no disponible:`. **A4 NO CERRADO:** eventos positivos/revocación, avisos positivos/autoridad, fuentes Reality8/recibos completos y pruebas semánticas finales requieren fixtures o casos dirigidos autorizados. Nunca inducir dato inexistente con UI o declarar cero global.
- No se reportaron errores AndroidRuntime en diagnósticos selectivos; **no** decir que fueron auditados logs completos o todas las rutas de tiempo de ejecución. No se probó IA real ni se modificó S2/main/V4.4. V2 productiva/S4/Auto/Noche siguen OFF.

## Continuidad eficiente

No volver a navegar todas las cinco rutas. Mantener carpeta de pruebas y manifests. Para A3, obtener el estado UI actual del chat/draft mediante XML y/o detectar mensaje de prueba antes de emitir **a lo sumo un** nuevo envío sintético con texto único, seguido por relectura durable (sin repetir el texto previo `G15A3` que pudo existir). Seguir revisión/confirmación/restauración sobre referencia inequívocamente identificada y comprobar consecuencia. Para A4, conservar PASS negativos; caso positivo necesita un fixture/hospedaje aprobado, no sembrar producción. No utilizar instrumentación que limpie raíces del Moto G15. Nuevas modificaciones de código funcional exigen B1–B5, permiso READY individual y guard.

S3 0/5 CLOSED formal de acuerdo al contrato; desarrollo orientativo NO se recalcula por nueva captura. Writer al terminar debe quedar RELEASED epoch831.
