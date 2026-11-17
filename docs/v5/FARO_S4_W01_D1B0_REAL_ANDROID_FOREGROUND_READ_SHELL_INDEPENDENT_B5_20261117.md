# FARO V5 — W01 D1B0 B5 adversarial por separado
2026-11-17 · `S4_W01_D1B0_REAL_ANDROID_FOREGROUND_READ_SHELL_DEV` · DOC_ONLY NO READY.

## B5
Auditoría adversarial del delta EXACT4 y blobs ya auditados en D1A.
- **CRÍTICO: evitar falsa autorización**. Ninguna Activity obtiene permiso de lectura de `Lifecycle.State.RESUMED`, ni de `ContentObserver`. El botón sólo navega; la Activity sólo crea reconciler con `newObservedFence` que deniega por ausencia de issuer, política SQL y prueba biométrica. El DTO no puede convertirse en saldo visible.
- **CRÍTICO: no romper el Chat DEV**. Heredar el launcher existente en source-set W01 sin tocar `FaroV5V2CoreDevActivity.kt` ni su super `ComponentActivity`. El reader nuevo hereda directamente `FragmentActivity`, preparado para D1B1. Manifest del paquete W01 usa subclass; v2CoreDev y S3 retienen sus launchers.
- **CRÍTICO: lifecycle**. Crear y cerrar reconciler/adapter/ContentObserver durante vida de Activity real; ante STOP/PAUSE se borra el estado y se impide leer; después DESTROY no revive. `ContentObserver` no acredita commit SQL; `ON_RESUME` no emite policy.
- **Aislamiento**. Package `ar.com.faro.alpha.w01financedev` + DEBUGGABLE + shipping V2 OFF, Activity no exportada, cero nuevas permissions, sin SQL/Keystore/grants, sin servicios de fondo.
- **Prueba**. Android API35 usando `ActivityScenario`, confirmar pantalla cerrada sin saldo, transición CREATED→RESUMED, cierre; test adicional resolución del launcher W01 y componente no exportado por PackageManager. No repetir D1A 2/2. En ausencia de ejecución real ⇒ NO B7.
- **Futuros límites**. D1B0 no conecta SQL ni BiometricPrompt ni certifica G15, balances o credenciales; D1B1 requiere autoridad durable y consentimiento humano, ninguna pantalla muestra importes hasta entonces.
**Dictamen:** GO condicional sólo para permiso exact4 después de guard documental SUCCESS, manteniendo denegada toda lectura real. No se altera la etapa W2.