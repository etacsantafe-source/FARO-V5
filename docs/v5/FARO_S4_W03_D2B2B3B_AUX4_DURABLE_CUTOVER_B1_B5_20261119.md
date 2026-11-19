# FARO V5 — W03-D2B2B3B · Cutover Auxiliary4 DEV autorizado y recuperable

Unidad S4_W03_D2B2B3B_AUX4_DURABLE_CUTOVER_DEV. Base funcional 0f86b186e99f4effdb2bb733f57d3b034078fff7. ACTIVE4 exact open CI 44607263020. Se excluyen host/UI y V2 productiva.

## B1 — Evidencia
FBS2v5 y restore STAGED autentican y conservan ledger, Guardados, Reality9 Asuntos/Recordatorios y Workspace. El preflight ACTIVE4 rechaza schema3 y schema futuro sin escribir, usa un solo grafo bajo pin del puntero completo. FaroV5Finance9DevCutoverRuntime ya es el dueño de lock cross-process, ticket autómata y reopening; FaroV5Finance9DevCutoverOwner usa journal, fence, rollback y health. Ningún camino DEV actual puede seleccionar Auxiliary4 autoritativamente. Falta staging que copie Finance9+Aux3, autorice migración exclusiva de la copia, publique/recupere selección AUX4 y pueda volver al mismo origen Aux3.

## B2 — Reutilización
V4.4 FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939 conserva recordatorios si OS no autoriza. Mantener separación y dejar las proyecciones OS desactivadas. Reutilizar Runtime/StagePreparation/CutoverOwner/Preflight ya usados por W1, mantener ticket y rutas W1, no agregar lock, graph owner ni journal alternativo. Comparar stage pre/post a igualdad de snapshot Auxiliary3 y ledger vacío; los otros cuatro archivos mantienen sus bytes al migrar solo Auxiliary.

## B3 — Investigación primaria
USAR el lock existente y ticket durable Atomic Move / fsync; Oracle JDK 21 Files ATOMIC_MOVE https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html y FileChannel https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html. SQLite WAL https://www.sqlite.org/wal.html, SQLite dominio público https://www.sqlite.org/copyright.html. Adaptar existing staged graph y salud, DESCARTAR abrir / migrar SQLite ACTIVE o copiar un DB con sidecars. Sin dependencia criptográfica o platform nueva.

## B4 — Unificación
Mantener misma raíz/lease que Finance9; el ticket Auxiliary4 tiene archivo propio con el formato ya certificado. Ticket preliminar antes de staging y evidencia de autorización tras confirmación y fingerprint de origen+copia sellada, ambos bajo el mismo lease. Se requiere generación activa Finance9+Aux3 con grafo cerrado; guardar huella antes de copiar y comprobarla antes del switch. Migrar solo la copia, comprobar snapshot de Aux3 y que ledger nuevo esté vacío. Validar los cinco owners en STAGED, usar el journal y replay fence existentes para cambiar y verificar ACTIVE4. Recovery prioriza ticket Auxiliary4 y verifica puntero completo, preservando los status y uso W1. En rollback abrir ACTIVE Finance9+Aux3 en la secuencia nueva. El host real se conecta en la siguiente unidad.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevStagePreparation.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverPreflight.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverOwner.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverRuntime.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4DevCutoverRuntimeTest.kt
- .github/workflows/faro-v5-s4-w03-d2b2b3-auxiliary4-cutover.yml

## B5 — Auditoría previa
Negativas: confirmación inicial ausente, revocación antes de autorizar, destino existente ajeno, archivos cambiados tras staging, pointer ABA, sidecar, health de destino fallido y journal/receipt fence incoherente. Pruebas: corte tras publicar puntero y recovery sin segundo switch, rollback y gate de escritura antes del fence, preservar hashes de origen, Notice/Guardados/Reality intactos y ledger vacío. Los tests se ejecutan en root DEV temporal. No mount de UI, OS, V2 productiva ni G15. Próxima unidad enlaza Avisos grant+idempotencia/reproyección local y define closure D.
