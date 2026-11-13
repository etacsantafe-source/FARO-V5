# FARO V5 — P06 · Especificación UX final documentada

2026-11-13 (Argentina). Unidad `S4_PREP_P06_FINAL_UX_SPEC`. **Producto documental y decisiones humanas registradas; sin aprobación de programación, S4 formal OFF.**

## B1 — Evidencias, fuentes y alcance

Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102; HEAD origen `d095782fd32badac1e7a633f19df1151d8709720`. Writer RELEASED epoch867 al iniciar y HELD epoch868 adquirido por CAS para publicar SOLO documentación. Permiso funcional FARO_PROTOCOL_WORK_PERMIT.json BLOCKED, S3 pausada, S4 formal OFF, V2 productiva/Auto/Noche OFF.

Reusar P00 D01–D33 blob 09d47f8a5c2ed5f02a97a4b1c68aec50ffd5ea41; P01 V01–V30 blob 5449938c3e5e8fbe5e18a2c7694989e1e5e4af96; P02 L01–L20 blob 9a6388ae6be298eb6536a3dc81e1266bf4d42385; P03 R01–R20 blob 4b9789004459eed6e95bc4d4f8032ee3b4c9f340; P04 B01–B09 blob 3f43ac170e812e3f363f02dea995d4a827de117b; P05 22 flujos producto blob 76eba232db78237e53d06bf9725dd73f6ec5dd2d. No retestar histórico ni código por este documento. AGENTS y protocolo retro-auditoría obligatorios.

El usuario aprobó de manera explícita: estética de la captura, Herramientas con Guardados/Actividad/Avisos, gráfico compacto anual expandible de saldos, resumen cuentas y mes actual detallado con cinco próximos abreviados, Radar opción C (Inicio+Chat+Asuntos) y línea de cero con toda evolución negativa en rojo. **U01–U06 son decisiones humanas de P06, U08–U11 no se elevan a aprobadas.**

## B2 — Comparación V4.4 frente a V5 comprobada

V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/FaroV4Shell.kt` @blob 28dc17b8e6ce3e3e7c63c3246c87cf1869330608 ya tenía menú, fijados/recientes, Buscar y experiencia financiera/Radar real (históricamente el gráfico se desplazó a Radar). V5 `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` @blob e40ac8fcbf65014149cfd97a797166e55f40004f posee destinos CHAT/INICIO/FINANZAS/OBJETIVOS/ASUNTOS/SMART_COMPRA/AJUSTES y secundarios BUSCAR/GUARDADOS/ACTIVIDAD/AVISOS; **actualmente todos los secundarios son filas planas**, no el acordeón ni búsqueda de cabecera. V5 `FaroV5AdaptiveShell.kt` @blob 3289bb793369d2a29d9ee86494f0dd446894ef22 soporta drawer modal/permanente por umbral minWidthDp >=840; no prueba física G15. Destinos pueden ser placeholders: P06 no afirma integración Finance/SmartCompra/Radar. La decisión moderna prevalece sobre ubicación del gráfico en Radar legacy.

## B3 — Alternativas y criterios de elección

Conservar **lineamiento visual de la captura elegida**: oscuro sobrio, líneas/divisores, iconos sencillos etiquetados, tarjetas contenidas y espacios claros; también tema claro y accesibilidad (no tema oscuro obligatorio). Rechazar barra inferior, duplicados Chat/drawer, raíz Radar, módulo Cocina aislado, migración de runtime legacy y adopción de código ajeno. Referencias Actual Budget/Grocy/KitchenOwl y Android Compose de P04 son solo patrones; no APIs pagas, sync ni servidores obligatorios. Preferir información progresiva: tarjeta de resumen -> detalle por interacción -> análisis ampliado, nunca certeza artificial.

## B4 — Matriz de decisiones P06 con autoridad explícita

| ID | Tema | Estado | Contenido aprobado o pendiente |
| --- | --- | --- | --- |
| U01 | Referencia gráfica de la captura | APROBADA_USUARIO | Oscuro sobrio, tarjetas, separadores, iconos y rótulos humanos; mantener accesibilidad/tema claro, no fijar colores exactos de todo UI |
| U02 | Herramientas | APROBADA_USUARIO | Acordeón dentro de drawer para Guardados, Actividad, Avisos; búsqueda única en cabecera |
| U03 | Gráfico compacto anual | APROBADA_USUARIO | Evolución de saldos: 6 meses pasados + actual M0 + 5 futuros = 12 meses; expandible |
| U04 | Finanzas en tres bloques | APROBADA_USUARIO | Gráfico, resumen de cuentas debajo/junto, M0 detallado y M+1..M+5 resumidos; tocar mes para detalle |
| U05 | Radar opción C | APROBADA_USUARIO | Tarjeta Inicio, consulta Chat y alertas contextualizadas en Asuntos; sin raíz Radar |
| U06 | Cero y negativos | APROBADA_USUARIO | Gráfico compacto incluye línea de referencia cero visible; TODOS los tramos/áreas con saldo bajo cero se muestran en rojo; series reales vs proyectadas se diferencian además por estilo de trazo/etiqueta |
| U07 | Drawer Chat-first | APROBADA_P00_D02_D08 | Cinco módulos, Chat landing sin fila redundante, fijados aparte de siete recientes, pie Nuevo chat y Ajustes |
| U08 | Filtros gráfico expandido | PROPUESTA_NO_APROBADA | Rangos/monedas/cuentas/comparaciones, supuestos de predicción y detalle visual por validar P07 |
| U09 | Matemática/ownership | PENDIENTE_P07 | Balance, corte asOf de M0, proyección, operaciones cross-owner, cuotas M+7/M+12/M+36 |
| U10 | Navegación durante Apply/IA | PENDIENTE_P07 | No guard global V4.4, estado/draft/fingerprint/receipt preservados, UNKNOWN no se reintenta |
| U11 | Privacidad y egress | PENDIENTE_P07 | Fuentes de Radar, ACL, owner de compras/stock/cocina y permisos de notificación |

## B4 — Árbol aprobado del drawer

```text
FARO                                      [Buscar]
────────────────────────────────────────────────
Inicio
Finanzas
Objetivos
Asuntos
SmartCompra
────────────────────────────────────────────────
Herramientas                               [⌄]
  Guardados       (cuando expandido)
  Actividad       (cuando expandido)
  Avisos          (cuando expandido)
