# मराठी संपर्क (Marathi Contacts) — Architecture Design

**Status:** Approved, pending implementation plan.
**Related docs:** `docs/Requirement.md` (product spec, screens, decided edge cases), `docs/UseCase-Install-And-Translate.md` (UC1–UC9, Cockburn-format use cases), `docs/design/Swarra Anande Static Gallery/Marathi Contacts App.dc.html` (visual design source — exported Claude Design canvas, 11 artboards covering all screens/sub-states).

This spec covers *how* the app is built. It does not re-derive product decisions already settled in the two docs above — it assumes them as given and only makes them concrete in code terms.

## Goals

- Implement UC1–UC3 and UC5–UC9 (UC4 was dropped — no in-app "re-check for new contacts," deferred to a future background-sync phase).
- Foreground-only translate/rollback runs, backed by a persisted status model and per-contact ledger, both defined in Requirement.md's Technical Design Notes.
- Senior-citizen UI constraints from Requirement.md (large Marathi text, ≥64dp touch targets, one primary action per screen, no hidden gestures) apply to every screen built here.

## Non-goals (explicitly out of scope for this build)

- Background WorkManager sync for newly-added contacts (future phase).
- True brand/business-name detection for translation eligibility (see "Known limitation" below).
- Any cloud/network dependency for transliteration or anything else — the app is fully offline.

## Tech stack

