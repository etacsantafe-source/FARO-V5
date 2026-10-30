# FARO V5 — Taxonomía de capacidades · historical freeze + bounded design
> **CURRENT AUTHORITY — D-229 (2026-10-20): CAPABILITY TAXONOMY REFROZEN / PASS.**
> Current exact identity set = **54/54**, all homed/decided, CapabilityDescriptor REOPEN = **NONE**. Historical 53/53 sections below are provenance.
> **POST-RG.06C.03 NOTICE — D-225 (2026-10-20): N-041 DESCRIPTOR REFINEMENT CLOSED.**
> N-041 is now canonically “Backup, restore, migración, compatibilidad y portabilidad de datos”. No identity moves/additions. CapabilityDescriptor bounded reopen set is now empty.
>
> **POST-RG.06C.02 NOTICE — D-224 (2026-10-20): N-032 DELIVERY/ATTENTION CONTRACT BOUNDARY CLOSED.**
> No capability identity is added or moved. A-003 Radar→delivery is now contractually closed through N-032 Notice/Delivery; N-041 remains the only CapabilityDescriptor bounded reopen.
>
> **POST-RG.06C.01 NOTICE — D-223 (2026-10-20): N-054 WATCHES/ROUTINES ADDED UNDER TX-05.**
> La taxonomía corriente tiene 54 identities. El freeze 53/53 de V5-0B.10 permanece como snapshot histórico; no hay refreeze global hasta RG.06C.07. N-041 sigue REOPENED BOUNDED para su refinamiento posterior.
>
> **POST-RG.06B NOTICE — D-217 (2026-10-20): N-041 DESCRIPTOR/BOUNDARY REOPENED BOUNDED.**
> No new capability identity is added: N-041 is refined to include human-readable data portability distinct from backup. N-018 saved views and N-032 delivery require contract reopens only. A-003 is now boundary-resolved by D-216, with exact contract design pending.
>

> **POST-FREEZE NOTICE — D-212 (2026-10-20): REOPENED BOUNDED FOR C-M03 ONLY.**
> El baseline 53/53 de este documento sigue siendo evidencia histórica pre-D-212, pero ya no es autoridad suficiente para volver a V5-0D. RG.05 demostró una responsabilidad nueva de Watches/Routines. Este bloque no asignó todavía N-ID ni modificó el conteo: eso requiere diseño controlado en RG.06 + ripple audit.


Actualizado: 2026-10-19
Origen candidato: V5-0B.09
Freeze: V5-0B.10
Estado: **FROZEN / AUTHORITY FOR V5 CAPABILITY TAXONOMY — D-229**

## 1. Propósito

Organizar las 54 identities neutrales reconciliadas en familias estables de responsabilidad para poder auditar y decidir V5 sin heredar:
- packages;
- pantallas;
- owners actuales;
- módulos legacy;
- S0–S14 como partición;
- arquitectura V3/V4 por inercia.

La taxonomía responde:
**¿qué responsabilidades/capacidades coherentes existen en FARO?**

No responde todavía:
- cuántos módulos tendrá V5;
- cuántos owners;
- qué corre local vs IA;
- qué se conserva/reemplaza/elimina;
- cómo se implementa cada familia.

## 2. Principios

1. **WHAT over HOW**
   - agrupar por responsabilidad/capability, no por tecnología.

2. **Un hogar primario**
   - cada N-xxx tiene exactamente una familia primaria para auditoría.

3. **Relaciones secundarias permitidas**
   - leer otra capability, depender de ella o presentarla no cambia el hogar primario.

4. **Alta cohesión**
   - una familia debe poder explicarse con una responsabilidad reconocible.

5. **Bajo acoplamiento conceptual**
   - se evita fusionar familias sólo porque comparten datos o UI.

6. **Historia preservada**
   - una capability retired/partial sigue mapeada.

7. **Taxonomía != arquitectura**
   - una familia NO obliga a crear un módulo, owner, servicio, package o pantalla.

