# Watch-Party-Port (Desktop → Mobile) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das komplette Watch-Party-Feature (v1 + Lobby 2.0/Auto-Follow + alle Fixes bis `baad4577`) aus dem Desktop-Fork nach NuvioMobile portieren, plus drei Mobile-Neubauten: Profilwechsel-Auto-Leave, Away-Modus, PiP-Prompt-Deferral.

**Architecture:** Port-Strategie „Option B in zwei Schichten": Die in sich geschlossenen WP-Dateien kommen als `git diff | git apply` aus dem Desktop-Repo (verifiziert konfliktfrei, Quelle gepinnt); die ~390 WP-Zeilen in geteilten Dateien werden handverlesen portiert (die Roh-Deltas enthalten Fork-Rauschen, das auf Mobile nicht kompiliert). Die Sync-Architektur bleibt unverändert: pure `WatchPartySyncEngine` ← `WatchPartySession` (Single-Thread-Dispatcher) ← `SupabaseWatchPartyClient`; app-weiter Besitzer ist der Singleton `WatchPartyCoordinator`.

**Tech Stack:** Kotlin Multiplatform (Android + iOS), Compose Multiplatform, Navigation 3, supabase realtime-kt (bereits eingebunden), SharedPreferences/NSUserDefaults für Persistenz.

## Global Constraints

- **Arbeitsbranch:** `watch-party-port`, abgezweigt von `cmp-rewrite`. Vor Task 1 anlegen: `git checkout -b watch-party-port`.
- **Quelle:** Desktop-Repo `/home/samson/Projects/NuvioDesktop-dev`, gepinnter Stand `6f4b4710c90765623fad77ae580bc049dd911977` (= `main` am 2026-07-17, enthält die komplette WP-Serie inkl. `baad4577`). Merge-Base beider Repos: `5230f2b9`. In jeder Shell zuerst:
  ```bash
  cd /home/samson/Projects/NuvioMobile
  git fetch /home/samson/Projects/NuvioDesktop-dev main
  export WP_SRC=6f4b4710c90765623fad77ae580bc049dd911977
  export JAVA_HOME=/nix/store/vs2j17dr25ph3h1k4w1ngxnn8y5br1sa-openjdk-21.0.12+2/lib/openjdk
  ```
- **Verifikationskommando** (kompiliert commonMain+androidMain und führt alle commonTest-Klassen aus): `./gradlew :composeApp:testAndroidHostTest`. Erwartung nach jedem Task: `BUILD SUCCESSFUL`, keine Test-Failures.
- **Nicht portieren (Desktop-only):** `PlayerControlsState`-Erweiterungen in `PlayerEngine.kt`, `NativePlayerController.kt`, `player-ui/controls.{html,css,js}`, das `desktopMain`-Storage-Actual, `isDesktop`-Hunks, `FullscreenActionButton`-Hunk in `PlayerControls.kt`, `handlePlayerControlsEvent`-WP-Prefixe in `PlayerScreenRuntimeUi.kt`, Fork-CI (`ab741e27` u. a.), die 4 Spec/Plan-Dokumente unter `docs/superpowers/` des Desktop-Repos.
- **Strings:** nur `values/strings.xml` (Default-Locale), exakt die 42 Keys aus Task 1.
- **Code-Kommentare auf Englisch** (Repo-Konvention).
- **Jeder Commit endet mit** `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- **Hintergrund-Entscheidungen:** siehe `CONTEXT.md` (Glossar: Away, Umzugs-Prompt, Profilwechsel-Regel) und `docs/adr/0001-shared-watchparty-backend.md` (gemeinsames Supabase-Projekt, Protokoll-Lockstep). Beide Dateien existieren bereits im Working Tree und werden in Task 1 mitcommittet.
- **Abweichung vom Grilling-Commit-Schnitt:** Die ursprüngliche Reihenfolge „Layer 1 zuerst" ist nicht kompilierbar (die WP-Player-Dateien und die Runtime-Felder brauchen einander). Dieser Plan schneidet daher: Config/Strings → Refactoring → WP-Kernpaket → Player-Integration → App-Verdrahtung → Storage → Auto-Leave → Away → PiP. Es bleiben 9 Commits, jeder kompiliert.

---

### Task 1: Build-Config + Strings + Session-Dokumente

**Files:**
- Modify: `composeApp/build.gradle.kts` (3 Stellen)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `local.properties` (nicht committen — steht in `.gitignore`)
- Commit (bereits im Working Tree): `CONTEXT.md`, `docs/adr/0001-shared-watchparty-backend.md`, `docs/superpowers/plans/2026-07-17-watch-party-port.md`

**Interfaces:**
- Produces: generiertes Objekt `com.nuvio.app.features.watchparty.WatchPartySupabaseConfig` mit `URL`/`ANON_KEY` (konsumiert von `WatchPartySupabaseProvider` in Task 3); 42 String-Ressourcen `watch_party_*` / `compose_player_watch_party*` / `compose_nav_watch_party` (konsumiert von Tasks 3–5).

- [ ] **Step 1: Task-Properties ergänzen**

In `composeApp/build.gradle.kts`, in der Klasse `GenerateRuntimeConfigsTask`, direkt nach dem Block `@get:Input abstract val supabaseAnonKey: Property<String>` einfügen:

```kotlin
    @get:Input
    abstract val watchPartySupabaseUrl: Property<String>

    @get:Input
    abstract val watchPartySupabaseAnonKey: Property<String>
```

- [ ] **Step 2: Config-Generierung ergänzen**

In derselben Datei, in der `@TaskAction`-Methode, direkt nach dem schließenden `}` des Blocks `outDir.resolve("com/nuvio/app/core/network").apply { … resolve("SupabaseConfig.kt").writeText(…) }` einfügen:

```kotlin
        outDir.resolve("com/nuvio/app/features/watchparty").apply {
            mkdirs()
            resolve("WatchPartySupabaseConfig.kt").writeText(
                """
                |package com.nuvio.app.features.watchparty
                |
                |object WatchPartySupabaseConfig {
                |    const val URL = "${watchPartySupabaseUrl.get()}"
                |    const val ANON_KEY = "${watchPartySupabaseAnonKey.get()}"
                |}
                """.trimMargin()
            )
        }
```

- [ ] **Step 3: Task-Registrierung ergänzen**

In derselben Datei, im Block `val generateRuntimeConfigs = tasks.register<GenerateRuntimeConfigsTask>("generateRuntimeConfigs") { … }`, direkt nach der Zeile `supabaseAnonKey.set(runtimeConfigValue("NUVIO_SUPABASE_ANON_KEY"))` einfügen:

```kotlin
    watchPartySupabaseUrl.set(runtimeConfigValue("NUVIO_WATCHPARTY_SUPABASE_URL"))
    watchPartySupabaseAnonKey.set(runtimeConfigValue("NUVIO_WATCHPARTY_SUPABASE_ANON_KEY"))