| Concern | Choice | Why |
|---|---|---|
| UI | Jetpack Compose (Material 3) | Simple, state-driven screens; strong large-text/accessibility support; current Android standard. |
| Navigation | Single Activity + Navigation Compose | Standard pairing with Compose; start destination driven by `AppRouter`. |
| Ledger persistence | Room | Structured, queryable per-contact data (count-by-status, etc.) benefits from real SQL over a flat file. |
| App status persistence | Jetpack DataStore (Preferences) | A handful of key-value fields (`direction`, `phase`, `permissionRequestedBefore`) — simpler than Room for this, modern replacement for SharedPreferences. |
| Dependency wiring | Manual DI (`AppContainer`) | App is small enough that a hand-written container is simpler than a code-gen DI framework. |
| Async | Kotlin Coroutines + Flow | `viewModelScope`-scoped coroutine drives the run loop; Flow exposes progress/state to Compose. |
| Transliteration | `android.icu.text.Transliterator` ("Latin-Devanagari") | Offline, no network dependency, no cost, ships with Android — see the earlier "what technology for translation" discussion (ML Kit rejected: it's a translation engine, not a transliteration engine, and mistranslates proper nouns). |
| Contacts test strategy | Robolectric | Runs `ContentResolver`/`ContactsProvider` against a shadow provider as fast local JVM tests — no emulator needed. |
| Min/target/compile SDK | 26 / 34 / 34 (unchanged) | Already set in `app/build.gradle.kts`. |

## Package structure

```
com.sampark
├─ data
│   ├─ contacts/
│   │   ├─ ContactsRepository.kt        (interface)
│   │   ├─ AndroidContactsRepository.kt (ContentResolver-backed impl)
│   │   └─ ContactEligibility.kt        (Requirement.md's skip-list rules)
│   ├─ ledger/
│   │   ├─ LedgerEntity.kt
│   │   ├─ LedgerDao.kt
│   │   └─ LedgerDatabase.kt            (Room)
│   └─ status/
│       └─ AppStatusRepository.kt       (DataStore-backed: direction, phase, permissionRequestedBefore)
├─ domain
│   ├─ TransliterationEngine.kt         (wraps ICU4J Latin→Devanagari)
│   ├─ RunEngine.kt                     (shared translate/rollback run loop, parameterized by direction)
│   └─ AppRouter.kt                     (resolves start destination: permission → direction/phase → ledger)
├─ ui
│   ├─ screens/
│   │   ├─ WelcomeScreen.kt
│   │   ├─ PermissionExplainerScreen.kt
│   │   ├─ RunProgressScreen.kt         (shared: translate + rollback, driven by direction param)
│   │   ├─ CompletionScreen.kt
│   │   ├─ HomeScreen.kt                (both states: offer-translate / offer-rollback, per direction+phase)
│   │   ├─ RollbackConfirmScreen.kt
│   │   └─ PermissionDeniedScreen.kt
│   ├─ navigation/
│   │   └─ SamparkNavHost.kt
│   └─ theme/
│       └─ Theme.kt, Type.kt            (palette/type scale from the design file — see "Visual design" below)
├─ AppContainer.kt                      (manual DI root)
└─ MainActivity.kt
```

## Visual design

Source of truth: `docs/design/Swarra Anande Static Gallery/Marathi Contacts App.dc.html` (exported Claude Design canvas). All 9 screens plus the running/paused sub-states are fully designed there — this is implemented as-is, not re-interpreted.

**Palette** (OKLCH):
- Background: `oklch(97% 0.015 75)` — warm, near-white throughout.
- Primary text: `oklch(24% 0.02 50)` (headings), `oklch(38% 0.02 50)` (body).
- Translate-direction actions (Welcome/Start, Continue, Translate Again, OK, Go to Settings): amber `oklch(56% 0.15 45)`.
- Rollback-direction actions (Bring Back English, Yes-revert-it): rose `oklch(55% 0.10 350)`.
- Resume (either direction): soft amber-orange `oklch(72% 0.14 70)` — deliberately not the same as the primary translate amber, and never red, since resuming isn't an error state.
- Success/Home-Marathi-state icon accent: green `oklch(55% 0.11 150)`.
- Secondary/outline buttons: transparent fill, `oklch(85% 0.02 50)` border, `oklch(45% 0.02 50)` text.

**Typography:** Noto Sans Devanagari (400/500/600/700), loaded via Google Fonts. Headings ~26–34px, body ~19–21px, primary button label ~22–25px — all comfortably above Requirement.md's 20sp/28sp minimums.

**Components:**
- Primary buttons: full-width pill, ~76px tall, 22px corner radius, drop shadow tinted to the button's own color.
- Secondary/outline buttons: ~56px tall, 18px corner radius, transparent background, thin border, no shadow — clearly lower visual weight, per the "no more than one prominent button" rule.
- Progress indicator: a **circular** conic-gradient ring with the percentage centered inside it (not a linear bar) — `RunProgressScreen`'s progress UI should be built as a circular ring, not `LinearProgressIndicator`.
- Icons are simple custom illustrations built from basic shapes (circles, rounded rects) tinted to match each screen's accent color, not standard Material icon glyphs — a friendly, non-technical visual language consistent with the "icon/illustration-first" requirement.
- The Translating/Rollback-running screens include a live **before → after name preview card** ("Swarra Anande → स्वारा आनंदे") beneath the status text — a nice concrete reassurance detail not called out in Requirement.md; `RunProgressScreen` should show the most-recently-processed contact's original/translated name pair here, sourced from the ledger.

**Two conflicts between the design file and decisions made after it was created — resolved in favor of the later decisions:**
- The design's Home/Marathi-state screen (4a) still shows the secondary "पुन्हा तपासा" (re-check) link — **omit this**; UC4 was dropped, new-contact detection is deferred to a future phase.
- The design's Rollback-paused screen (6b) still shows a "रद्द करा" (Cancel) button alongside Resume — **omit this**; rollback has no Cancel option (see UC2/UC6/UC7). The paused-rollback screen renders as a single centered "सुरू ठेवा" button only, not the two-button stack shown in the design file.

## Data layer

### Ledger (Room)

```kotlin
@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey val lookupKey: String,
    val originalName: String,
    val translatedName: String,
    val status: LedgerStatus // PENDING | TRANSLATED | ROLLED_BACK | SKIPPED_EXTERNAL_EDIT
)
```

`LedgerDao` exposes: `insertAll(rows)`, `updateStatus(lookupKey, status)`, `countByStatus(status): Flow<Int>`, `getRowsByStatus(status): List<LedgerEntity>`, `clearAll()` (used when a fresh translate cycle replaces the ledger).

**Lifecycle (per Requirement.md):** a translate run always starts by clearing the table and inserting one `PENDING` row per currently-eligible contact (frozen snapshot). Rollback never inserts rows — it only reads/updates existing ones.

### App status (DataStore)

Three preference keys: `direction` (`NONE | TRANSLATE | ROLLBACK`), `phase` (`RUNNING | COMPLETED`), `permissionRequestedBefore` (`Boolean`). `AppStatusRepository` exposes each as a `Flow` plus suspend setters.

### Contacts (ContentResolver)

`ContactsRepository` interface (unchanged in shape from the earlier build): read all contacts with name + account type, write a new display name for a given `lookupKey`, read current name by `lookupKey` (used by rollback's drift check — see below), exclude SIM-only/WhatsApp-only account types.

`ContactEligibility` implements Requirement.md's skip-list as a pure function `isEligibleForTranslation(name: String): Boolean`:
- Skip if: no Latin characters (already Devanagari/other Indic script), blank, purely numeric, symbols/emoji-only, all-caps (acronym heuristic), mixed-script, leetspeak-pattern (heuristic: Latin letters interspersed with digits/symbols mid-word), single letter/bare initials.
- **Known limitation:** true brand/business-name detection (e.g., "Pizza Hut") has no reliable algorithmic rule without a maintained dictionary — deliberately out of scope for this build. Such names will currently pass the eligibility filter and get transliterated like any personal name. Flagged, not silently pretended away.

## Domain layer

### TransliterationEngine
Thin wrapper around `Transliterator.getInstance("Latin-Devanagari")`. One method: `transliterate(name: String): String`.

### RunEngine
Owns the per-contact loop for both directions:
- **Translate:** for each `PENDING` row, transliterate `originalName` → write to contact via `ContactsRepository`, update ledger row to `TRANSLATED`.
- **Rollback:** for each `TRANSLATED` row, look up the contact's current name by `lookupKey`.
  - Not found → mark `SKIPPED_EXTERNAL_EDIT` (UC9, extension 3b — deleted contact treated identically to a name mismatch).
  - Found, current name ≠ `translatedName` → mark `SKIPPED_EXTERNAL_EDIT` (UC9, extension 3a).
  - Found, current name = `translatedName` → write `originalName` back, mark `ROLLED_BACK`.
- Exposes progress as `Flow<RunProgress>` (`done`, `total`).
- Runs inside `viewModelScope` in `RunViewModel`; pausing cancels the coroutine job (in-memory only — `phase` stays `RUNNING`, nothing persisted, per the "paused is never a persisted state" decision). Cancel (translate only — the UI simply never renders a Cancel button when `direction = ROLLBACK`) writes `phase = COMPLETED` via `AppStatusRepository` immediately.

### AppRouter
Single function, `resolveStartDestination(): Screen`, implementing UC1/UC2/UC3/UC5/UC8's combined routing logic:
1. Check live contacts permission. Not granted → `permissionRequestedBefore` false → Welcome; true → PermissionDenied.
2. Permission granted, `direction = NONE` → Welcome.
3. `direction = TRANSLATE, phase = RUNNING` → RunProgress(TRANSLATE) (renders as paused-prompt per UC6/UC8, since no live job exists in a fresh process).
4. `direction = TRANSLATE, phase = COMPLETED` → Home (offer-rollback state).
5. `direction = ROLLBACK, phase = RUNNING` → RunProgress(ROLLBACK) (paused-prompt, Resume-only).
6. `direction = ROLLBACK, phase = COMPLETED` → Home (offer-translate-again state).

## UI layer

Seven Compose screens (UC4's "re-check" screen removed), built from the design file per the "Visual design" section above — including its two adjustments (no re-check link on Home, no Cancel button on Rollback-paused). `RunProgressScreen` is shared between translate and rollback, parameterized by `direction: Direction`, rendering the Cancel button only when `direction == TRANSLATE` (per the pause/cancel asymmetry decided earlier) and using the design's accent color for that direction (amber for translate, rose for rollback). `HomeScreen` renders one of two states based on `direction`/`phase` from `AppStatusRepository`. Navigation start destination comes from `AppRouter.resolveStartDestination()` at `MainActivity` launch.

## Error handling / edge cases (traceability to decisions already made)

| Case | Handling | Reference |
|---|---|---|
| Permission denied or revoked (including mid-run) | Checked live before consulting status; mid-run revocation treated as a pause, no distinct failure path | UC3 |
| App killed/backgrounded mid-run | `AppRouter` finds `phase = RUNNING` with no live job → shows paused-prompt, never auto-resumes silently | UC6, UC8 |
| Cancel mid-translate | `phase = COMPLETED` written early; partial ledger state stands | UC7 |
| Cancel mid-rollback | Not possible — no Cancel button renders for this direction | UC2, UC6, UC7 |
| Contact edited after translation, before rollback | Drift check compares current name vs. `translatedName`; mismatch → `SKIPPED_EXTERNAL_EDIT`, never overwritten | UC9 |
| Contact deleted after translation, before rollback | Same outcome as a name mismatch — `SKIPPED_EXTERNAL_EDIT`, no error | UC9 |
| Zero contacts to roll back | Rollback proceeds and completes immediately; no precondition guard needed | UC2 |
| Aggregated contacts (multiple raw contacts per visible Contact) | Ledger keys and drift-compares on the aggregate `lookupKey` / aggregate display name, not per-raw-contact — accepted trade-off | Requirement.md |
| Read-then-write race during drift check | Accepted risk, not engineered around (single-user personal-contacts app) | Requirement.md |

## Testing strategy

- **Robolectric:** `AndroidContactsRepository` — name read/write, SIM/WhatsApp account exclusion.
- **Plain JUnit:** `TransliterationEngine`, `ContactEligibility` (one case per skip-rule), `AppRouter` (one case per routing branch above), `RunEngine`'s ledger status transitions (using an in-memory Room database), `AppStatusRepository` transitions.
- **Compose UI tests:** critical path (Welcome → Permission → RunProgress → Completion → Home) using `androidx.compose.ui.test`.

## Next step

Hand off to the `writing-plans` skill to turn this into a step-by-step implementation plan.
