# FARO V5 — Sesión manual F1, 31/10/2026

**Modo vigente:** desarrollo acompañado en el chat, **sin FARO Noche ni automatizaciones programadas**. El usuario desactivó explícitamente Noche. No reactivar. La rama `work/f1-auto-v15-20261101` es de código/CI en sesiones manuales; su nombre no autoriza tareas nocturnas.

## Preflight

- F1 fuente `work/f1-noche-v14-20261031-1820`, HEAD `304690d378f02c1553856ca52b6aa7338005cb7b` sin cambios. Legacy writer ACTIVE epoch6 en archivo viejo, pero **owner B3 terminal**, reporte final posterior terminal, lease vencido, Noche A/B apagadas. No modificar su registro histórico ni permitir dos escritores.
- Rama exclusiva de esta sesión, base HEAD `fb619c18c10469d0053a9c673d2af52fefafa152`; cambio de rama mediante CAS expected HEAD, sin escritor de fondo. Estado S2.A6 3/8, F1 OPEN, V2 OFF, T4 NOT_RUN.
- [Actions #41254217573](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41254217573), SHA f7d5b68e, SUCCESS 20/20 JVM, 2/2 Android Keystore y 2/2 Compose/Review/Apply/Delivery. Es histórico, no evidencia del nuevo SHA.

## Cambio de la sesión

Ampliar el gate de Android del workflow existente desde 2/2 hasta cinco pruebas F1 ya codificadas, con oráculo XML de nombres exactos, fallos, saltos y duplicados:
- Consentimiento con Keystore en host aislado antes de abrir stores.
- Composer -> Workspace -> respuesta local durable y recreación de UI.
- Respuesta estructurada -> Review -> Apply aislado -> receipt -> Delivery denegado, idempotencia.
- Delivery OS permitido, sin duplicar publicaciones.
- Recuperación de mensaje interrumpido sin repetir la llamada al proveedor.

No es llamada a Groq real ni prueba física. Agregar la rama manual al filtro de CI y renombrar el nombre visible para no confundirla con Noche. No alterar código de producción ni levantar el gate V2. En CI, verificar SHA, resultado terminal y XML; corregir ROJO sin reintentos idénticos. Tras VERDE marcar solamente LISTO_PARA_T4 cuando todos los gates faltantes estén cubiertos. No iniciar F2–F4.

**Regla de evidencia:** esta nota es una planificación de commit; hasta que Actions termine, el nuevo 5/5 es `PENDING`, no `PASS`.