```

- [ ] **Step 4: Strings einfügen**

In `composeApp/src/commonMain/composeResources/values/strings.xml` direkt vor `</resources>` einfügen (exakt diese 42 Zeilen):

```xml
    <string name="compose_player_watch_party">Party</string>
    <string name="compose_player_watch_party_with_count">Party (%1$d)</string>
    <string name="watch_party_panel_title">Watch Party</string>
    <string name="watch_party_your_name">Your name</string>
    <string name="watch_party_create_room">Create room</string>
    <string name="watch_party_room_code">Room code</string>
    <string name="watch_party_join_room">Join</string>
    <string name="watch_party_leave_room">Leave room</string>
    <string name="watch_party_participants">Participants</string>
    <string name="watch_party_alone_hint">You are alone in this room. Share the code so others can join.</string>
    <string name="watch_party_guest_name">Guest-%1$d</string>
    <string name="watch_party_reconnecting">Reconnecting…</string>
    <string name="watch_party_not_configured">Watch Party is not configured. Add NUVIO_WATCHPARTY_SUPABASE_URL and NUVIO_WATCHPARTY_SUPABASE_ANON_KEY to local.properties.</string>
    <string name="watch_party_status_playing">Playing</string>
    <string name="watch_party_status_paused">Paused</string>
    <string name="watch_party_status_buffering">Buffering</string>
    <string name="watch_party_status_selecting_source">Selecting source</string>
    <string name="watch_party_status_idle">Browsing</string>
    <string name="compose_nav_watch_party">Watch Party</string>
    <string name="watch_party_screen_title">Watch Party</string>
    <string name="watch_party_lobby_waiting">Waiting for content — start something to kick things off for everyone.</string>
    <string name="watch_party_rejoin_last">Rejoin %1$s</string>
    <string name="watch_party_rejoin_last_with_count">Rejoin %1$s · %2$d in room</string>
    <string name="watch_party_copy_code">Copy room code</string>
    <string name="watch_party_code_copied">Code copied</string>
    <string name="watch_party_now_watching">Watching: %1$s</string>
    <string name="watch_party_open_playback">Go to playback</string>
    <string name="watch_party_toast_joined">%1$s joined</string>
    <string name="watch_party_toast_left">%1$s left</string>
    <string name="watch_party_toast_paused">%1$s paused</string>
    <string name="watch_party_toast_resumed">%1$s resumed</string>
    <string name="watch_party_toast_seeked">%1$s jumped to %2$s</string>
    <string name="watch_party_toast_buffering">Waiting for %1$s (buffering)…</string>
    <string name="watch_party_prompt_title">The room is now watching %1$s</string>
    <string name="watch_party_prompt_show_episodes">Show episodes</string>
    <string name="watch_party_prompt_dismiss">Dismiss</string>
    <string name="watch_party_move_prompt_title">Move the party to %1$s?</string>
    <string name="watch_party_move_prompt_confirm">Move party</string>
    <string name="watch_party_move_prompt_decline">Just for me</string>
    <string name="watch_party_follow_failed">Could not open what the party is watching. Open it manually to rejoin the sync.</string>
    <string name="watch_party_banner_lobby">Watch party lobby — waiting for content</string>
    <string name="watch_party_banner_watching">Party is watching %1$s — tap to join</string>
```

- [ ] **Step 5: local.properties befüllen (lokal, kein Commit)**

```bash
grep NUVIO_WATCHPARTY /home/samson/Projects/NuvioDesktop-dev/local.properties >> local.properties
grep -c NUVIO_WATCHPARTY local.properties
```
Erwartung: `2` (URL + ANON_KEY, identische Werte wie im Desktop-Repo — gleiches Supabase-Projekt, siehe ADR 0001).

- [ ] **Step 6: Verifizieren**

```bash
./gradlew :composeApp:generateRuntimeConfigs
cat composeApp/build/generated/runtimeConfigs/com/nuvio/app/features/watchparty/WatchPartySupabaseConfig.kt
```
Erwartung: Datei existiert, `URL`/`ANON_KEY` tragen die local.properties-Werte. (Hinweis: exakter Output-Pfad kann abweichen — `find composeApp/build -name "WatchPartySupabaseConfig.kt"` falls nötig.)

Danach: `./gradlew :composeApp:testAndroidHostTest` → `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/commonMain/composeResources/values/strings.xml CONTEXT.md docs/
git commit -m "$(cat <<'EOF'
feat: add watch party build config and strings

Ports the WatchPartySupabaseConfig generation (NUVIO_WATCHPARTY_SUPABASE_URL/
_ANON_KEY via local.properties) and the 42 watch_party_* string keys from the
desktop fork (source pinned at 6f4b4710). Also records the port glossary
(CONTEXT.md), ADR 0001 (shared Supabase backend) and the port plan.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `PlayerStreamAutoPlayPolicy`-Refactoring übernehmen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerStreamAutoPlayPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt`

**Interfaces:**
- Produces: `PlayerStreamAutoPlayPolicy(settings, currentStreamBingeGroup)` mit `val plainManualMode: Boolean`; Top-Level-Funktion `internal suspend fun awaitStreamAutoPlaySelection(innerCollectScope: CoroutineScope, policy: PlayerStreamAutoPlayPolicy, type: String, video: MetaVideo): StreamItem?` — harte Abhängigkeit von `PlayerScreenRuntimeWatchPartyFollow.kt` (Task 4; dessen Aufruf `contentType ?: parentMetaType` ist dank `parentMetaType: String` non-null). Verbindlich ist die Byte-Identität mit der Desktop-Quelle, nicht diese Paraphrase.

Hintergrund: Desktop-Commit `2b8f19b8` ist der **einzige** Commit, der beide Dateien berührt; Mobile hat `PlayerNextEpisodeAutoPlay.kt` seit dem Merge-Base nicht angefasst. Das per-File-Delta ist damit exakt das Refactoring und wendet verifiziert sauber an (`git apply --check` getestet am 2026-07-17).

- [ ] **Step 1: Diff anwenden**

```bash
git diff 5230f2b9..$WP_SRC -- \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerStreamAutoPlayPolicy.kt \
  | git apply --index
git status --short
```
Erwartung: `M  …PlayerNextEpisodeAutoPlay.kt`, `A  …PlayerStreamAutoPlayPolicy.kt`, keine Fehler.

- [ ] **Step 2: Verifizieren**

```bash
./gradlew :composeApp:testAndroidHostTest
```
Erwartung: `BUILD SUCCESSFUL` — das Refactoring ist verhaltensgleich, bestehende Tests bleiben grün.

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor: extract PlayerStreamAutoPlayPolicy from next-episode auto-play

Port of desktop commit 2b8f19b8: the stream auto-selection policy moves into
its own file so the watch-party episode follow (next task) can reuse it
without the countdown/next-episode-card UI concerns.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Watch-Party-Kernpaket (`features/watchparty/`)

**Files:**
- Create (10): `composeApp/src/commonMain/kotlin/com/nuvio/app/features/watchparty/{WatchPartyModels,WatchPartySyncEngine,WatchPartySession,WatchPartyClient,SupabaseWatchPartyClient,WatchPartySupabaseProvider,WatchPartyCoordinator,WatchPartyScreen,WatchPartyBanner,WatchPartyPreferencesStorage}.kt`
- Create (13): `composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/{FakeWatchPartyClient,WatchPartyFollowRouterTest,WatchPartyModelsTest,WatchPartySessionPresenceBudgetTest,WatchPartySessionTest,WatchPartySyncEngineAllReadyTest,WatchPartySyncEngineBufferRaceTest,WatchPartySyncEngineContentChangeTest,WatchPartySyncEngineDriftPresenceTest,WatchPartySyncEngineLateJoinTest,WatchPartySyncEngineRemoteTest,WatchPartySyncEngineRoomMoveTest,WatchPartySyncEngineSnapshotTest}.kt`
- Create (2): `composeApp/src/androidMain/kotlin/com/nuvio/app/features/watchparty/WatchPartyPreferencesStorage.android.kt`, `composeApp/src/iosMain/kotlin/com/nuvio/app/features/watchparty/WatchPartyPreferencesStorage.ios.kt` (No-op-Stubs; werden in Task 6 durch echte Implementierungen ersetzt)

**Interfaces:**
- Consumes: `WatchPartySupabaseConfig` (Task 1), `Res.string.watch_party_*` (Task 1), `ProfileRepository`, `TraktPlatformClock`, supabase realtime-kt.
- Produces: `WatchPartyCoordinator` (object: `sessionState`/`roomContent`/`lastRoomCode`/`followInPlayer`/`followViaLaunch`/`followLaunchInProgress`-Flows, `createRoom`/`joinRoom`/`leave`/`requestManualFollow`/`onPlayerBoundContent`/`onPlayerUnbound`/`markLaunchFollowFinished`/`resolveDisplayName`, `isConfigured`), `WatchPartySession`, `WatchPartyScreen(modifier, onOpenPlayback)`, `WatchPartyBannerHost(isPlayerVisible, onOpenTab, onJoinPlayback, modifier)`, Modelle (`WatchPartyContentId`, `WatchPartyPlaybackSnapshot`, `WatchPartyFollowRequest`, Enums).

Das Paket ist in sich geschlossen: **keine** Referenz auf Player-Runtime-Felder (verifiziert per Grep auf Fork-Identifier am 2026-07-17). `WatchPartyPromptSuppressionTest` bleibt außen vor — er testet Funktionen aus `PlayerScreenRuntimeWatchPartyActions.kt` (Task 4). Das `desktopMain`-Actual wird bewusst **nicht** übernommen (Mobile hat kein Desktop-Target).

- [ ] **Step 1: Diff anwenden**

```bash
git diff 5230f2b9..$WP_SRC -- \
  'composeApp/src/commonMain/kotlin/com/nuvio/app/features/watchparty/' \
  'composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/' \
  'composeApp/src/androidMain/kotlin/com/nuvio/app/features/watchparty/' \
  'composeApp/src/iosMain/kotlin/com/nuvio/app/features/watchparty/' \
  ':(exclude)composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/WatchPartyPromptSuppressionTest.kt' \
  | git apply --index