## 3. Las 10 familias candidatas

### TX-01 — Realidad e integridad operacional

Pregunta:
**¿Cómo pasa una intención a un cambio real seguro, trazable y reversible?**

Incluye:
N-001, N-003, N-005, N-006, N-007, N-008, N-016

Responsabilidades:
- autoridad/Truth y ownership;
- persistencia durable ligada a autoridad;
- operaciones/tools tipados como frontera operacional;
- propuesta/revisión/Apply;
- identidad transaccional, idempotencia, stale/recovery;
- auditabilidad determinística;
- Undo/reversal/corrección segura.

No incluye:
- comprensión conversacional;
- dominio financiero/objetivos/SmartCompra;
- backup/update del producto.

### TX-02 — Conversación, contexto e inteligencia

Pregunta:
**¿Cómo entiende FARO lo que la persona quiere, obtiene contexto, razona y mantiene continuidad cognitiva?**

Incluye:
N-004, N-009, N-010, N-011, N-012, N-013, N-014, N-021, N-048

Responsabilidades:
- intención semántica/canónica;
- contexto conversacional;
- workspace/lifecycle de chats;
- memoria/aprendizaje validado;
- IA/model routing/telemetría;
- conocimiento/corpus/evaluación offline;
- Summary como representación/revisión cognitiva;
- simulación hipotética;
- web externo bajo demanda.

No incluye:
- commit de Reality;
- documentos/archivos como lifecycle durable;
- Radar como observador/proactividad transversal.

### TX-03 — Evidencia, documentos e intercambio de estado

Pregunta:
**¿Cómo ingresa, se estructura, se conserva o se transporta evidencia/artefactos sin confundirlos con Reality?**

Incluye:
N-019, N-020, N-049, N-050, N-051

Responsabilidades:
- ingesta/extracción documental;
- comprobantes/archivos owned;
- State Packet / transporte compacto histórico;
- Documents durable/lifecycle;
- adjuntos efímeros como canal de entrada.

Estado importante:
- contiene pérdidas y piezas desconectadas;
- la familia existe como responsabilidad aunque parte no esté materializada en V4.

### TX-04 — Finanzas y planificación financiera

Pregunta:
**¿Cómo representa FARO dinero, causalidad financiera, capacidad y horizonte?**

Incluye:
N-025, N-026, N-027, N-029

Responsabilidades:
- finanzas causal/operativa;
- Money/moneda/FX;
- recurrentes, horizonte y forecast;
- presupuestos, margen/ahorro seguro y conciliación.

Relación:
- el Plan del mes vive primariamente en TX-05 porque coordina compromisos/objetivos, aunque consuma Truth financiera.

### TX-05 — Objetivos, compromisos y seguimiento

Pregunta:
**¿Cómo representa FARO lo que la persona quiere lograr, debe atender, debe recordar o pidió volver a comprobar?**

Incluye:
N-028, N-030, N-031, N-032, N-054

Responsabilidades:
- Plan del mes / compromisos coordinados;
- Objetivos;
- Asuntos/follow-up;
- recordatorios, temporalidad y notificaciones asociadas;
- vigilancias/rutinas de reevaluación definidas por la persona.

No incluye:
- Radar como hallazgo transversal;
- finanzas causales;
- configuración técnica de notificaciones.

### TX-06 — SmartCompra y operación doméstica

Pregunta:
**¿Cómo ayuda FARO a decidir, planificar y ejecutar compras domésticas conectadas con stock, precios y cocina?**

Incluye:
N-033, N-034, N-035, N-036, N-037

Responsabilidades:
- necesidad/decisión doméstica;
- evidencia de precios y catálogo comercial;
- stock/inventario;
- plan de compra, fulfillment/recepción;
- cocina, recetas, planificación y preparados.

Límite:
- alcance doméstico;
- no absorbe viajes, repuestos u otras compras excepcionales de FARO general.

### TX-07 — Radar, insight y proactividad

