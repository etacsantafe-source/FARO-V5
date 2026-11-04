# FARO V5 — S3.A2 Adapter real + federación canónica · checkpoint 04/11/2026

## Cierres técnicos verificados

- Rama: `work/s3-night-20261102`; baseline `e1c1a3f6866cd3c8ba0c504ce1014124c1df4a29`. HEAD de código `8a5febc76e509b43d5dd46fa2f1b3eea0b5389e4`.
- **Workspace -> superficie**: código final `ef4eb2a8d8a832e6fdcd550eba82d2ea9573d188`, run **41823167085** SUCCESS, XML exacto **15/15**: Home Notice 9, Workspace source 3, Workspace surface adapter 3. La selección usa identificadores opacos de Workspace real, relectura canónica y gate existente de selección; cambios de ACL/generación fallan cerrados. Los eventos de ACL deben venir del propietario real, NO fueron simulados en producción.
- **Federación**: código final `8a5febc76e509b43d5dd46fa2f1b3eea0b5389e4`, run **41823780133** SUCCESS, XML exacto **15/15**: Canonical 7, Projection 2, Federated 6. Hasta 8 fuentes explícitas, resultado por fuente COMPLETE/PARTIAL/UNAVAILABLE, orden round-robin para no hacer desaparecer un dominio menos frecuente, sin datos nacidos del índice. Una revocación de una fuente anterior durante la consulta de otra elimina sus resultados antes de entregar la página.
- Certificaciones anteriores reutilizadas, sin repetir: búsqueda canónica en español **9/9** `a65b56fa` y UI de Buscar **48/48** `6b2ddcf4`; ambas de alcance dirigido, sin Android físico.
- Writer único liberado en epoch 103 tras código, y epoch **105** esperado al cerrar este checkpoint. Noche y Auto OFF; queue CLOUD STAGED, activación false; S2 8/8 CLOSED; S3 OPEN; S4/V2 OFF; Guardados +8 no aprobado.

## Causas reales de fallos intermedios

- Primer CI del adaptador falló una prueba por suposición incorrecta de **dos** chats: la fuente preserva la paridad V4.4 y reutiliza el chat activo vacío. Corregido el test sin cambiar el propietario.
- Se corrigió también la igualdad de claves de `SearchProjectionKey` por valores (no identidad de objeto), el alcance de `return` de Kotlin y el pre/post stamp completo en búsqueda.
- Primer CI de federación falló ANTES de compilar por Maven HTTP 429; una única repetición del trabajo fallido pasó 14/14. Se identificó después hueco real de revocación inter-propietarios, se corrigió con prueba adicional y CI final exacto 15/15.

## Hueco principal, sin atajos

S3.A2 NO está completo. `FaroV5WorkspaceSearchReadSource` requiere **epoch ACL genuino** y generación activa; en el host shipping no está implementado/demostrado un emisor independiente de revocación ACL, por lo que `searchReadPort` queda nulo. El adaptador y federador son utilidades preparadas pero no navegación real habilitada. No crear un ACL fijo o inventar permisos para activar Buscar.

Próximas unidades: autoridad y eventos ACL propietarios, recuperación/generación; pruebas de revocación entre instancias, revalidación en navegación; incorporación de fuentes canónicas de Finanzas/Otros con cobertura individual; Compose e instrumentación. N031 de planificación requiere persistencia/backup FBS-compatible, N015 historia necesita tiempo COMMITTED real. Sin contador Home ficticio.

## Restricciones y métricas

S3 manual con writer CAS estricto y permisos vigentes, no main, no V4.4, no API paga, ni nuevas operaciones no aprobadas; S4/V2/Auto/Noche OFF. Prueba física única al final de S3: NOT_RUN. Trabajo efectivo: NO_MEDIDO. Mantener termómetro cualitativo del chat al final de respuestas de FARO.