git status --short | wc -l
```
Erwartung: 25 neue Dateien (10 commonMain + 13 commonTest + 2 Stubs), keine Konflikte.

- [ ] **Step 2: Verifizieren**

```bash
./gradlew :composeApp:testAndroidHostTest
```
Erwartung: `BUILD SUCCESSFUL`; die portierten Engine-/Session-/Models-/FollowRouter-Tests laufen und sind grün. Falls ein Import fehlschlägt (einziger bekannter Risikokandidat: `com.nuvio.app.features.trakt.TraktPlatformClock` im Coordinator), existiert das Symbol auf Mobile unter demselben Pfad — per `grep -rn "object TraktPlatformClock" composeApp/src/commonMain` gegenprüfen, bevor irgendetwas umgebaut wird.

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat: port watch party core package from desktop fork

Full features/watchparty/ package (models, pure sync engine, session with
presence budget, transport seam + Supabase client, app-wide coordinator,
lobby screen, global banner, preference storage expect) plus 13 test classes
and the fake client. Android/iOS storage actuals are no-op stubs for now.
Source: desktop fork pinned at 6f4b4710, delta 5230f2b9..HEAD.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Player-Integration (WP-Player-Dateien + Runtime-Hooks)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/{PlayerWatchPartyPanel,PlayerScreenRuntimeWatchPartyActions,PlayerScreenRuntimeWatchPartyFollow}.kt`, `composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/WatchPartyPromptSuppressionTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerControls.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/streams/StreamLaunchStore.kt`

**Interfaces:**
- Consumes: Task-3-Typen, `PlayerStreamAutoPlayPolicy`/`awaitStreamAutoPlaySelection` (Task 2), vorhandene Runtime-Member `switchToEpisodeStream`/`openEpisodesPanel`/`playerMetaVideos`/`currentStreamBingeGroup` (alle auf Mobile verifiziert vorhanden).
- Produces: Runtime-Felder `watchPartySession`, `watchPartySessionState`, `showWatchPartyPanel`, `watchPartyDisplayName`, `watchPartyToast(-Job)`, `watchPartyContentPrompt`, `watchPartyDismissedPrompt`, `watchPartyMoveRoomPrompt`; `BindWatchPartyEffects()`; `RenderWatchPartyOverlays()`; `WatchPartyBadgePill(sessionState, onClick)`; `StreamLaunch.isWatchPartyFollow: Boolean` — konsumiert von Task 5, 8, 9.

- [ ] **Step 1: Die vier geschlossenen Dateien per Diff anlegen**

```bash
git diff 5230f2b9..$WP_SRC -- \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerWatchPartyPanel.kt \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeWatchPartyActions.kt \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeWatchPartyFollow.kt \
  composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/WatchPartyPromptSuppressionTest.kt \
  | git apply --index
```

- [ ] **Step 2: Runtime-State-Felder ergänzen** (`PlayerScreenRuntimeState.kt`)

Zu den Imports (alphabetisch neben `com.nuvio.app.features.watched.WatchedUiState`) hinzufügen:

```kotlin
import com.nuvio.app.features.watchparty.WatchPartyContentId
import com.nuvio.app.features.watchparty.WatchPartySession
import com.nuvio.app.features.watchparty.WatchPartySessionState
```

Direkt nach der Zeile `var showEpisodesPanel by mutableStateOf(false)` einfügen:

```kotlin
    var showWatchPartyPanel by mutableStateOf(false)
    var watchPartySession by mutableStateOf<WatchPartySession?>(null)
    var watchPartySessionState by mutableStateOf(WatchPartySessionState())
    var watchPartyDisplayName by mutableStateOf("")
    var watchPartyToast by mutableStateOf<WatchPartyToastState?>(null)
    var watchPartyToastJob by mutableStateOf<Job?>(null)
    var watchPartyContentPrompt by mutableStateOf<WatchPartyContentId?>(null)
    var watchPartyDismissedPrompt by mutableStateOf<WatchPartyContentId?>(null)
    var watchPartyMoveRoomPrompt by mutableStateOf<WatchPartyContentId?>(null)
```

(`WatchPartyToastState` liegt in `PlayerScreenRuntimeWatchPartyActions.kt`, gleiches Paket — kein Import. `kotlinx.coroutines.Job` ist bereits importiert. Die Desktop-Hunks `resizeModeStretchLabel`, `controlsActivityTick`, `supportedOnCurrentPlatform()`, `mutableStateOf`-Umbau der Stream-States, `submitIntro*`-Extras, `playerControlsPendingP2pSwitch/CloseModalsToken` sind Fork-Rauschen — **nicht** übernehmen.)

- [ ] **Step 3: Effects-Hook ergänzen** (`PlayerScreenRuntimeEffects.kt`)

In `BindPlayerRuntimeEffects()`, direkt nach der Zeile `BindPlayerMetadataAndSkipEffects()` einfügen:

```kotlin
    BindWatchPartyEffects()
```

(Die Desktop-Hunks `isDesktop`-Import/-Seek-Block und `controlsActivityTick` **nicht** übernehmen.)

- [ ] **Step 4: Player-Controls erweitern** (`PlayerControls.kt`)

4a. Import ergänzen (alphabetisch bei den anderen `material.icons.rounded`-Imports):

```kotlin
import androidx.compose.material.icons.rounded.Groups
```

4b. In der Signatur von `PlayerControlsShell`, direkt nach `onEpisodesClick: (() -> Unit)? = null,`:

```kotlin
    onWatchPartyClick: (() -> Unit)? = null,
    watchPartyParticipantCount: Int = 0,
    watchPartyBadge: (@Composable () -> Unit)? = null,
```

4c. Im `PlayerHeader(...)`-Aufruf innerhalb von `PlayerControlsShell`, direkt nach `onBack = onBack,`:

```kotlin
                watchPartyBadge = watchPartyBadge,
```

4d. Im `ProgressControls(...)`-Aufruf innerhalb von `PlayerControlsShell`, direkt nach `onEpisodesClick = onEpisodesClick,`:

```kotlin
                    onWatchPartyClick = onWatchPartyClick,
                    watchPartyParticipantCount = watchPartyParticipantCount,
```

4e. In der Signatur von `private fun PlayerHeader(...)`, direkt nach `onBack: () -> Unit,`:

```kotlin
    watchPartyBadge: (@Composable () -> Unit)? = null,
```