Pregunta:
**¿Cómo detecta FARO algo material, lo explica y decide si merece atención sin convertirse en dueño de Reality?**

Incluye:
N-023, N-024, N-052

Responsabilidades:
- advice/recomendación puntual histórica;
- Radar, salud, hallazgos y analítica transversal;
- coaching/detección proactiva de patrones histórica.

Regla:
- Advice, Coaching y Radar siguen siendo identities distintas dentro de una misma familia;
- compartir familia NO declara equivalencia funcional.

### TX-08 — Historial, búsqueda y relaciones transversales

Pregunta:
**¿Cómo navega FARO lo que ocurrió y cómo conecta entidades entre dominios?**

Incluye:
N-015, N-017, N-018

Responsabilidades:
- historial/operation receipts/trazabilidad;
- relaciones tipadas cross-domain;
- búsqueda global/transversal.

No incluye:
- Undo, que usa historial pero pertenece a integridad operacional;
- dependencias internas de un owner.

### TX-09 — Experiencia, personalización e interacción

Pregunta:
**¿Cómo entra la persona a FARO, navega, configura su experiencia y usa modalidades de interacción?**

Incluye:
N-022, N-038, N-039, N-043, N-044, N-047, N-053

Responsabilidades:
- Inicio/materialidad;
- Perfil/identidad/preferencias personales;
- Ajustes como superficie de control;
- FARO Quick como superficie histórica;
- shell/navegación/superficies;
- onboarding;
- voz/dictado/TTS.

Regla:
- Perfil != Ajustes != Onboarding;
- Quick != voz.

### TX-10 — Seguridad, continuidad y operación del sistema

Pregunta:
**¿Cómo se mantiene FARO seguro, recuperable, portable, actualizable y operativamente sano?**

Incluye:
N-002, N-040, N-041, N-042, N-045, N-046

Responsabilidades:
- composición/runtime/health técnico;
- seguridad y privacidad;
- backup/restore/migración/compatibilidad/portabilidad de datos;
- update/distribución;
- diagnóstico/self-test/performance;
- reset/limpieza controlada.

No incluye:
- Truth de dominios;
- navegación/UX cotidiana;
- integridad transaccional de Apply.

## 4. Distribución y coverage

| Familia | Cantidad |
|---|---:|
| TX-01 | 7 |
| TX-02 | 9 |
| TX-03 | 5 |
| TX-04 | 4 |
| TX-05 | 5 |
| TX-06 | 5 |
| TX-07 | 3 |
| TX-08 | 3 |
| TX-09 | 7 |
| TX-10 | 6 |
| **TOTAL** | **54** |

Gate:
- 54/54 identities tienen hogar primario;
- duplicados primarios: 0;
- huérfanos: 0.

## 5. Relaciones materiales entre familias

Estas relaciones NO son arquitectura; sólo explican dependencia semántica.

### TX-02 → TX-01
Conversación/inteligencia puede producir intención/propuesta.
TX-01 gobierna la mutación real.

### TX-03 → TX-02 / TX-04 / TX-05 / TX-06
Evidencia/adjuntos pueden alimentar comprensión o dominios.
No autorizan writes por sí mismos.

### TX-04 ↔ TX-05
Finanzas aporta capacidad/Reality monetaria.
Objetivos/compromisos aportan propósito y coordinación.

### TX-04 ↔ TX-06
SmartCompra usa condiciones financieras sin apropiarse de Finance Truth.

### TX-07 → TX-04 / TX-05 / TX-06 / TX-08
Radar/proactividad observa y explica.
No se vuelve owner de los dominios observados.

### TX-05 ↔ TX-07 — Watches vs Radar
N-054 posee la regla durable definida por la persona y su evaluación operacional.
N-024 conserva ownership de findings/proactividad definida por FARO. Una watch puede producir una señal/candidato, pero no una segunda Radar Truth.

### TX-08 ↔ múltiples familias
Historial/búsqueda/relaciones cruzan dominios sin convertirse en Truth global.

