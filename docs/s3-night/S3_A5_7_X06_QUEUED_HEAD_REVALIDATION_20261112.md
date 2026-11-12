# X06 queued HEAD revalidation — 2026-11-12

## B1
Reviewed current X02 code 5cdeae4cb1d6947f0e222487430b28a53bba1e98 and original preaudited X06 package. X02 changes only add AUXILIARY_NOTICE, Avisos Session/Activity plumbing and X02 test/workflow. Existing four-owner Search sources, federation, namespace mapping, grantAfterVerifiedOpen and pause Search revocation remain unchanged. Fixture FaroV5G5ActiveFourOwnerSearchTest and Compose Search helpers inspected without rerunning certified tests.

## B2
Reuse canonical RealityStore transaction seed after closing initialization Session; use real Activity native foreground Chat plus drawer Buscar. No fake hits or replacement production source. Historic Workspace tests remain certified and will not be requested.

## B3
Official Android activity testing and Compose synchronization read live 2026-11-12: https://developer.android.com/guide/components/activities/testing and https://developer.android.com/develop/ui/compose/testing/synchronization . Restored destinations require waiting for Search after recreate, not Chat. Reuse existing SearchBackgroundRevokeUiTest restoration handling. Choose canonical seeds and single-owner visible query per namespace over LazyColumn assumptions for offscreen three-result visibility. No dependency or cost.

## B4
Original two paths unchanged. Two new tests only, seed Finance/Objective/Matter via official owner transaction, close stores before Activity opens. Verify three namespaces from actual port and visible UI title/source/detail, no raw amount/IDs. Second test holds genuine old hit and port, pauses Activity, rejects old opener and clears UI, recreate + fresh native action -> new grant and visible results. Respect restored Search state.

## B5
Do not issue READY before X02 B7 PASS. Original preaudit blob 585a6276a6fffaeb8f7f7fbc3dd1153299749da2 retained. Revalidate exact HEAD again after X02 closure. If production route needed -> NEEDS_PREP delta first. No data migration, G15, Internet, paid API or global certification.

Status PREAUDITED_QUEUED_HEAD_DELTA_REVALIDATED_NOT_READY.