4f. In `PlayerHeader`, in der `Row` mit `horizontalArrangement = Arrangement.spacedBy(10.dp)`, als **erstes** Element vor dem `if (onSubmitIntroClick != null)`-Block:

```kotlin
                    if (watchPartyBadge != null) {
                        watchPartyBadge()
                    }
```

4g. In der Signatur von `private fun ProgressControls(...)`, direkt nach `onEpisodesClick: (() -> Unit)? = null,`:

```kotlin
    onWatchPartyClick: (() -> Unit)? = null,
    watchPartyParticipantCount: Int = 0,
```

4h. In `ProgressControls`, direkt nach dem schließenden `}` des `if (onEpisodesClick != null) { PlayerActionPillButton(…) }`-Blocks:

```kotlin
                    if (onWatchPartyClick != null) {
                        PlayerActionPillButton(
                            label = if (watchPartyParticipantCount > 0) {
                                stringResource(Res.string.compose_player_watch_party_with_count, watchPartyParticipantCount)
                            } else {
                                stringResource(Res.string.compose_player_watch_party)
                            },
                            icon = Icons.Rounded.Groups,
                            onClick = onWatchPartyClick,
                        )
                    }
```

(Die Desktop-Hunks `FullscreenActionButton` und das `isDesktop`-Top-Padding **nicht** übernehmen.)

- [ ] **Step 5: Runtime-UI verdrahten** (`PlayerScreenRuntimeUi.kt`)

5a. In `RenderPlayerRuntimeUi()`, direkt nach der Zeile `RenderPlayerModals(displayedPositionMs = displayedPositionMs)`:

```kotlin
        RenderWatchPartyOverlays()
```

5b. Im `PlayerControlsShell(...)`-Aufruf in `RenderPlayerControls(...)`, direkt nach dem `onEpisodesClick = …`-Argument:

```kotlin
            onWatchPartyClick = {
                showWatchPartyPanel = true
                controlsVisible = true
            },
            watchPartyParticipantCount = if (watchPartySessionState.isActive) {
                watchPartySessionState.participants.size
            } else {
                0
            },
            watchPartyBadge = if (watchPartySessionState.isActive) {
                {
                    WatchPartyBadgePill(
                        sessionState = watchPartySessionState,
                        onClick = {
                            showWatchPartyPanel = true
                            controlsVisible = true
                        },
                    )
                }
            } else {
                null
            },
```

(Der komplette `PlayerControlsState`-/`watchPartyStatusLabels`-/`handlePlayerControlsEvent`-Anteil des Desktop-Diffs ist Desktop-only — **nicht** übernehmen.)

- [ ] **Step 6: StreamLaunch-Flag ergänzen** (`StreamLaunchStore.kt`)

In `data class StreamLaunch(…)`, direkt nach `val startFromBeginning: Boolean = false,`:

```kotlin
    val isWatchPartyFollow: Boolean = false,
```

- [ ] **Step 7: Verifizieren**

```bash
./gradlew :composeApp:testAndroidHostTest
```
Erwartung: `BUILD SUCCESSFUL`; jetzt laufen alle 14 portierten Testklassen (inkl. `WatchPartyPromptSuppressionTest`) grün.

- [ ] **Step 8: Commit**

```bash
git add -A composeApp/src
git commit -m "$(cat <<'EOF'
feat: integrate watch party into the player runtime

In-player panel, pill button and participant badge (Compose controls),
runtime state fields, session bind/unbind effects, episode follow via the
shared PlayerStreamAutoPlayPolicy, and the isWatchPartyFollow launch flag.
Desktop-only native-overlay hunks (PlayerControlsState mapping, WebView
event prefixes, FullscreenActionButton) are deliberately not ported.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: App-Verdrahtung (Route, Home-Einstieg, Banner, Follow-Launch)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/navigation/Routes.kt`
- Create: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/watchparty/WatchPartyHomeEntry.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt` (6 Stellen)

**Interfaces:**
- Consumes: `WatchPartyCoordinator`, `WatchPartyBannerHost`, `WatchPartyScreen` (Task 3), `StreamLaunch.isWatchPartyFollow` (Task 4).
- Produces: `WatchPartyRoute` (AppRoute), Home-Einstieg, globaler Banner, `followViaLaunch`-Pfad.

- [ ] **Step 1: Route definieren** (`Routes.kt`, ans Dateiende)

```kotlin
@Serializable
data class WatchPartyRoute(override val title: String = "") : AppRoute {
    /** Lobby lives on the Home stack on iOS native navigation. */
    override val preferredTabName: String
        get() = "Home"
}
```

- [ ] **Step 2: Home-Einstiegs-Composable anlegen** (`WatchPartyHomeEntry.kt`, neue Datei)

```kotlin
package com.nuvio.app.features.watchparty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_watch_party
import org.jetbrains.compose.resources.stringResource

/**
 * Floating watch-party entry on the Home tab (phones have no persistent top
 * bar). Hidden entirely when the feature is not configured; shows an active
 * dot while a session is running.
 */
@Composable
fun WatchPartyHomeEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!WatchPartyCoordinator.isConfigured) return
    val sessionState by WatchPartyCoordinator.sessionState.collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = stringResource(Res.string.compose_nav_watch_party),
                modifier = Modifier.padding(10.dp).size(22.dp),
            )
        }
        if (sessionState.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
```

Hinweis: Der `Res`-Importpfad (`nuvio.composeapp.generated.resources.…`) muss dem der übrigen Dateien entsprechen — bei Abweichung den Import aus `WatchPartyScreen.kt` (Task 3) übernehmen.

- [ ] **Step 3: `App.kt` — Imports + Titel-Val**

Imports ergänzen (bei den vorhandenen `com.nuvio.app.features.…`-Imports):

```kotlin
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import com.nuvio.app.features.watchparty.WatchPartyBannerHost
import com.nuvio.app.features.watchparty.WatchPartyCoordinator
import com.nuvio.app.features.watchparty.WatchPartyHomeEntry
import com.nuvio.app.features.watchparty.WatchPartyScreen
import com.nuvio.app.navigation.WatchPartyRoute
```
(`androidx.compose.foundation.layout.WindowInsets` ist in `App.kt` bereits importiert — `WindowInsets(0)` wird genutzt; die beiden Inset-Imports nur ergänzen, falls nicht vorhanden.)
(Nur die, die nicht ohnehin per bestehendem Wildcard-/Einzelimport abgedeckt sind; `WatchPartyRoute` liegt im selben Paket wie `PlayerRoute` — falls `App.kt` Routen unqualifiziert nutzt, entfällt der Import.)

Direkt nach `val collectionsTitle = stringResource(Res.string.collections_header)` (App.kt:870):

```kotlin
    val watchPartyTitle = stringResource(Res.string.watch_party_screen_title)
```

- [ ] **Step 4: `App.kt` — `followViaLaunch`-Collector + `isWatchPartyFollow`-Parameter**

4a. In der Signatur von `launchPlaybackWithDownloadPreference` (App.kt:1504), direkt nach `startFromBeginning: Boolean,`:

```kotlin
            isWatchPartyFollow: Boolean = false,
```

4b. Im `StreamLaunchStore.put(StreamLaunch(…))`-Aufruf am Ende derselben Funktion, direkt nach `startFromBeginning = startFromBeginning,`:

```kotlin
                    isWatchPartyFollow = isWatchPartyFollow,
```

4c. Direkt nach dem schließenden `}` von `launchPlaybackWithDownloadPreference` (vor `val onPlay: …`, App.kt:≈1597) einfügen:

```kotlin
        val watchPartyFollowFailedText = stringResource(Res.string.watch_party_follow_failed)
        LaunchedEffect(Unit) {
            WatchPartyCoordinator.followViaLaunch.collect { request ->
                val content = request.contentId
                val meta = runCatching {
                    MetaDetailsRepository.fetch(content.mediaType, content.metaId)
                }.getOrNull()
                if (meta == null) {
                    WatchPartyCoordinator.markLaunchFollowFinished()
                    NuvioToastController.show(watchPartyFollowFailedText)
                    return@collect
                }
                val video = if (content.season != null || content.episode != null) {
                    meta.videos.firstOrNull { it.season == content.season && it.episode == content.episode }
                } else {
                    null
                }
                if ((content.season != null || content.episode != null) && video == null) {
                    WatchPartyCoordinator.markLaunchFollowFinished()
                    NuvioToastController.show(watchPartyFollowFailedText)
                    return@collect
                }
                if (navController.currentRoute is PlayerRoute) {
                    navController.popBackStack()
                }
                launchPlaybackWithDownloadPreference(
                    type = content.mediaType,
                    videoId = video?.id ?: content.metaId,
                    parentMetaId = content.metaId,
                    parentMetaType = content.mediaType,
                    title = meta.name,
                    logo = meta.logo,
                    poster = meta.poster,
                    background = meta.background,
                    seasonNumber = content.season,
                    episodeNumber = content.episode,
                    episodeTitle = video?.title,
                    episodeThumbnail = video?.thumbnail,
                    pauseDescription = null,
                    resumePositionMs = request.resumePositionMs,
                    resumeProgressFraction = null,
                    manualSelection = false,
                    startFromBeginning = request.resumePositionMs <= 0L,
                    isWatchPartyFollow = true,
                )
            }
        }