### TX-09 → todas
Experiencia presenta y captura interacción.
No debe transformarse en segundo cerebro/Truth.

### TX-10 → todas
Protege continuidad, seguridad, diagnóstico y lifecycle técnico.
No absorbe semántica de producto.

## 6. Gate contra S0–S14

S0–S14 siguen siendo red de seguridad, no familias.

| Hipótesis | Cobertura candidata | Gate |
|---|---|---|
| S0 Linaje | provenance de N-xxx + ledger/canteras | PASS |
| S1 Autoridad/owners/contracts/composition | TX-01 + TX-10 | PASS |
| S2 Conversación/IA/routing/contexto/memoria/modelos | TX-02 + TX-09 | PASS |
| S3 intención/Apply/retry/idempotencia/conflictos/Undo | TX-01 + relación TX-02/TX-08 | PASS |
| S4 Finanzas | TX-04 | PASS |
| S5 Objetivos | TX-05 | PASS |
| S6 Asuntos/follow-up/reminders/notifications/watches | TX-05 | PASS |
| S7 SmartCompra doméstica | TX-06 | PASS |
| S8 Radar/Home/atención/materialidad/proactividad | TX-07 + TX-09 | PASS |
| S9 búsqueda/historial/relaciones/evidencia/adjuntos/conocimiento | TX-02 + TX-03 + TX-08 | PASS |
| S10 shell/UX/settings/onboarding/entrada/voz | TX-09 + TX-10 | PASS |
| S11 seguridad/privacidad | TX-10 | PASS |
| S12 persistencia/backup/restore/migración/compat/update | TX-01 + TX-10 | PASS |
| S13 offline/local-first/resiliencia/diagnostics/release/físico | TX-02 + TX-10 | PASS |
| S14 dead/obsolete/duplicate/disconnected/test-only | D-032 + anomalías A-001..A-008, transversal | PASS |

Resultado:
**15/15 hipótesis cubiertas sin reutilizarlas como taxonomía.**

## 7. Gate contra V5_AUDIT_SCOPE

Cobertura:
- V1–V4: provenance transversal;
- motor conversacional / IA / routing / memoria / contexto / Summary: TX-02;
- IA/FARO + intención/Apply/retry/recovery/Undo: TX-02 ↔ TX-01;
- owners/Reality: TX-01 + familias de dominio;
- seguridad: TX-10;
- persistencia: TX-01/TX-10;
- backups/migración/update: TX-10;
- relaciones/búsqueda: TX-08;
- Radar: TX-07;
- Finanzas: TX-04;
- Objetivos/Asuntos: TX-05;
- SmartCompra: TX-06;
- capacidades ocultas/dead/obsolete/disconnected/duplicates/contracts/tests: D-032 transversal.

Resultado:
**PASS — ninguna área durable del scope queda sin hogar o lente de auditoría.**

## 8. Gate contra anomalías A-001..A-008

| Anomalía | Destino |
|---|---|
| A-001 core:evidence desconectado | TX-03 / N-019 |
| A-002 core:knowledge desconectado | TX-02 / N-013 |
| A-003 Radar notification contract contract-only | **RESOLVED D-224**: N-024 finding → N-032 `notice.route` → Notice → `notification.delivery.dispatch/reconcile` |
| A-004 Finance private Explore obsolete-candidate | TX-07/N-024 como capability destino; residuo físico en TX-04 |
| A-005 charts Finance usados por Radar | TX-07 primary capability; TX-04 implementation support |
| A-006 finance routers conectados | relación TX-02 → TX-01 → TX-04 |
| A-007 CompatibilityMigrator | TX-10 / N-041 |
| A-008 OwnerAccessGate + diagnostics | TX-10 / N-002,N-045 |

Resultado:
**8/8 trazadas; ninguna exige familia nueva.**

## 9. Por qué no se fusionaron más familias

### TX-02 + TX-03 — RECHAZADO
Conversar/recordar/razonar no es lo mismo que administrar evidencia/documentos/artefactos.
Fusionarlas ocultaría la pérdida de Documents/Comprobantes y la desconexión de core:evidence.

