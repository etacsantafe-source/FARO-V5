# FARO V5 · W05-A1C0B1 · Codec FBS2v9 DEV

## B7 — PASS acotado

- Código `afa612718bfd5910047f5bc2ee9de240e8daf688`. Sus cuatro rutas coinciden con el permiso READY del padre `22cd1e98f32a715e2f011a7d104c6959973ed025`. Preflight local y remoto aprobados. Guardia del SHA [45544056913](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45544056913) SUCCESS.
- [CI 45544057188](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45544057188) SUCCESS, job 138969266789. El [ZIP original 14991525372](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45544057188/artifacts/14991525372) se descargó y se verificó localmente. Sus dos XML contienen 10 casos únicos para FBS2v9 y 10 para FBS2v8, con cero failures, errors y skipped.
- SHA-256 ZIP `3057269bec41fb13bda72a203838303c91a340b75386de12e947b5e12fa53002`.
- SHA-256 XML v9 `b96ff46ad8b4ee4783372eb52102cd84b265937a14b64705db17e4f90a6cac56`.
- SHA-256 XML v8 `c21c73a51ce79e82eff239e24254672f5f2d7b122f5a25569c766a22f28b5d14`.
- Se probaron cantidad comprada de 10 y recibos 7+3, preservación de recibos heredados, v8 byte-exacto para Evidence1 y Evidence2, rechazo de referencias/cantidades/unidades inválidas, límites, corrupción, GCM y compatibilidad por lector. La apertura devuelve una proyección de procedencia sin efectos. El diff de crypto añade únicamente dos adaptadores a los mismos sealPayload/openPayload.
- Dos workflows históricos se dispararon por el adaptador compartido. W05-A1B2A 45544056915 y W04-B0C2 45544056998 terminaron en FAILURE en el preflight de sus propias unidades antes de ejecutar Gradle. La regresión v8 de este turno se ejecutó dentro de la CI dirigida. W04-F2B2B01 45544056914 quedó SKIPPED.
- Consume el permiso A1C0B1. El siguiente paso A1C0B2A es el puerto SQL12 transaccional, seguido del coordinador autenticado de los cinco almacenes A1C0B2B. W05-A1 DEV OPEN y Moto G15 físico NOT_RUN. V2 productiva, stock real, Apply productivo, pagos y Auto/Noche OFF.