```

(`stringResource` in Composable-Kontext: der umgebende Block ist dieselbe Composable-Funktion, in der auch `launchPlaybackWithDownloadPreference` definiert ist — identisch zum Desktop-Muster.)

- [ ] **Step 5: `App.kt` — Abbruch-Erkennung verlassener Follows im StreamRoute-Entry**

Im `entry<StreamRoute> { route -> … }`-Block (App.kt:2183), direkt nach der Zeile `var pendingP2pStreamOpen by remember { mutableStateOf<PendingP2pStreamOpen?>(null) }` einfügen:

```kotlin
                    // StreamLaunchStore cleanup lives elsewhere; this effect only detects a
                    // watch-party follow abandoned without starting playback (leaving
                    // composition without having navigated to the player).
                    val watchPartyFollowNavigatedToPlayer = remember { mutableStateOf(false) }
                    DisposableEffect(route.launchId) {
                        onDispose {
                            if (launch.isWatchPartyFollow && !watchPartyFollowNavigatedToPlayer.value) {
                                WatchPartyCoordinator.markLaunchFollowFinished()
                            }
                        }
                    }
```

Anschließend an **allen drei** `navController.navigate(PlayerRoute(launchId = launchId, title = playerLaunch.title)) {`-Stellen innerhalb dieses Entries (App.kt:2325, 2450, 2586) direkt **davor** einfügen:

```kotlin
                        watchPartyFollowNavigatedToPlayer.value = true
```

- [ ] **Step 6: `App.kt` — Banner, Route-Entry, Home-Einstieg**

6a. Im Root-Overlay-Box von `MainAppContent`, direkt nach dem `NuvioToastHost(…)`-Aufruf (App.kt:3496):

```kotlin
            WatchPartyBannerHost(
                isPlayerVisible = navController.currentRoute is PlayerRoute,
                onOpenTab = {
                    if (navController.currentRoute !is WatchPartyRoute) {
                        navController.navigate(WatchPartyRoute(watchPartyTitle))
                    }
                },
                onJoinPlayback = { WatchPartyCoordinator.requestManualFollow() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(16f),
            )
```

6b. Im `entryProvider`-Block, direkt **vor** `entry<PlayerRoute>(` (App.kt:2817):

```kotlin
                entry<WatchPartyRoute> {
                    WatchPartyScreen(
                        onOpenPlayback = { WatchPartyCoordinator.requestManualFollow() },
                    )
                }
```

6c. `AppTabHost` (App.kt:3526): Parameter ergänzen, direkt vor `requestedSettingsPageName: String? = null,`:

```kotlin
    onOpenWatchParty: (() -> Unit)? = null,
```

Den `AppScreenTab.Home ->`-Zweig (App.kt:≈3570) so umbauen, dass `HomeScreen` unverändert bleibt, aber in eine Box mit dem Einstieg wandert:

```kotlin
                AppScreenTab.Home -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeScreen(
                            modifier = Modifier.fillMaxSize(),
                            animateCollectionGifs = animateHomeCollectionGifs,
                            scrollToTopRequests = homeScrollToTopRequests,
                            onCatalogClick = onCatalogClick,
                            onPosterClick = onPosterClick,
                            onPosterLongClick = onPosterLongClick,
                            onContinueWatchingClick = onContinueWatchingClick,
                            onContinueWatchingLongPress = onContinueWatchingLongPress,
                            onFolderClick = onFolderClick,
                            onFirstCatalogRendered = onInitialHomeContentRendered,
                        )
                        if (onOpenWatchParty != null) {
                            WatchPartyHomeEntry(
                                onClick = onOpenWatchParty,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(top = 8.dp, end = 16.dp),
                            )
                        }
                    }
                }
```

6d. Am `AppTabHost`-Aufrufort (der Block mit `onCollectionsSettingsClick = …`, App.kt:≈2004), einen Parameter ergänzen:

```kotlin
                                        onOpenWatchParty = {
                                            if (navController.currentRoute !is WatchPartyRoute) {
                                                navController.navigate(WatchPartyRoute(watchPartyTitle))
                                            }
                                        },
```

- [ ] **Step 7: Verifizieren**

```bash
./gradlew :composeApp:testAndroidHostTest
```
Erwartung: `BUILD SUCCESSFUL`. (Funktionale Prüfung: siehe Gesamtverifikation am Ende.)

- [ ] **Step 8: Commit**

```bash
git add -A composeApp/src
git commit -m "$(cat <<'EOF'
feat: wire watch party into app navigation

WatchPartyRoute on the nav stack (no fifth tab; Home stack on iOS native
navigation), floating Home entry with active dot, global banner next to the
toast host, followViaLaunch collector into
launchPlaybackWithDownloadPreference(isWatchPartyFollow=true), and abandoned
launch-follow detection in the StreamRoute entry.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Echte Storage-Actuals (Rejoin-Persistenz)

**Files:**
- Modify (ersetzen): `composeApp/src/androidMain/kotlin/com/nuvio/app/features/watchparty/WatchPartyPreferencesStorage.android.kt`
- Modify (ersetzen): `composeApp/src/iosMain/kotlin/com/nuvio/app/features/watchparty/WatchPartyPreferencesStorage.ios.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/nuvio/app/MainActivity.kt` (1 Zeile)

**Interfaces:**
- Produces: funktionierende `loadLastRoomCode()/saveLastRoomCode()/clearLastRoomCode()` auf Android + iOS, Keys profilgebunden via `ProfileScopedKey` (Muster: `CardDepthStyleStorage`).

- [ ] **Step 1: Android-Actual ersetzen** (kompletter neuer Dateiinhalt)

```kotlin
package com.nuvio.app.features.watchparty

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object WatchPartyPreferencesStorage {
    private const val preferencesName = "nuvio_watch_party"
    private const val lastRoomCodeKey = "last_room_code"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadLastRoomCode(): String? =
        preferences?.getString(ProfileScopedKey.of(lastRoomCodeKey), null)

    actual fun saveLastRoomCode(code: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(lastRoomCodeKey), code)
            ?.apply()
    }

    actual fun clearLastRoomCode() {
        preferences
            ?.edit()
            ?.remove(ProfileScopedKey.of(lastRoomCodeKey))
            ?.apply()
    }
}
```

- [ ] **Step 2: Initialisierung verdrahten**

In `MainActivity.kt`, direkt nach `CardDepthStyleStorage.initialize(applicationContext)` (Zeile 98):

```kotlin
        com.nuvio.app.features.watchparty.WatchPartyPreferencesStorage.initialize(applicationContext)
```
(Oder mit Import `com.nuvio.app.features.watchparty.WatchPartyPreferencesStorage` und unqualifiziertem Aufruf — an den Stil der Nachbarzeilen anpassen.)

- [ ] **Step 3: iOS-Actual ersetzen** (kompletter neuer Dateiinhalt)

```kotlin
package com.nuvio.app.features.watchparty

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object WatchPartyPreferencesStorage {
    private const val lastRoomCodeKey = "nuvio_watch_party_last_room_code"

    actual fun loadLastRoomCode(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(lastRoomCodeKey))

    actual fun saveLastRoomCode(code: String) {
        NSUserDefaults.standardUserDefaults.setObject(code, ProfileScopedKey.of(lastRoomCodeKey))
    }

    actual fun clearLastRoomCode() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(lastRoomCodeKey))
    }
}
```

- [ ] **Step 4: Verifizieren**

```bash
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:compileIosMainKotlinMetadata 2>/dev/null || ./gradlew :composeApp:compileKotlinIosSimulatorArm64
```
Erwartung: beide `BUILD SUCCESSFUL` (der zweite Befehl prüft, dass das iOS-Actual kompiliert; exakten Task-Namen ggf. via `./gradlew :composeApp:tasks --all | grep -i iosSimulator` ermitteln).

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src
git commit -m "$(cat <<'EOF'
feat: persist last watch party room per profile on Android/iOS

Replaces the desktop-only no-op stubs with SharedPreferences (Android,
initialized in MainActivity) and NSUserDefaults (iOS), keys scoped via
ProfileScopedKey — the rejoin shortcut now works on mobile.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Profilwechsel-Auto-Leave

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/watchparty/WatchPartyCoordinator.kt`

**Interfaces:**
- Consumes: `ProfileRepository.state: StateFlow<ProfileState>` mit `activeProfileIndex: Int`.
- Produces: Domänenregel „Profilwechsel beendet die Teilnahme" (siehe `CONTEXT.md`); `lastRoomCode` wird nach dem Wechsel für das neue Profil neu geladen (kein Auto-Rejoin).

- [ ] **Step 1: Profil-Beobachter ergänzen**

In `WatchPartyCoordinator`, direkt nach der Deklaration `val isConfigured: Boolean get() = WatchPartySupabaseProvider.isConfigured` einfügen:

```kotlin
    init {
        // Party membership is bound to the profile identity: switching profiles
        // auto-leaves the room (no auto-rejoin — the new profile joins on purpose)
        // and reloads the profile-scoped last room code for the rejoin shortcut.
        scope.launch {
            ProfileRepository.state
                .map { it.activeProfileIndex }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (_session.value != null) leave()
                    _lastRoomCode.value = WatchPartyPreferencesStorage.loadLastRoomCode()
                }
        }
    }