### TX-07 + TX-08 — RECHAZADO
Radar/proactividad decide qué merece atención.
Historial/búsqueda/relaciones permite navegar/trazar.
Una observa/interpreta; la otra registra/conecta/encuentra.

### TX-01 + TX-10 — RECHAZADO
Integridad operacional gobierna cambios de Reality.
Seguridad/continuidad gobierna salud/lifecycle técnico del producto.
Fusionarlas produciría una “plataforma” demasiado amplia.

### TX-02 + TX-09 — RECHAZADO
Chat/inteligencia es responsabilidad cognitiva.
Shell/Quick/voz/onboarding son canales/superficies.
La UI no debe convertirse en segundo cerebro.

### TX-04 + TX-05 — RECHAZADO
Finanzas responde por causalidad monetaria.
Objetivos/compromisos responden por propósito, planificación y seguimiento.
N-028 demuestra relación fuerte pero no identidad.

## 10. D-024 — convergencia

### Ronda 1
Hipótesis inicial agrupaba Conversation + Evidence.
Delta:
- escondería Documents/Comprobantes y core:evidence desconectado.
Acción:
- separar TX-02 y TX-03.

### Ronda 2
Hipótesis inicial agrupaba Radar + History/Search.
Delta:
- observación/proactividad != trazabilidad/navegación.
Acción:
- separar TX-07 y TX-08.

### Ronda 3
Hipótesis inicial agrupaba Operational Integrity + System Continuity.
Delta:
- Apply/Truth y backup/update/security tienen fallos y owners conceptuales distintos.
Acción:
- separar TX-01 y TX-10.

### Ronda 4
Chat/IA y superficies de interacción.
Delta:
- Quick/voz/onboarding/shell no deben gobernar inteligencia.
Acción:
- separar TX-02 y TX-09.

### Ronda 5
Finance vs Objectives/Commitments.
Delta:
- Plan del mes es cross-domain, pero su responsabilidad primaria es coordinación de compromisos, no cálculo monetario.
Acción:
- N-028 → TX-05 con relación a TX-04.

### Ronda 6
SmartCompra.
Revisión:
- mantiene modelo causal doméstico propio y relaciones fuertes con Finance/Radar;
- fusionarlo con “compras generales” violaría alcance histórico.
Acción:
- TX-06 permanece independiente.

### Ronda 7
Coverage mecánico:
- 53 asignaciones;
- 53 únicas;
- 0 faltantes;
- 0 duplicados primarios.

### Ronda 8
S0–S14:
- 15/15 PASS;
- S0 y S14 permanecen lentes transversales, no familias.

### Ronda 9
Scope + anomalías:
- todo V5_AUDIT_SCOPE cubierto;
- A-001..A-008 trazadas;
- no aparece familia adicional.

### Ronda 10 — preguntas generadoras
- ¿Qué mejorarías/agregarías/quitarías? No reducir a costa de mezclar responsabilidades; no ampliar sin capability huérfana.
- ¿Falta algo importante? 53/53 + S0–S14 + scope + A-001..A-008 cubiertos.
- ¿Existe solución más simple/madura? 10 familias con IDs primarios y relaciones secundarias es más simple que 53 sectores y más fiel que reutilizar S0–S14.
- ¿Se conserva algo porque ya existe? No; packages actuales no gobiernan.
- ¿Hay dos sistemas resolviendo lo mismo? Los solapes conocidos siguen como relations/gaps dentro de familias, no se borran.
- ¿IA o FARO? La taxonomía no decide implementación; TX-02/TX-01 sólo separan comprensión de mutación.
- ¿Qué quedó oculto/desconectado? A-001..A-004 permanecen explícitos.
- ¿Hay baches cross-capability? Relaciones principales están documentadas.
- ¿Se perdió una protección histórica? Identities retired/partial permanecen en TX-02/TX-03/TX-07/TX-09.