────────────────────────────────────────────────
Fijados            (sin cupo de recientes)
Recientes          (máximo 7 no fijados)
  Ver todos / Ocultar anteriores
────────────────────────────────────────────────
Nuevo chat
Ajustes
```

Chat abre primero y no es otra fila del drawer. El acordeón Herramientas es visual, no crea un owner; cerrado inicialmente como referencia de limpieza, expandible y con estado accesible. Buscar tiene un único acceso en cabecera. Pie accesible, scroll central y foco único sección O conversación; fijados fuera del cupo, archivados recuperables y regla cold start D08. Adaptación a rotación/split/200% fuente y teclado IME.

## B4 — Finanzas: UX de 12 meses, cero rojo y 6 meses operativos

**Bloque 1, gráfico compacto: Evolución de saldos.** Doce posiciones: M-6,M-5,M-4,M-3,M-2,M-1 (seis pasados), M0 actual y M+1,M+2,M+3,M+4,M+5 (cinco futuros). **Eje horizontal del saldo cero ($0 / equivalente según moneda elegida) siempre visible en compacto**, aun si todas las posiciones son positivas o negativas. La referencia cero no deriva de un saldo faltante: es la constante matemática 0. No fijar un eje que oculte el cero para exagerar cambios.

**Regla cromática aprobada U06**: la evolución y la zona sombreada por debajo del nivel cero se representan en **rojo**, desde la intersección exacta con 0; al subir por encima dejan de ser rojas. Si la línea cruza 0 entre meses, el cruce visual divide correctamente los tramos, sin volver rojo todo un mes positivo ni el área superior. Los saldos negativos históricos REAL se diferencian de negativos futuros ESTIMADO/COMPROMETIDO mediante línea continua/punteada, leyenda, descripción y etiquetas visibles. El color nunca es la única señal: indicar 'saldo negativo' o 'saldo previsto negativo' en lectura textual accesible. Si falta dato no dibujar segmento interpolado ni falso saldo cero. ARS/USD por separado o selector de cuenta; no agregarlos nominalmente sin fuente/consentimiento de conversión. M0 separa saldo real al corte asOf de proyección a fin de mes; algoritmo P07 pendiente.

**Expandir gráfico** abre vista más completa dentro de Finanzas, con mayor espacio y análisis, propuestas de filtros por rango/cuenta/moneda, escenarios y supuestos a estudiar en P07; no aprobar por inercia pronósticos >M+5 ni crear raíz Proyecciones/Radar. La expansión es lectura; no modifica Reality.

**Bloque 2, resumen de cuentas**: debajo del gráfico en móviles angostos y junto a él cuando el ancho lo permita. Nombre, moneda, saldo confirmado/último corte, estado y enlace a cuenta, sin ARS+USD nominal ni mostrar cero cuando falta fuente.

**Bloque 3, detalle temporal**: M0 actual detallado (ingresos, egresos, compromisos, saldo, evidencia), M+1..M+5 compactos. Tocar mes abre movimientos/fechas/cuotas/origen y etiqueta REAL/COMPROMETIDO/ESTIMADO/SIMULADO. Horizonte operativo seis meses no borra cuotas M+7/M+12/M+36 ni histórico. La gráfica es de 12 meses como perspectiva visual, no un mandato de limitar almacenamiento. Simulado no escribe Reality.

## B4 — Radar opción C sin raíz

**Inicio** tarjeta compacta 'Cómo viene todo', combinada con máximo cuatro señales materiales sin duplicar la lista, cobertura por owner; error/fuente faltante -> PARCIAL/NULL, nunca 'todo tranquilo' inventado. **Chat** diálogo exploratorio/explicación de señales con fecha, fuente, permisos, datos reales o previstos etiquetados; si IA remota falta, conservar lectura local y operación manual. **Asuntos** hallazgo contextual de relación válida con el asunto, por ejemplo vencimiento y gasto, sin mutar estados ni hacer pagos. Radar es read-model derivado, no nueva Reality, y la gráfica financiera detallada permanece en Finanzas.

## B4 — Ubicación de 10 superficies con trazabilidad P05

| ID | Superficie | Comportamiento de UX |
| --- | --- | --- |
| E01 | Chat | Inicio FARO y composer sin accesos de módulos duplicados |
| E02 | Inicio | Tarjeta Radar 'Cómo viene todo' con máximo cuatro señales materiales visibles en total y cobertura |
| E03 | Finanzas | Gráfico 12 meses con cero rojo bajo 0, resumen de cuentas y detalle temporal seis meses |
| E04 | Objetivos | Cartera, estado de progreso y pasos del mes; cifras solo si hay evidencia |
| E05 | Asuntos | Atención/fechas y hallazgos Radar de cada asunto, sin cambiar estado por lectura |
| E06 | SmartCompra | Compras y stock diferenciados, Cocina como subsección |
| E07 | Buscar | Ícono de búsqueda junto al título FARO, único acceso lógico |
| E08 | Herramientas | Acordeón Guardados/Actividad/Avisos y estado expandido accesible |
| E09 | Conversaciones | Fijados, siete recientes y Ver todos, sin duplicados ni selección falsa |
| E10 | Gráfico ampliado | Análisis financiero de mayor capacidad dentro de Finanzas, no raíz independiente |

Los 22 recorridos P05 F01–C02 se mantienen sin añadir contratos ni crear owners. Navegar durante respuesta IA/Apply no debe destruir draft, selección, fingerprint o receipt; no copiar guard global V4.4. Mostrar operación en curso y resolver estado UNKNOWN por receipt, jamás segundo Apply. La semántica transaccional, grant y cancelación quedan P07, no se dan por resueltas.

## B4 — Accesibilidad y errores

Targets táctiles >=48dp, orden foco TalkBack, soporte 200% fuente, split/rotación, IME accesible, colores con contraste y textos; **gráfico con tabla textual equivalente por mes y moneda**, que indica si el saldo es positivo/negativo, si es histórico o estimación, fecha asOf y cobertura. Distintas señales también por patrones de línea, no solo color. Estados SIN_DATOS/PARCIAL/NO_AUTORIZADO/STALE/SIN_CONEXION/UNKNOWN_RECOVERY no se sustituyen por cero o COMMITTED. Solicitudes de egress y entrega OS siempre específicas, nada automático por abrir una vista.

## B5 — Pruebas adversariales UX futuras, NO ejecutadas

| ID | Caso | Aceptación |
| --- | --- | --- |
| UX01 | Abrir con reciente vacío válido y archivado vacío viejo | Solo se reutiliza el reciente elegible, se conserva draft |
| UX02 | Drawer compacto y búsqueda cabecera | Sin barra inferior ni acceso duplicado |
| UX03 | Herramientas cerradas | Los tres destinos ocultos pero título/foco disponibles |
| UX04 | Herramientas abiertas | Guardados, Actividad y Avisos accesibles, estado anunciado a TalkBack |
| UX05 | Buscar desde cabecera | Único punto de acceso lógico y autorización al abrir resultados |
| UX06 | Fijados y más de siete recientes | Fijados fuera del cupo, siete recientes y expansión sin duplicados |
| UX07 | Sección y chat seleccionados | No dos focos de navegación activos simultáneamente |
| UX08 | Workspace no disponible | Indicación de falla, no selección de chat cacheado sin confirmar |
| UX09 | Radar Inicio fuente fallida | PARCIAL o NULL, no todo tranquilo |
| UX10 | Cinco señales en Inicio | Hasta cuatro priorizadas, tarjeta Radar sin duplicarlas |
| UX11 | Radar Chat sin grant de un owner | No revelación de montos/snippets y explicación de cobertura |
| UX12 | Radar Asuntos hallazgo revocado | No detalle protegido ni cambio automático de estado |
| UX13 | Gráfico compacto por meses | Seis meses históricos, M0 y cinco futuros exactamente |
| UX14 | M0 con saldo hoy y estimación fin mes | Corte actual distinguible de horizonte estimado |
| UX15 | ARS y USD presentes | Series por moneda separadas sin suma nominal |
| UX16 | Mes sin datos suficientes | Hueco visible sin interpolación engañosa ni cero artificial |
| UX17 | Expandir y regresar del gráfico | Selección/rango conservados o reinicio explicado, Reality sin cambios |
| UX18 | Tocar M+4 | Movimientos, cuotas, etiquetas de certeza y fuente legibles |
| UX19 | Obligación M+7/M+12/M+36 | Persistencia y acceso por rango, sin truncamiento |
| UX20 | Resumen en pantalla angosta | Debajo del gráfico, legible sin solapamiento |
| UX21 | 200% font/TalkBack y targets >=48dp | Tabla textual equivalente al gráfico y controles etiquetados |
| UX22 | Teclado IME en Chat | No tapa composer/enviar/review |
| UX23 | IA en curso y cambio de módulo | Draft y selección sin pérdida; no falso 'completado' |
| UX24 | Apply UNKNOWN, navegar y regresar | Receipt/reconcile antes de segundo Apply |
| UX25 | Rotación/split/ancho >=840dp | Drawer modal/permanente usable y footer accesible |
| UX26 | Subsección Cocina | No root adicional ni stock consumido por preview |
| UX27 | Resultado de búsqueda revocado | Invalidar snippet y revalidar generación/ACL |
| UX28 | Sin red ni Groq | Operaciones manuales locales no bloqueadas |
| UX29 | Serie cruza cero y vuelve a positivo | Referencia horizontal 0 siempre visible, segmento y área NEGATIVA rojos solo bajo cero, sin pintar tramos positivos |
| UX30 | Saldo proyectado por debajo de cero | Tramo negativo rojo con distinción de estimado (punteado/etiqueta); lectura accesible 'saldo previsto negativo' sin depender solo del color |

Las UX01–UX30 son pruebas negativas y visuales DEFINIDAS PARA FUTURO, NO EJECUTADAS. Nada de P06 certifica en G15 el gráfico, el rojo, TalkBack, ni un editor de dominio. Riesgos P07: suma por owner, saldo al corte vs saldo proyectado, forma de curva/cero, algoritmo de seis meses, compras/stock/cocina, Radar nullable/permisos, background recovery, delivery, idempotencia y privacidad.

## B6 — Publicación y B7 — gates remotos

Publicar solo este archivo, `docs/v5/FARO_S4_P06_B7_20261113.md`, y `docs/v5/FARO_CONTINUITY_POINTER.json` en rama de trabajo bajo writer global CAS epoch868. P06 cierre documental con decisiones humanas U01–U06, P07 READY_TO_START_READ_ONLY_NOT_EXECUTED. Sin cambio funcional, CI/Gradle/XML/APK/G15/Groq nuevos = NOT_RUN_DOC_ONLY, P00–P05 conservados, permiso BLOCKED, S3 pausada/S4 OFF/V2 productiva OFF. B7 posterior a liberar writer verifica HEAD, hashes, 11 decisiones U, 10 superficies E, 30 UX, 3 archivos de diff, main e histórico intactos; no declarar PASS si falla.