```

Imports ergänzen:

```kotlin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
```

- [ ] **Step 2: Verifizieren**

```bash
./gradlew :composeApp:testAndroidHostTest
```
Erwartung: `BUILD SUCCESSFUL`, bestehende Tests grün. (Der Coordinator ist ein `Dispatchers.Main`-Singleton und hat bewusst keine Unit-Tests; das Verhalten wird in der Gesamtverifikation auf dem Gerät geprüft: Party beitreten → Profil wechseln → Teilnehmer verschwindet bei den anderen, Lobby zeigt den Rejoin-Shortcut des neuen Profils bzw. keinen.)

- [ ] **Step 3: Commit**

```bash
git add -A composeApp/src
git commit -m "$(cat <<'EOF'
feat: auto-leave watch party on profile switch

Party membership is bound to the profile identity: switching the active
profile leaves the room and reloads the profile-scoped last room code.
No auto-rejoin — the new profile joins deliberately.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Away-Modus (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/watchparty/WatchPartySession.kt`
- Create: `composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/WatchPartySessionAwayTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeWatchPartyActions.kt` (Lifecycle-Wiring in `BindWatchPartyEffects`)

**Interfaces:**
- Produces: `WatchPartySession.onAppBackgrounded()` / `onAppForegrounded()`; Konstruktor-Param `lifecyclePauseWindowMs: Long = 2_000L`.
- Semantik (siehe `CONTEXT.md`, „Away"): Ein Play→Pause-Flip, der innerhalb des Fensters nach `onAppBackgrounded()` eintrifft, ist eine Lifecycle-Pause → **kein** Raum-Broadcast, einmalige IDLE-Presence, Session friert ein (keine Kommandos/Broadcasts/Drift-Ticks; Remote-States aktualisieren weiter `lastKnownState`). `onAppForegrounded()` gleicht über `engine.applyKnownState` an den Raum an und erneuert die Presence. Läuft die Wiedergabe im Hintergrund weiter (PiP/Background-Audio), kommt kein Pause-Flip → Session bleibt voll aktiv.

**Warum Activity- statt Prozess-Lifecycle:** `ProcessLifecycleOwner` verzögert ON_STOP um ~700 ms — der Pause-Broadcast wäre dann längst raus. Der Player-Engine-Pause und unser Marker hängen am **selben** Activity-Lifecycle; der Marker ist ein synchroner `@Volatile`-Write, die Snapshot-Zustellung ist immer per `scope.launch` gequeued — der Marker gewinnt deshalb unabhängig von der Observer-Reihenfolge.

- [ ] **Step 1: Failing Tests schreiben** (`WatchPartySessionAwayTest.kt`, kompletter Dateiinhalt)

```kotlin
package com.nuvio.app.features.watchparty

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Away mode: backgrounding the app must not pause the room. A play->pause flip
 * arriving within the lifecycle window after onAppBackgrounded() is an
 * OS-caused pause — suppressed, announced as IDLE. onAppForegrounded()
 * realigns to the room via applyKnownState.
 */
class WatchPartySessionAwayTest {

    private val content = WatchPartyContentId("tt1", "movie")

    private class Harness {
        var now: Long = 1_000_000L
        val room = FakeWatchPartyRoom()
        val client: FakeWatchPartyClient = room.client()
        val observer: FakeWatchPartyClient = room.client()
        val observedStates = mutableListOf<WatchPartyRoomState>()
        val commands = mutableListOf<WatchPartyPlayerCommand>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session: WatchPartySession = WatchPartySession(
            client = client,
            scope = scope,
            nowMs = { now },
            actorId = "actor-a",
            driftTickIntervalMs = 3_600_000L,
            presenceMinGapMs = 0L,
            presenceWindowMs = 60_000L,
            presenceMaxPerWindow = 100,
        )

        suspend fun joinPlaying(content: WatchPartyContentId) {
            scope.launch { session.commands.collect { commands += it } }
            scope.launch { observer.incomingStates.collect { observedStates += it } }
            observer.join("ABCDEF", WatchPartyPresencePayload("actor-b", "Bob", WatchPartyParticipantStatus.IDLE, null))
            session.join("ABCDEF", "Anna")
            session.setFollowing(true)
            session.onContentChanged(content)
            session.onPlaybackSnapshot(WatchPartyPlaybackSnapshot(isPlaying = true, positionMs = 0L, isBuffering = false))
        }
    }

    @Test
    fun lifecyclePauseDoesNotPauseTheRoom() = runBlocking {
        val h = Harness()
        h.joinPlaying(content)
        val baseline = h.observedStates.size

        h.session.onAppBackgrounded()
        h.now += 100
        h.session.onPlaybackSnapshot(WatchPartyPlaybackSnapshot(isPlaying = false, positionMs = 100L, isBuffering = false))

        val newBroadcasts = h.observedStates.drop(baseline)
        assertTrue(
            newBroadcasts.none { !it.isPlaying },
            "a lifecycle pause must not broadcast a room pause, got: $newBroadcasts",
        )
        assertEquals(
            WatchPartyParticipantStatus.IDLE,
            h.client.currentPresence?.status,
            "the away participant announces IDLE",
        )
    }

    @Test
    fun pauseFlipAfterWindowIsUserIntent() = runBlocking {
        val h = Harness()
        h.joinPlaying(content)
        val baseline = h.observedStates.size

        h.session.onAppBackgrounded()
        h.now += 10_000 // window (2s) long expired
        h.session.onPlaybackSnapshot(WatchPartyPlaybackSnapshot(isPlaying = false, positionMs = 10_000L, isBuffering = false))

        val newBroadcasts = h.observedStates.drop(baseline)
        assertTrue(
            newBroadcasts.any { !it.isPlaying },
            "a pause outside the lifecycle window is user intent and must pause the room",
        )
    }

    @Test
    fun remoteCommandsAreSuppressedWhileAway() = runBlocking {
        val h = Harness()
        h.joinPlaying(content)
        h.session.onAppBackgrounded()
        h.now += 100
        h.session.onPlaybackSnapshot(WatchPartyPlaybackSnapshot(isPlaying = false, positionMs = 0L, isBuffering = false))
        val baseline = h.commands.size

        h.client.emitState(
            WatchPartyRoomState(
                contentId = content,
                isPlaying = false,
                positionMs = 0L,
                atWallClockMs = h.now,
                actorId = "actor-b",
                seq = 10L,
                reason = WatchPartyStateReason.USER,
            ),
        )

        assertEquals(baseline, h.commands.size, "no player commands while away")
    }

    @Test
    fun foregroundRealignsToTheRoom() = runBlocking {
        val h = Harness()
        h.joinPlaying(content)
        h.session.onAppBackgrounded()
        h.now += 100
        h.session.onPlaybackSnapshot(WatchPartyPlaybackSnapshot(isPlaying = false, positionMs = 0L, isBuffering = false))
        // Room pauses at position 0 while we are away.
        h.client.emitState(
            WatchPartyRoomState(
                contentId = content,
                isPlaying = false,
                positionMs = 0L,
                atWallClockMs = h.now,
                actorId = "actor-b",
                seq = 10L,
                reason = WatchPartyStateReason.USER,
            ),
        )
        val baseline = h.commands.size

        h.now += 500
        h.session.onAppForegrounded()

        assertTrue(
            h.commands.drop(baseline).contains(WatchPartyPlayerCommand.Pause),
            "returning from away must realign the (engine-side still playing) player to the paused room",
        )
    }
}
```

- [ ] **Step 2: Tests laufen lassen — sie müssen scheitern**

```bash
./gradlew :composeApp:testAndroidHostTest --tests "com.nuvio.app.features.watchparty.WatchPartySessionAwayTest" 2>&1 | tail -20
```
Erwartung: Compile-Fehler `unresolved reference: onAppBackgrounded` (o. ä.).

- [ ] **Step 3: Session-Implementierung**

In `WatchPartySession.kt`:

3a. Konstruktor-Parameter ergänzen, nach `presenceMaxPerWindow: Int = 4,`:

```kotlin
    private val lifecyclePauseWindowMs: Long = 2_000L,
```

3b. Import ergänzen:

```kotlin
import kotlin.concurrent.Volatile
```

3c. Felder ergänzen, nach `private var lastEngineStatus: WatchPartyParticipantStatus? = null`:

```kotlin
    // Away mode: set synchronously from the player's lifecycle observer. Snapshot
    // delivery is always queued on [scope], so this mark is visible to the queued
    // pause flip regardless of lifecycle observer ordering.
    @Volatile
    private var appBackgroundedAtMs: Long = Long.MIN_VALUE
    private var lifecycleSuspended = false
    private var lastForwardedIsPlaying: Boolean? = null
```

3d. `onPlaybackSnapshot` umbauen — bestehende Methode ersetzen durch:

```kotlin
    fun onPlaybackSnapshot(snapshot: WatchPartyPlaybackSnapshot) {
        scope.launch {
            if (lifecycleSuspended) return@launch
            val wasPlaying = lastForwardedIsPlaying
            lastForwardedIsPlaying = snapshot.isPlaying
            val backgroundedAt = appBackgroundedAtMs
            val isLifecyclePause = backgroundedAt != Long.MIN_VALUE &&
                wasPlaying == true &&
                !snapshot.isPlaying &&
                nowMs() - backgroundedAt <= lifecyclePauseWindowMs
            if (isLifecyclePause) {
                // The OS paused us, not the user: freeze instead of pausing the room.
                lifecycleSuspended = true
                bufferProbeJob?.cancel()
                bufferProbeJob = null
                sendPresenceThrottled(buildPresencePayload(WatchPartyParticipantStatus.IDLE))
                return@launch
            }
            dispatch(engine.onSnapshot(snapshot, nowMs()))
            if (snapshot.isBuffering) {
                if (bufferProbeJob?.isActive != true) {
                    bufferProbeJob = scope.launch {
                        while (true) {
                            delay(engineConfig.bufferDebounceMs + 50L)
                            dispatch(engine.onBufferProbe(nowMs()))
                        }
                    }
                }
            } else {
                bufferProbeJob?.cancel()
                bufferProbeJob = null
            }
        }
    }
```
(Der Teil ab `dispatch(engine.onSnapshot…)` ist der unveränderte Bestandscode.)

3e. Drift-Loop in `join(...)` gaten — die Zeile `dispatch(engine.onDriftTick(nowMs()))` ersetzen durch:

```kotlin
                if (!lifecycleSuspended) dispatch(engine.onDriftTick(nowMs()))
```

3f. `dispatch(...)` gaten — als **erste** Zeile der Methode:

```kotlin
        // Away: keep the engine's room model fresh (state intake happened in the
        // caller), but let no commands, broadcasts or presence out.
        if (lifecycleSuspended) return
```

3g. Neue öffentliche Methoden, direkt nach `setFollowing(...)`:

```kotlin
    /** Synchronous mark from the player's lifecycle observer (ON_STOP). */
    fun onAppBackgrounded() {
        appBackgroundedAtMs = nowMs()
    }

    /** Clears away mode and realigns the local player with the room (ON_START). */
    fun onAppForegrounded() {
        appBackgroundedAtMs = Long.MIN_VALUE
        scope.launch {
            if (!lifecycleSuspended) return@launch
            lifecycleSuspended = false
            dispatch(engine.applyKnownState(nowMs()))
            sendPresenceThrottled(
                buildPresencePayload(lastEngineStatus ?: WatchPartyParticipantStatus.IDLE),
            )
        }
    }
```

- [ ] **Step 4: Tests laufen lassen — grün**

```bash
./gradlew :composeApp:testAndroidHostTest --tests "com.nuvio.app.features.watchparty.WatchPartySessionAwayTest"
```
Erwartung: 4 Tests, alle PASS. Danach die volle Suite: `./gradlew :composeApp:testAndroidHostTest` → alles grün (insbesondere die bestehenden Session-/Drift-Tests dürfen sich nicht ändern).

- [ ] **Step 5: Lifecycle-Wiring im Player**

In `PlayerScreenRuntimeWatchPartyActions.kt`, in `BindWatchPartyEffects()`, direkt vor dem abschließenden `DisposableEffect(Unit) { … onPlayerUnbound() }`-Block einfügen:

```kotlin
    // Away mode: the engine pauses playback on the same activity lifecycle's
    // ON_STOP; marking the session first (synchronous volatile write) lets it
    // swallow that flip instead of pausing the room. CMP maps iOS backgrounding
    // to the same ON_STOP/ON_START events.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session, lifecycleOwner) {
        val active = session ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> active.onAppBackgrounded()
                Lifecycle.Event.ON_START -> active.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

Imports ergänzen:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

- [ ] **Step 6: Volle Suite + Commit**

```bash
./gradlew :composeApp:testAndroidHostTest
git add -A composeApp/src
git commit -m "$(cat <<'EOF'
feat: away mode — backgrounding never pauses the watch party room

A play->pause flip within 2s of the player lifecycle's ON_STOP is treated as
an OS pause: no room broadcast, one IDLE presence, session frozen (remote
states keep updating the room model, commands/broadcasts/drift suppressed).
ON_START realigns via applyKnownState and refreshes presence. PiP and
background audio keep playing and therefore never trigger the gate.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: PiP-Prompt-Deferral

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` (Overlay-Gate)
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeWatchPartyActions.kt` (Presence-Wiring)
- Create: `composeApp/src/commonTest/kotlin/com/nuvio/app/features/watchparty/WatchPartyPromptDeferralTest.kt`

**Interfaces:**
- Consumes: `rememberIsInPictureInPicture()` (vorhandenes commonMain-expect in `PlayerPlatformEffects.kt:31`), `WatchPartySession.setFollowing(Boolean)` + `mappedStatus`-Semantik (SELECTING_SOURCE → IDLE wenn nicht following).
- Semantik (siehe `CONTEXT.md`, „Umzugs-Prompt"): In PiP werden Prompts nicht gerendert (State bleibt erhalten, Prompt erscheint nach PiP-Ende), und der Teilnehmer meldet IDLE statt SELECTING_SOURCE, damit er den Auto-Resume der anderen nicht bis zum Timeout blockiert.

- [ ] **Step 1: Failing Test schreiben** (`WatchPartyPromptDeferralTest.kt`, kompletter Dateiinhalt)

```kotlin
package com.nuvio.app.features.watchparty

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PiP prompt deferral relies on the session's following flag: while a room-move
 * prompt cannot be answered (PiP), the participant reports IDLE instead of
 * SELECTING_SOURCE so the all-ready auto-resume never waits for them.
 */
class WatchPartyPromptDeferralTest {

    @Test
    fun deferredPromptReportsIdleInsteadOfSelectingSource() = runBlocking {
        var now = 1_000_000L
        val client = FakeWatchPartyRoom().client()
        val session = WatchPartySession(
            client = client,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            nowMs = { now },
            actorId = "actor-a",
            driftTickIntervalMs = 3_600_000L,
            presenceMinGapMs = 0L,
            presenceMaxPerWindow = 100,
        )
        session.join("ABCDEF", "Anna")
        session.setFollowing(true)
        session.onContentChanged(WatchPartyContentId("tt1", "movie"))
        session.onPlaybackSnapshot(WatchPartyPlaybackSnapshot(isPlaying = true, positionMs = 0L, isBuffering = false))

        // The room moves to different content -> engine flags SELECTING_SOURCE.
        now += 100
        client.emitState(
            WatchPartyRoomState(
                contentId = WatchPartyContentId("tt2", "movie"),
                isPlaying = false,
                positionMs = 0L,
                atWallClockMs = now,
                actorId = "actor-b",
                seq = 10L,
                reason = WatchPartyStateReason.CONTENT_CHANGE,
            ),
        )
        assertEquals(WatchPartyParticipantStatus.SELECTING_SOURCE, client.currentPresence?.status)

        // Prompt deferred (PiP): unfollow maps the status to IDLE immediately.
        session.setFollowing(false)
        assertEquals(WatchPartyParticipantStatus.IDLE, client.currentPresence?.status)
    }
}
```

- [ ] **Step 2: Test laufen lassen**

```bash
./gradlew :composeApp:testAndroidHostTest --tests "com.nuvio.app.features.watchparty.WatchPartyPromptDeferralTest"
```
Erwartung: PASS, falls die Session-Semantik bereits trägt (das ist der dokumentierende Fixpunkt des Verhaltens); schlägt er fehl, ist die `mappedStatus`-Annahme falsch und muss VOR dem UI-Wiring geklärt werden — nicht weiterbauen.

- [ ] **Step 3: Overlay-Gate** (`PlayerScreenRuntimeUi.kt`)

Die in Task 4 eingefügte Zeile `RenderWatchPartyOverlays()` ersetzen durch:

```kotlin
        // PiP shows only the video surface: prompts/panel/toasts stay in state
        // and reappear when the user expands back to full screen.
        if (!isInPip) {
            RenderWatchPartyOverlays()
        }
```
(`isInPip` existiert in `RenderPlayerRuntimeUi` bereits via `rememberIsInPictureInPicture()` — dieselbe Variable, die `visible = … && !isInPip` für die Controls nutzt.)

- [ ] **Step 4: Presence-Wiring** (`PlayerScreenRuntimeWatchPartyActions.kt`)

In `BindWatchPartyEffects()`, direkt nach dem Away-`DisposableEffect` aus Task 8 einfügen:

```kotlin
    // Deferred prompts (PiP): report IDLE instead of SELECTING_SOURCE so the
    // room's all-ready auto-resume never waits for someone who cannot answer.
    val isInPip = rememberIsInPictureInPicture()
    LaunchedEffect(isInPip, watchPartyContentPrompt, watchPartyMoveRoomPrompt, session) {
        val active = session ?: return@LaunchedEffect
        if (isInPip && (watchPartyContentPrompt != null || watchPartyMoveRoomPrompt != null)) {
            active.setFollowing(false)
        }
    }
```
(Kein Import nötig — `rememberIsInPictureInPicture` liegt im selben Paket `features.player`.)

- [ ] **Step 5: Volle Suite + Commit**

```bash
./gradlew :composeApp:testAndroidHostTest
git add -A composeApp/src
git commit -m "$(cat <<'EOF'
feat: defer watch party prompts in picture-in-picture

Overlays are not rendered while in PiP (prompt state survives and shows on
expand), and a participant with a pending prompt reports IDLE instead of
SELECTING_SOURCE so the room's coordinated start never waits for them.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

---

## Gesamtverifikation (manuell, Android-Emulator/Gerät + Desktop-Client)

Definition of done laut Grilling: **Android verifiziert, iOS vollständig mitgeschrieben, aber ungetestet.**

- [ ] Lobby: Home-Icon sichtbar (nur mit Config), Raum erstellen, Code kopieren.
- [ ] Cross-Plattform: Desktop-Client (NuvioDesktop-dev, gleiche Supabase-Config) joint denselben Raum; Play/Pause/Seek synchronisieren in beide Richtungen.
- [ ] Banner: außerhalb des Players sichtbar bei aktiver Session; Klick auf „Go to playback" folgt zum Raum-Content.
- [ ] Follow: Desktop wechselt Episode → Mobile folgt in-player; Desktop wechselt Titel → Mobile launcht (bzw. Prompt-Verhalten bei Deviation).
- [ ] Rejoin: App-Neustart → Lobby zeigt „Rejoin …" mit Occupancy-Count.
- [ ] Profilwechsel: aktive Party + Profilwechsel → andere Clients sehen den Leave; kein Auto-Rejoin.
- [ ] Away: Mobile backgrounden (ohne PiP) → Desktop läuft weiter, Mobile-Teilnehmer wird IDLE/verschwindet; Mobile in den Vordergrund → Position springt auf Raum-Stand.
- [ ] PiP: Party in PiP, Desktop wechselt Titel → Desktop-Raum startet ohne Warten auf Mobile; PiP aufklappen → Prompt erscheint.
- [ ] **Offene iOS-Punkte (dokumentieren, nicht blockieren):** Socket-Suspension/Resume-Realign auf echtem Gerät, NSUserDefaults-Persistenz, CMP-Lifecycle-Mapping (ON_STOP beim Backgrounden).

## Ausblick (bewusst NICHT Teil dieses Plans)

- Formfaktor-Feinschliff von Panel (570 LOC) und Lobby (565 LOC) auf Phones.
- Übersetzungen der 42 Strings in weitere Locales.
- iOS-Verifikation auf echter Hardware.