Convergencia alcanzada para **taxonomía CANDIDATA**.

## 11. D-025 — atributos de calidad y trade-offs

Atributos:
- estabilidad frente a cambios de implementación;
- cohesión conceptual;
- bajo acoplamiento;
- trazabilidad;
- explicabilidad humana;
- cero huérfanos;
- libertad arquitectónica futura.

Trade-off:
- TX-07 y TX-08 tienen sólo 3 identities cada una;
- se acepta porque fusionarlas mezclaría proactividad con trazabilidad.

Trade-off:
- TX-01 y TX-10 son transversales;
- se acepta porque representan responsabilidades reales distintas, no capas técnicas arbitrarias.

## 12. D-026 — riesgos y controles

Riesgo:
tratar cada TX como módulo/owner futuro.
Control:
D-033 — taxonomía no prescribe arquitectura.

Riesgo:
familias transversales se conviertan en “god services”.
Control:
decisiones de boundaries quedan para arquitectura, después de auditoría capability-by-capability.

Riesgo:
capabilities retired desaparezcan en freeze.
Control:
53/53 deben permanecer trazables aunque después se decidan REMOVE/REPLACE.

Riesgo:
S0/S14 desaparezcan porque no son familias.
Control:
permanecen lentes obligatorias de linaje y huérfanos.

## 13. D-027 — trazabilidad / cero huérfanos

- 54/54 identities → exactamente un TX primario;
- S0–S14 → 15/15 cubiertas;
- V5_AUDIT_SCOPE → cobertura completa;
- A-001..A-008 → 8/8 con destino;
- relaciones cross-family documentadas;
- no hay capability nueva sin hogar;
- no hay familia sin identities;
- no hay decisión KEEP/REMOVE implícita.

## 14. Estado de freeze

**FROZEN / AUTHORITY FOR V5 CAPABILITY AUDIT.**

Gate V5-0B.10:
- checkpoint humano explícito recibido;
- checkpoint sin delta material;
- 10 familias confirmadas;
- 53/53 identities asignadas exactamente una vez;
- 0 huérfanos;
- 0 duplicados primarios;
- S0–S14: 15/15 PASS;
- V5_AUDIT_SCOPE: PASS;
- A-001..A-008: 8/8 trazadas;
- D-024 final sin delta material.

Post-D-223 — estado corriente:
- el freeze V5-0B.10 de 53/53 es histórico;
- D-212 reabrió taxonomía y D-223 incorporó N-054;
- estado corriente: **54/54 identities con hogar primario, 0 huérfanos primarios**;
- TX-05 pasa de 4 a 5 identities;
- N-041 descriptor refinement **CLOSED / PASS — D-225**;
- CapabilityDescriptor bounded reopen remaining: **NONE**;
- refreeze global **CLOSED / PASS — D-229**.

## 15. Gobernanza después del freeze

Esta taxonomía gobierna la **partición de auditoría y decisión de capabilities V5**.

No gobierna arquitectura, módulos, owners, servicios, packages, pantallas, IA vs local ni KEEP/ADAPT/REMOVE.

Reglas:
- toda auditoría detallada debe trazar su objeto a un N-xxx y TX-xx;
- S0 (linaje) y S14 (huérfanos) continúan como lentes transversales obligatorias;
- identities retired/partial/disconnected permanecen dentro de la autoridad congelada;
- cambiar TX, mover N-xxx, agregar/eliminar/fusionar/dividir una identity o alterar límites materiales requiere REOPEN explícito bajo D-034;
- cambios editoriales sin cambio semántico no reabren la clasificación;
- arquitectura futura debe trazarse hacia esta taxonomía y las decisiones capability-by-capability.

Fuente candidata congelada:
- `docs/v5/V5_CAPABILITY_TAXONOMY_CANDIDATE.md`;
- commit candidato: `9c93248adb3c768b01cffd1693874437e855805d`.

Autoridad vigente:
`docs/v5/V5_CAPABILITY_TAXONOMY.md`.
