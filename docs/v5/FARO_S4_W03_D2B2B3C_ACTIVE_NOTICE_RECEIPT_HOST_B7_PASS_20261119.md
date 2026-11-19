# FARO V5 — W03-D2B2B3C · B7 host real de Avisos con recibos

Unidad S4_W03_D2B2B3C_ACTIVE_NOTICE_RECEIPT_HOST_DEV, código 7ed59b8d770b279fbcecd7c007910e9f37d7bef0. Scope DEV.

## B1–B5
docs/v5/FARO_S4_W03_D2B2B3C_ACTIVE_NOTICE_RECEIPT_HOST_B1_B5_20261119.md, guardia 44610390077 SUCCESS. READY exacto 2aa9c0515f76c81e36a3236b7594a32cde840fae, guardia 44610638400 SUCCESS, preflight seis rutas.

## B6
Un mismo SQLite owner de Auxiliary4 recompone la proyección SUPPRESSED_POLICY de Avisos de Reminder restaurados con identidad exacta y sin repetir Notice ni cambiar su revisión al recomponer. En Notice resuelto, reconstruye CANCEL suprimido. Click nativo verifica permiso de lectura, objeto/operación exactos, lifecycle/epoch y puntero antes y durante SQL; request key y fingerprint preservan el recibo original. Session DEV conecta upgrade explícito y reutiliza el grafo, sin conectar OS ni shipping.

Run 44611463388, job 135868871550 SUCCESS, guardia de código 44611463263 SUCCESS. Compilación v2CoreDev en CI. Artefacto 14573506616, ZIP sha256:72927b834536ca5e301e0f9bd231ae844d5eea9aa77f6862d57f1e8f0533645d, verificado 4/4 SQLite y 4/4 JVM host, sin fallas, errores u omisiones. Cobertura: rebuild sin entrega, terminal/foreign, replay, stale/conflicto, READ/WRITE/foreground independientes y ABA.

## B7
PASS acotado integración de backup/restore/ACTIVE4/cutover/host de Avisos. D2B2B cubierto en cadena. Consumir READY y revalidar guardia/writer de este checkpoint. W03-D requiere integración de sesión para los owners Matter/Reminder y conciliación conjunta. Próxima preauditoría: docs/v5/FARO_S4_W03_D_OWNER_INTEGRATION_B1_B5_20261119.md. E Android y G global después. F opcional diferida, V2 productiva OFF y G15 NOT_RUN.
