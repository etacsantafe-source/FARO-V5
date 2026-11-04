# FARO V5 — S3.A2-G1 active Chat read pointer ABA · B6 WAIT_CI / B7 PENDING (04/11/2026)

**Unidad:** `S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA`. **Código funcional:** `212679f7c2dc211445dc7f806479b83aea8780e0`; **B1–B5/READY en padre:** `e1cc4b979e39bef1e2cd78384c549829adab4164`; preflight observado `#41936541907` **SUCCESS** y `FARO_PROTOCOL_PREFLIGHT_PASS S3_A2_G1_ACTIVE_CHAT_READ_POINTER_ABA e1cc4b9...`.

## B6 alcance y comprobaciones
- Diff funcional: `FaroV5ActiveChatReadSession.kt`, `FaroV5CompositionRoot.java`, `FaroV5ActiveChatReadSessionT2Test.kt`, `.github/workflows/faro-v5-s3-home-notice-directed.yml`. Cuatro rutas, sin cambios de tabla, V4.4, main, manifest ni flags.
- El owner del read session de Chat valida puntero durable completo (ID+sequence) antes/después, además de recuperación. El input V2 shipping sigue OFF, V1 no abre quinto owner.
- Pruebas nuevas del puntero real: A→B→A sin listener y A→B→A a mitad de lectura, además de cuatro pruebas T2 históricas; XML esperado `S3_CHAT_POINTER_ABA_XML_PASS 6/6`.
- En validación en Actions exact SHA (NO declarar PASS mientras running):
  - guard `#41936856589` **SUCCESS** (historia de permiso), no prueba funcional;
  - S3 Home+Chat `#41936856517`: **RUNNING**, debe mostrar Home 9/9, Workspace 7/7, Search bridge 5/5, Avisos 4/4, Chat ABA 6/6;
  - V2 core `#41936856422`: **RUNNING**; asegurar DEV 10/10 y aislamiento existente;
  - APK DEV `#41936856486`: **RUNNING**; firmar, paquete exclusivo y sin permisos. No instalar.
- Auditoría final B7: revisar SHA, diff, todas conclusiones y XML reales. Si falla JVM/Java/Kotlin, RED_FIX mismo scope y nuevo B1–B5/READY; nada de PASS inventado.
- Si verde, B7 PASS **solo del endurecimiento de lectura de Chat activo**. **No acredita grant/revoke ACL real ni eventos cross-process ni Buscar shipping, por tanto G1 sigue 0/5**.
- S2 100%=8/8; S3 desarrollo aproximado ~40% y cierre formal 0/5; Auto/Noche/V2 productiva/S4 OFF. Writer debe liberarse sin mantener exclusividad durante CI. Tiempo de desarrollo NO MEDIDO.
