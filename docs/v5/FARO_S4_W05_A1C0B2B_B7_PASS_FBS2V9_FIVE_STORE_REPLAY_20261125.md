# FARO V5 · W05-A1C0B2B · Replay y captura FBS2v9 con cinco almacenes SQL12

## B7 — PASS acotado

- Código `7af8bda34e9aa05b3dad0964e41e58d9e62f8d97`. Preflight exact11, guardia [45548015251](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45548015251) SUCCESS. El commit inicial `e8fecbfd31d3a6563d02a0280cf708a3b9e4879a` compiló el código principal y tuvo ARGUMENT_TYPE_MISMATCH en una prueba. La corrección mismo scope cambia sólo las dos llamadas CAS de esa prueba. El guard de código inicial 45547259594 pasó y el RED_FIX se registró en `c6ee770d553e3479e197bd6af8efdd5fc0be6395`.
- [CI 45548015333](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45548015333) SUCCESS y [ZIP original 14992591395](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45548015333/artifacts/14992591395) verificado. Cuatro XML originales con 8 pruebas nuevas, 8 de base v8, 10 de replay v8 y 6 de Evidence2 v8. Total 32/32, cero failures, errors o skipped.
- ZIP SHA-256 `b18c507487e2b626337de7ab91da83ed225a49e271a173c4ee07498b292a3ba5`.
- XML `FaroV5Household11V8FullStageReplayTest` SHA-256 `c9823fdd61947a31f0c39692bee1f63ae5d415e89de7f73fb87e8c86510c259e`.
- XML `FaroV5Household11V8BaseReplayTest` SHA-256 `096ba32deb5c82dcea17308e5c39cddeb99634072eae3cf9f9a07a22b108422f`.
- XML `FaroV5Household12V9StagedReplayTest` SHA-256 `c3a261f0b790a6ce1a6d942f356e157a0d03de34559d17595380138d67182b70`.
- XML `FaroV5Household11V8Evidence2FullStageReplayTest` SHA-256 `cc8e8425711702845b3cd4bc00e86960756e02647cd2c0a674060c0222b10757`.
- Captura y replay SQL12 STAGED de los cinco almacenes desde el mismo ciphertext FBS2v9 autenticado. Se cubren Evidence1 y Evidence2, compra 10, recepciones 7+3, rollback y reintento con el mismo archivo, divergencia semántica, corrupción, ABA, revocación de lease y ruta explícita de v8 que conserva recibos y arranca sin compras. La prueba recaptura y compara datos de los cinco owners, finanzas y necesidades. La entrada nueva de grafo es STAGED de lectura exacta SQL12.
- A1C0B2 queda B7 PASS DEV por A1C0B2A y A1C0B2B. El permiso A1C0B2B se consume. A1C0B3A seguirá con el grafo ACTIVE DEV read-only y A1C0B3B con el ticket, journal y cutover. W05-A1 DEV OPEN. Moto G15 físico NOT_RUN. V2 productiva, stock real, Apply productivo, pagos y Auto/Noche OFF.
