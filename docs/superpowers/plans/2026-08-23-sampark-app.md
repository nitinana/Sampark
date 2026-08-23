# Sampark (मराठी संपर्क) App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the मराठी संपर्क Android app from the current empty skeleton: translate English contact names to Marathi (with full rollback), following the Compose + Room + DataStore architecture already approved.

**Architecture:** Three layers — `data` (ContentResolver-backed contacts repo, Room ledger, DataStore app-status), `domain` (transliteration, the shared translate/rollback run engine, routing), `ui` (Compose screens built from the design file, one shared ViewModel per run). Manual DI via a single `AppContainer`. No network, no background service in this phase.

**Tech Stack:** Kotlin 1.9.25, Jetpack Compose (Material 3) + Navigation Compose, Room (KSP), Jetpack DataStore (Preferences), Kotlin Coroutines/Flow, `android.icu.text.Transliterator`, Robolectric for all local tests (including Compose UI tests — no emulator).

**Spec:** `docs/superpowers/specs/2026-08-23-sampark-architecture-design.md` (also read `docs/Requirement.md` and `docs/UseCase-Install-And-Translate.md`, which the spec references for the *why* behind each decision below).

## Global Constraints

- All UI text in Marathi (Devanagari), body ≥20sp, headings ≥28sp, high contrast — per Requirement.md.
- Touch targets ≥64dp; the design file's primary buttons are 76px tall, secondary 56px tall — use those exact sizes.
- Exactly one primary (visually prominent) action per screen; secondary actions render clearly lower-weight.
- No swipe gestures, no long-press, no hamburger menus anywhere.
- App is fully offline — no network calls anywhere in this codebase.
- `minSdk 26`, `compileSdk 34`, `targetSdk 34` (unchanged, already set).
- Rollback has **no Cancel option** — only Pause/Resume. Cancel exists for translate only.
- "Paused" is never persisted — it's the UI rendering `phase = RUNNING` with no live job in the current process. Never auto-resume a run without a fresh explicit user tap.
- A translate run always starts by clearing the ledger and inserting one row per currently-eligible contact (frozen snapshot, no re-check for mid-run additions).
- Ledger keys and drift-compares on the aggregate `lookupKey` (not per-raw-contact) — accepted trade-off, not to be "fixed" mid-implementation.
- Permission is checked live, every time, before consulting any persisted status.

---

## Task 1: Project dependencies and build configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (root)

**Interfaces:**
- Produces: a compiling `app` module with Compose, Room (KSP), DataStore, Navigation Compose, and test dependencies available to every later task.

- [ ] **Step 1: Add the KSP plugin to the root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
}
```

- [ ] **Step 2: Rewrite `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.sampark"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sampark"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 3: Verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (no source files reference the new dependencies yet, so this just confirms the configuration resolves).

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "build: add Compose, Room, DataStore, Navigation, and test dependencies"
```

---

## Task 2: Contact translation eligibility rules

**Files:**
- Create: `app/src/main/java/com/sampark/data/contacts/ContactEligibility.kt`
- Test: `app/src/test/java/com/sampark/data/contacts/ContactEligibilityTest.kt`

**Interfaces:**
- Produces: `object ContactEligibility { fun isEligibleForTranslation(name: String): Boolean }` — consumed by Task 6 (`AndroidContactsRepository`).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sampark.data.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEligibilityTest {

    @Test
    fun `plain English name is eligible`() {
        assertTrue(ContactEligibility.isEligibleForTranslation("Nitin Anande"))
    }

    @Test
    fun `already Devanagari name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("नितीन आनंदे"))
    }

    @Test
    fun `blank name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation(""))
        assertFalse(ContactEligibility.isEligibleForTranslation("   "))
    }

    @Test
    fun `purely numeric name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("9876543210"))
    }

    @Test
    fun `symbols-only name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("---"))
    }

    @Test
    fun `mixed-script name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("Suresh शर्मा"))
    }

    @Test
    fun `non-English Latin-script name with diacritics is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("Renee"))
        assertFalse(ContactEligibility.isEligibleForTranslation("René"))
    }

    @Test
    fun `all-caps acronym is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("IT Support"))
        assertFalse(ContactEligibility.isEligibleForTranslation("HR"))
    }

    @Test
    fun `bare initials are not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("J"))
        assertFalse(ContactEligibility.isEligibleForTranslation("R.K."))
    }

    @Test
    fun `leetspeak nickname is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("K@ran"))
        assertFalse(ContactEligibility.isEligibleForTranslation("Ash_ley"))
    }

    @Test
    fun `URL-like name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("www.example.com"))
    }

    @Test
    fun `ordinary two-word name with a period is still eligible`() {
        assertTrue(ContactEligibility.isEligibleForTranslation("Nitin Anande"))
        assertTrue(ContactEligibility.isEligibleForTranslation("Swarra Anande"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.contacts.ContactEligibilityTest"`
Expected: FAIL — `ContactEligibility` is unresolved.

- [ ] **Step 3: Implement `ContactEligibility`**

```kotlin
package com.sampark.data.contacts

/**
 * Requirement.md "Contact eligibility for translation" — every skip rule
 * decided there, implemented as one pure function.
 */
object ContactEligibility {

    private val urlOrEmailPattern = Regex(
        "(https?://|www\\.|[a-zA-Z0-9.+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    )

    fun isEligibleForTranslation(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (!containsAsciiLatinLetter(trimmed)) return false
        if (containsNonAsciiLetter(trimmed)) return false
        if (urlOrEmailPattern.containsMatchIn(trimmed)) return false
        if (isAllCapsAcronym(trimmed)) return false
        if (isBareInitials(trimmed)) return false
        if (looksLeetspeak(trimmed)) return false
        return true
    }

    private fun containsAsciiLatinLetter(s: String): Boolean =
        s.any { it in 'A'..'Z' || it in 'a'..'z' }

    /**
     * Catches already-Devanagari/other-Indic-script letters (mixed-script
     * names) AND accented Latin letters like "é"/"ñ" (non-English
     * Latin-script names) — both are letters but outside plain ASCII A-Z/a-z,
     * which is exactly the line Requirement.md drew for "skip, don't guess."
     */
    private fun containsNonAsciiLetter(s: String): Boolean =
        s.any { ch -> ch.isLetter() && ch !in 'A'..'Z' && ch !in 'a'..'z' }

    private fun isAllCapsAcronym(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.length >= 2 && letters.all { it.isUpperCase() }
    }

    private fun isBareInitials(s: String): Boolean {
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.all { token -> token.trim('.', ',').length <= 1 }
    }

    private fun looksLeetspeak(s: String): Boolean {
        val tokens = s.split(Regex("\\s+"))
        return tokens.any { token ->
            val hasLetter = token.any { it.isLetter() }
            val hasDigitOrSymbol = token.any { it.isDigit() || it in "@_$" }
            hasLetter && hasDigitOrSymbol
        }
    }
}
```

Note the `"IT Support"` case: `isAllCapsAcronym` filters letters from the *whole string*, so `"ITSupport"` → not all-uppercase → would pass. But the test uses `"IT Support"` with a space — `looksLeetspeak`/`isAllCapsAcronym` operate differently; verify against the actual test: `isAllCapsAcronym("IT Support")` — letters = "ITSupport", not all upper (contains lowercase "upport") → **would return false, marking it eligible**, contradicting the test. Fix: acronym detection must be per-token, not whole-string. Use this corrected version instead:

```kotlin
    private fun isAllCapsAcronym(s: String): Boolean {
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.any { token ->
            val letters = token.filter { it.isLetter() }
            letters.length >= 2 && letters.all { it.isUpperCase() }
        }
    }
```

With this fix, `"IT Support"` → token `"IT"` is a 2-letter all-caps token → not eligible. `"HR"` → single all-caps token → not eligible. `"Nitin Anande"` → no all-caps token → unaffected. Use this corrected `isAllCapsAcronym`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.contacts.ContactEligibilityTest"`
Expected: PASS (all 12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/data/contacts/ContactEligibility.kt app/src/test/java/com/sampark/data/contacts/ContactEligibilityTest.kt
git commit -m "feat: add contact translation eligibility rules"
```

---

## Task 3: Transliteration engine

**Files:**
- Create: `app/src/main/java/com/sampark/domain/TransliterationEngine.kt`
- Test: `app/src/test/java/com/sampark/domain/TransliterationEngineTest.kt`

**Interfaces:**
- Produces: `class TransliterationEngine { fun transliterate(name: String): String }` — consumed by Task 7 (`RunEngine`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class TransliterationEngineTest {

    private val engine = TransliterationEngine()

    @Test
    fun `transliterates a simple English name to Devanagari`() {
        val result = engine.transliterate("Nitin")
        assertNotEquals("Nitin", result)
        assertTrue(result.isNotBlank())
        assertTrue(result.any { it.code in 0x0900..0x097F }) // Devanagari Unicode block
    }

    @Test
    fun `transliteration is deterministic for the same input`() {
        assertEquals(engine.transliterate("Nitin Anande"), engine.transliterate("Nitin Anande"))
    }
}
```

(Add `import org.junit.Assert.assertTrue` alongside the other JUnit imports.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.domain.TransliterationEngineTest"`
Expected: FAIL — `TransliterationEngine` is unresolved.

- [ ] **Step 3: Implement `TransliterationEngine`**

```kotlin
package com.sampark.domain

import android.icu.text.Transliterator

class TransliterationEngine {
    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Latin-Devanagari")
    }

    fun transliterate(name: String): String = transliterator.transliterate(name)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.domain.TransliterationEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/domain/TransliterationEngine.kt app/src/test/java/com/sampark/domain/TransliterationEngineTest.kt
git commit -m "feat: add ICU-backed transliteration engine"
```

---

## Task 4: Ledger (Room)

**Files:**
- Create: `app/src/main/java/com/sampark/data/ledger/LedgerStatus.kt`
- Create: `app/src/main/java/com/sampark/data/ledger/LedgerEntity.kt`
- Create: `app/src/main/java/com/sampark/data/ledger/LedgerDao.kt`
- Create: `app/src/main/java/com/sampark/data/ledger/LedgerDatabase.kt`
- Test: `app/src/test/java/com/sampark/data/ledger/LedgerDaoTest.kt`

**Interfaces:**
- Produces: `enum class LedgerStatus { PENDING, TRANSLATED, ROLLED_BACK, SKIPPED_EXTERNAL_EDIT }`, `data class LedgerEntity(val lookupKey: String, val originalName: String, val translatedName: String, val status: LedgerStatus)`, `interface LedgerDao` with `insertAll`, `updateStatus`, `countAll`, `countByStatus`, `countByStatuses`, `getRowsByStatus`, `clearAll`. `LedgerDatabase.getInstance(context): LedgerDatabase`. Consumed by Task 7 (`RunEngine`), Task 14/16 (`RunViewModel`, progress display).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.data.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LedgerDaoTest {

    private fun createDao(): LedgerDao {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        return db.ledgerDao()
    }

    @Test
    fun `insertAll then countAll reflects inserted rows`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "स्वारा", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "निनान", LedgerStatus.PENDING)
            )
        )
        assertEquals(2, dao.countAll().first())
    }

    @Test
    fun `updateStatus moves a row from PENDING to TRANSLATED`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.PENDING)))
        dao.updateStatus("key1", LedgerStatus.TRANSLATED)
        assertEquals(1, dao.countByStatus(LedgerStatus.TRANSLATED).first())
        assertEquals(0, dao.countByStatus(LedgerStatus.PENDING).first())
    }

    @Test
    fun `getRowsByStatus returns only matching rows`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED),
                LedgerEntity("key2", "Swarra", "स्वारा", LedgerStatus.PENDING)
            )
        )
        val translated = dao.getRowsByStatus(LedgerStatus.TRANSLATED)
        assertEquals(1, translated.size)
        assertEquals("key1", translated[0].lookupKey)
    }

    @Test
    fun `countByStatuses sums matching statuses`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED),
                LedgerEntity("key2", "Swarra", "स्वारा", LedgerStatus.ROLLED_BACK),
                LedgerEntity("key3", "Amit", "अमित", LedgerStatus.PENDING)
            )
        )
        val count = dao.countByStatuses(listOf(LedgerStatus.TRANSLATED, LedgerStatus.ROLLED_BACK)).first()
        assertEquals(2, count)
    }

    @Test
    fun `clearAll removes every row`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.PENDING)))
        dao.clearAll()
        assertEquals(0, dao.countAll().first())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.ledger.LedgerDaoTest"`
Expected: FAIL — none of the ledger classes exist yet.

- [ ] **Step 3: Implement the ledger classes**

```kotlin
// LedgerStatus.kt
package com.sampark.data.ledger

enum class LedgerStatus { PENDING, TRANSLATED, ROLLED_BACK, SKIPPED_EXTERNAL_EDIT }
```

```kotlin
// LedgerEntity.kt
package com.sampark.data.ledger

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey val lookupKey: String,
    val originalName: String,
    val translatedName: String,
    val status: LedgerStatus
)
```

```kotlin
// LedgerDao.kt
package com.sampark.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert
    suspend fun insertAll(rows: List<LedgerEntity>)

    @Query("UPDATE ledger SET status = :status WHERE lookupKey = :lookupKey")
    suspend fun updateStatus(lookupKey: String, status: LedgerStatus)

    @Query("SELECT COUNT(*) FROM ledger")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ledger WHERE status = :status")
    fun countByStatus(status: LedgerStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM ledger WHERE status IN (:statuses)")
    fun countByStatuses(statuses: List<LedgerStatus>): Flow<Int>

    @Query("SELECT * FROM ledger WHERE status = :status")
    suspend fun getRowsByStatus(status: LedgerStatus): List<LedgerEntity>

    @Query("DELETE FROM ledger")
    suspend fun clearAll()
}
```

```kotlin
// LedgerDatabase.kt
package com.sampark.data.ledger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class LedgerStatusConverters {
    @TypeConverter
    fun fromStatus(status: LedgerStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): LedgerStatus = LedgerStatus.valueOf(value)
}

@Database(entities = [LedgerEntity::class], version = 1, exportSchema = false)
@TypeConverters(LedgerStatusConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "ledger.db"
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.ledger.LedgerDaoTest"`
Expected: PASS (all 5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/data/ledger/ app/src/test/java/com/sampark/data/ledger/
git commit -m "feat: add Room-backed contact ledger"
```

---

## Task 5: App status (DataStore)

**Files:**
- Create: `app/src/main/java/com/sampark/data/status/Direction.kt`
- Create: `app/src/main/java/com/sampark/data/status/Phase.kt`
- Create: `app/src/main/java/com/sampark/data/status/AppStatusRepository.kt`
- Test: `app/src/test/java/com/sampark/data/status/AppStatusRepositoryTest.kt`

**Interfaces:**
- Produces: `enum class Direction { NONE, TRANSLATE, ROLLBACK }`, `enum class Phase { RUNNING, COMPLETED }`, `class AppStatusRepository(dataStore: DataStore<Preferences>)` exposing `direction: Flow<Direction>`, `phase: Flow<Phase>`, `permissionRequestedBefore: Flow<Boolean>`, `suspend fun setDirection(direction: Direction)`, `suspend fun setPhase(phase: Phase)`, `suspend fun setPermissionRequestedBefore(value: Boolean)`. Consumed by Task 8 (`AppRouter`), Task 14/16 (`RunViewModel`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.data.status

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppStatusRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): AppStatusRepository {
        val file = File(tempFolder.newFolder(), "app_status.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return AppStatusRepository(dataStore)
    }

    @Test
    fun `direction defaults to NONE`() = runTest {
        val repo = createRepository()
        assertEquals(Direction.NONE, repo.direction.first())
    }

    @Test
    fun `phase defaults to COMPLETED`() = runTest {
        val repo = createRepository()
        assertEquals(Phase.COMPLETED, repo.phase.first())
    }

    @Test
    fun `permissionRequestedBefore defaults to false`() = runTest {
        val repo = createRepository()
        assertEquals(false, repo.permissionRequestedBefore.first())
    }

    @Test
    fun `setDirection persists and is readable back`() = runTest {
        val repo = createRepository()
        repo.setDirection(Direction.TRANSLATE)
        assertEquals(Direction.TRANSLATE, repo.direction.first())
    }

    @Test
    fun `setPhase persists and is readable back`() = runTest {
        val repo = createRepository()
        repo.setPhase(Phase.RUNNING)
        assertEquals(Phase.RUNNING, repo.phase.first())
    }

    @Test
    fun `setPermissionRequestedBefore persists and is readable back`() = runTest {
        val repo = createRepository()
        repo.setPermissionRequestedBefore(true)
        assertEquals(true, repo.permissionRequestedBefore.first())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.status.AppStatusRepositoryTest"`
Expected: FAIL — none of the status classes exist yet.

- [ ] **Step 3: Implement the status classes**

```kotlin
// Direction.kt
package com.sampark.data.status

enum class Direction { NONE, TRANSLATE, ROLLBACK }
```

```kotlin
// Phase.kt
package com.sampark.data.status

enum class Phase { RUNNING, COMPLETED }
```

```kotlin
// AppStatusRepository.kt
package com.sampark.data.status

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppStatusRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val DIRECTION = stringPreferencesKey("direction")
        val PHASE = stringPreferencesKey("phase")
        val PERMISSION_REQUESTED_BEFORE = booleanPreferencesKey("permission_requested_before")
    }

    val direction: Flow<Direction> = dataStore.data.map { prefs ->
        prefs[Keys.DIRECTION]?.let { Direction.valueOf(it) } ?: Direction.NONE
    }

    val phase: Flow<Phase> = dataStore.data.map { prefs ->
        prefs[Keys.PHASE]?.let { Phase.valueOf(it) } ?: Phase.COMPLETED
    }

    val permissionRequestedBefore: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PERMISSION_REQUESTED_BEFORE] ?: false
    }

    suspend fun setDirection(direction: Direction) {
        dataStore.edit { it[Keys.DIRECTION] = direction.name }
    }

    suspend fun setPhase(phase: Phase) {
        dataStore.edit { it[Keys.PHASE] = phase.name }
    }

    suspend fun setPermissionRequestedBefore(value: Boolean) {
        dataStore.edit { it[Keys.PERMISSION_REQUESTED_BEFORE] = value }
    }
}
```

`Phase` defaults to `COMPLETED` rather than a null/absent state deliberately: `direction` defaults to `NONE`, and `AppRouter` (Task 8) only ever consults `phase` when `direction != NONE` — so the unread default value of `phase` is never actually observed at `direction = NONE`. `COMPLETED` is the safer default of the two if that invariant is ever violated by a bug, since it can never be mistaken for a live run.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.status.AppStatusRepositoryTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/data/status/ app/src/test/java/com/sampark/data/status/
git commit -m "feat: add DataStore-backed app status repository"
```

---

## Task 6: Contacts repository

**Files:**
- Create: `app/src/main/java/com/sampark/data/contacts/ContactsRepository.kt`
- Create: `app/src/main/java/com/sampark/data/contacts/AndroidContactsRepository.kt`
- Test: `app/src/test/java/com/sampark/data/contacts/AndroidContactsRepositoryTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ContactEligibility.isEligibleForTranslation(name: String): Boolean` (Task 2).
- Produces: `data class ContactRef(val lookupKey: String, val name: String)`, `interface ContactsRepository { fun hasContactsPermission(): Boolean; fun getEligibleContacts(): List<ContactRef>; fun getCurrentName(lookupKey: String): String?; fun updateName(lookupKey: String, newName: String) }`, `class AndroidContactsRepository(context: Context) : ContactsRepository`. Consumed by Task 7 (`RunEngine`), Task 8 (`AppRouter`).

- [ ] **Step 1: Add contacts permissions and restore the launcher activity declaration to the manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.WRITE_CONTACTS" />

    <application
        android:label="Sampark"
        android:theme="@style/Theme.Sampark">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

(`MainActivity` doesn't exist yet — this is fine, it's created in Task 17. The manifest change is grouped here because it's contacts-permission-specific and this task is what makes the permission meaningful.)

- [ ] **Step 2: Write the failing test**

```kotlin
package com.sampark.data.contacts

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidContactsRepositoryTest {

    private fun grantContactsPermission() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }

    private fun insertContact(displayName: String, accountType: String? = null, accountName: String? = null): String {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val resolver = context.contentResolver

        val rawContactValues = ContentValues().apply {
            if (accountType != null) put(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            if (accountName != null) put(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
        }
        val rawContactUri = resolver.insert(ContactsContract.RawContacts.CONTENT_URI, rawContactValues)!!
        val rawContactId = rawContactUri.lastPathSegment!!.toLong()

        val nameValues = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
        }
        resolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

        val cursor = resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )!!
        cursor.moveToFirst()
        val contactId = cursor.getLong(0)
        cursor.close()

        val lookupCursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null
        )!!
        lookupCursor.moveToFirst()
        val lookupKey = lookupCursor.getString(0)
        lookupCursor.close()

        return lookupKey
    }

    @Test
    fun `hasContactsPermission is false without permission`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        assertFalse(repository.hasContactsPermission())
    }

    @Test
    fun `hasContactsPermission is true once granted`() {
        grantContactsPermission()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        assertTrue(repository.hasContactsPermission())
    }

    @Test
    fun `getEligibleContacts includes an eligible English name`() {
        grantContactsPermission()
        insertContact("Nitin Anande")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        val contacts = repository.getEligibleContacts()
        assertTrue(contacts.any { it.name == "Nitin Anande" })
    }

    @Test
    fun `getEligibleContacts excludes a SIM-only contact`() {
        grantContactsPermission()
        insertContact("Sim Contact", accountType = "vnd.sec.contact.sim", accountName = "SIM")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        val contacts = repository.getEligibleContacts()
        assertFalse(contacts.any { it.name == "Sim Contact" })
    }

    @Test
    fun `getEligibleContacts excludes an already-Devanagari name`() {
        grantContactsPermission()
        insertContact("नितीन आनंदे")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        val contacts = repository.getEligibleContacts()
        assertFalse(contacts.any { it.name == "नितीन आनंदे" })
    }

    @Test
    fun `updateName changes the contact display name`() {
        grantContactsPermission()
        val lookupKey = insertContact("Nitin Anande")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)

        repository.updateName(lookupKey, "निनान आनंदे")

        assertEquals("निनान आनंदे", repository.getCurrentName(lookupKey))
    }

    @Test
    fun `getCurrentName returns null for an unknown lookup key`() {
        grantContactsPermission()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        assertNull(repository.getCurrentName("does-not-exist"))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.contacts.AndroidContactsRepositoryTest"`
Expected: FAIL — `ContactsRepository`/`AndroidContactsRepository`/`ContactRef` are unresolved.

- [ ] **Step 4: Implement the contacts repository**

```kotlin
// ContactsRepository.kt
package com.sampark.data.contacts

data class ContactRef(val lookupKey: String, val name: String)

interface ContactsRepository {
    fun hasContactsPermission(): Boolean
    fun getEligibleContacts(): List<ContactRef>
    fun getCurrentName(lookupKey: String): String?
    fun updateName(lookupKey: String, newName: String)
}
```

```kotlin
// AndroidContactsRepository.kt
package com.sampark.data.contacts

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

private val EXCLUDED_ACCOUNT_TYPES = setOf(
    "vnd.sec.contact.sim",
    "com.android.contacts.sim",
    "com.whatsapp",
    "com.whatsapp.w4b"
)

class AndroidContactsRepository(private val context: Context) : ContactsRepository {

    override fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    override fun getEligibleContacts(): List<ContactRef> {
        val resolver = context.contentResolver
        val results = mutableListOf<ContactRef>()

        val cursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            null
        ) ?: return emptyList()

        cursor.use {
            val lookupKeyIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (it.moveToNext()) {
                val lookupKey = it.getString(lookupKeyIndex) ?: continue
                val name = it.getString(nameIndex) ?: continue
                if (isExcludedAccountType(lookupKey)) continue
                if (!ContactEligibility.isEligibleForTranslation(name)) continue
                results.add(ContactRef(lookupKey, name))
            }
        }
        return results
    }

    override fun getCurrentName(lookupKey: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val cursor = resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
            arrayOf(lookupKey),
            null
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
        }
    }

    override fun updateName(lookupKey: String, newName: String) {
        val resolver = context.contentResolver
        val rawContactId = findRawContactId(lookupKey) ?: return

        val values = ContentValues().apply {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
        }
        resolver.update(
            ContactsContract.Data.CONTENT_URI,
            values,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        )
    }

    private fun findRawContactId(lookupKey: String): Long? {
        val resolver = context.contentResolver
        val contactUri = ContactsContract.Contacts.lookupUri(
            resolver,
            android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        ) ?: return null

        val contactIdCursor = resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        ) ?: return null
        val contactId = contactIdCursor.use {
            if (!it.moveToFirst()) return null
            it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        }

        val rawCursor = resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        ) ?: return null
        return rawCursor.use {
            if (!it.moveToFirst()) null
            else it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
        }
    }

    private fun isExcludedAccountType(lookupKey: String): Boolean {
        val resolver = context.contentResolver
        val contactUri = ContactsContract.Contacts.lookupUri(
            resolver,
            android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        ) ?: return false

        val contactIdCursor = resolver.query(
            contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null
        ) ?: return false
        val contactId = contactIdCursor.use {
            if (!it.moveToFirst()) return false
            it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        }

        val rawCursor = resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        ) ?: return false

        return rawCursor.use {
            var allExcluded = it.count > 0
            while (it.moveToNext()) {
                val accountType = it.getString(it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE))
                if (accountType !in EXCLUDED_ACCOUNT_TYPES) allExcluded = false
            }
            allExcluded
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.data.contacts.AndroidContactsRepositoryTest"`
Expected: PASS (all 7 tests). If Robolectric's shadow `ContactsProvider` rejects the raw insert sequence used in the test helper, adjust `insertContact` to use `ContactsContract.RawContacts.CONTENT_URI` with `ContactsContract.RawContacts.ACCOUNT_TYPE`/`ACCOUNT_NAME` set to `null`/`null` for the default local case (already done above) — this is the standard Robolectric contacts-provider test pattern and should work as written with `robolectric:4.13`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sampark/data/contacts/ app/src/test/java/com/sampark/data/contacts/AndroidContactsRepositoryTest.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add ContentResolver-backed contacts repository"
```

---

## Task 7: Run engine (shared translate/rollback loop)

**Files:**
- Create: `app/src/main/java/com/sampark/domain/RunEngine.kt`
- Test: `app/src/test/java/com/sampark/domain/RunEngineTest.kt`

**Interfaces:**
- Consumes: `ContactsRepository` (Task 6), `LedgerDao`/`LedgerEntity`/`LedgerStatus` (Task 4), `TransliterationEngine.transliterate(name: String): String` (Task 3).
- Produces: `class RunEngine(contactsRepository: ContactsRepository, ledgerDao: LedgerDao, transliterationEngine: TransliterationEngine)` with `suspend fun runTranslate(onContactProcessed: suspend (originalName: String, translatedName: String) -> Unit = {})` and `suspend fun runRollback(onContactProcessed: suspend (originalName: String, translatedName: String) -> Unit = {})`. Consumed by Task 16 (`RunViewModel`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sampark.data.contacts.ContactRef
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeContactsRepository : ContactsRepository {
    val names = mutableMapOf<String, String>() // lookupKey -> current name
    var permission = true

    override fun hasContactsPermission() = permission
    override fun getEligibleContacts(): List<ContactRef> =
        names.map { (key, name) -> ContactRef(key, name) }

    override fun getCurrentName(lookupKey: String): String? = names[lookupKey]

    override fun updateName(lookupKey: String, newName: String) {
        names[lookupKey] = newName
    }
}

private class FakeTransliterationEngine : TransliterationEngine() {
    // Deterministic fake: uppercase the input, prefixed, so tests don't depend on real ICU output.
}

@RunWith(RobolectricTestRunner::class)
class RunEngineTest {

    private fun createDao() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LedgerDatabase::class.java
    ).allowMainThreadQueries().build().ledgerDao()

    @Test
    fun `runTranslate transliterates every PENDING row and marks it TRANSLATED`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING)
            )
        )
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runTranslate()

        val translated = dao.getRowsByStatus(LedgerStatus.TRANSLATED)
        assertEquals(2, translated.size)
        assertEquals(contacts.names["key1"], translated.first { it.lookupKey == "key1" }.translatedName)
    }

    @Test
    fun `runRollback reverts a row whose name still matches translatedName`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "निनान"
        }
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runRollback()

        assertEquals("Nitin", contacts.getCurrentName("key1"))
        assertEquals(1, dao.getRowsByStatus(LedgerStatus.ROLLED_BACK).size)
    }

    @Test
    fun `runRollback skips a row whose name has drifted since translation`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Someone Else Entirely"
        }
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runRollback()

        assertEquals("Someone Else Entirely", contacts.getCurrentName("key1"))
        assertEquals(1, dao.getRowsByStatus(LedgerStatus.SKIPPED_EXTERNAL_EDIT).size)
    }

    @Test
    fun `runRollback skips a row whose contact no longer exists`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository() // key1 never added -> getCurrentName returns null
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runRollback()

        assertEquals(1, dao.getRowsByStatus(LedgerStatus.SKIPPED_EXTERNAL_EDIT).size)
    }

    @Test
    fun `runTranslate invokes the progress callback per contact`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply { names["key1"] = "Nitin" }
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        val processed = mutableListOf<Pair<String, String>>()
        engine.runTranslate { original, translated -> processed.add(original to translated) }

        assertEquals(1, processed.size)
        assertEquals("Nitin", processed[0].first)
    }
}
```

(Drop the unused `FakeTransliterationEngine` class from the test file — it's not referenced by any test above; the real `TransliterationEngine` is deterministic enough for these assertions since they only check the row moved status and that *some* name was written, except the drift tests which supply their own fixed "already translated" name directly into the ledger row.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.domain.RunEngineTest"`
Expected: FAIL — `RunEngine` is unresolved.

- [ ] **Step 3: Implement `RunEngine`**

```kotlin
package com.sampark.domain

import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class RunEngine(
    private val contactsRepository: ContactsRepository,
    private val ledgerDao: LedgerDao,
    private val transliterationEngine: TransliterationEngine
) {
    suspend fun runTranslate(onContactProcessed: suspend (originalName: String, translatedName: String) -> Unit = {}) {
        val pendingRows = ledgerDao.getRowsByStatus(LedgerStatus.PENDING)
        for (row in pendingRows) {
            currentCoroutineContext().ensureActive()
            val translatedName = transliterationEngine.transliterate(row.originalName)
            contactsRepository.updateName(row.lookupKey, translatedName)
            ledgerDao.updateStatus(row.lookupKey, LedgerStatus.TRANSLATED)
            onContactProcessed(row.originalName, translatedName)
        }
    }

    suspend fun runRollback(onContactProcessed: suspend (originalName: String, translatedName: String) -> Unit = {}) {
        val translatedRows = ledgerDao.getRowsByStatus(LedgerStatus.TRANSLATED)
        for (row in translatedRows) {
            currentCoroutineContext().ensureActive()
            val currentName = contactsRepository.getCurrentName(row.lookupKey)
            if (currentName == null || currentName != row.translatedName) {
                ledgerDao.updateStatus(row.lookupKey, LedgerStatus.SKIPPED_EXTERNAL_EDIT)
            } else {
                contactsRepository.updateName(row.lookupKey, row.originalName)
                ledgerDao.updateStatus(row.lookupKey, LedgerStatus.ROLLED_BACK)
            }
            onContactProcessed(row.originalName, row.translatedName)
        }
    }
}
```

Note `TransliterationEngine` above is used as a concrete class, not an interface — the test file's `RunEngineTest` passes a real `TransliterationEngine()` instance in every test (the unused `FakeTransliterationEngine` was removed in Step 1), so no interface extraction is needed for this task.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.domain.RunEngineTest"`
Expected: PASS (all 5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/domain/RunEngine.kt app/src/test/java/com/sampark/domain/RunEngineTest.kt
git commit -m "feat: add shared translate/rollback run engine"
```

---

## Task 8: Routes and app router

**Files:**
- Create: `app/src/main/java/com/sampark/domain/Routes.kt`
- Create: `app/src/main/java/com/sampark/domain/AppRouter.kt`
- Test: `app/src/test/java/com/sampark/domain/AppRouterTest.kt`

**Interfaces:**
- Consumes: `ContactsRepository.hasContactsPermission()` (Task 6), `AppStatusRepository.direction/phase/permissionRequestedBefore` (Task 5).
- Produces: `object Routes` with string route constants (`WELCOME`, `PERMISSION_EXPLAINER`, `PERMISSION_DENIED`, `RUN_TRANSLATE`, `RUN_ROLLBACK`, `HOME`, `ROLLBACK_CONFIRM`, `COMPLETION`), `class AppRouter(contactsRepository: ContactsRepository, appStatusRepository: AppStatusRepository) { suspend fun resolveStartDestination(): String }`. Consumed by Task 17 (`SamparkNavHost`, `MainActivity`), and every screen task (Task 12/13/14/15) for route name constants.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.domain

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sampark.data.contacts.ContactRef
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeContactsRepository(var permission: Boolean) : ContactsRepository {
    override fun hasContactsPermission() = permission
    override fun getEligibleContacts(): List<ContactRef> = emptyList()
    override fun getCurrentName(lookupKey: String): String? = null
    override fun updateName(lookupKey: String, newName: String) {}
}

class AppRouterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createStatusRepository(): AppStatusRepository {
        val file = File(tempFolder.newFolder(), "app_status.preferences_pb")
        return AppStatusRepository(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `no permission and never requested before routes to Welcome`() = runTest {
        val router = AppRouter(FakeContactsRepository(permission = false), createStatusRepository())
        assertEquals(Routes.WELCOME, router.resolveStartDestination())
    }

    @Test
    fun `no permission but requested before routes to PermissionDenied`() = runTest {
        val status = createStatusRepository()
        status.setPermissionRequestedBefore(true)
        val router = AppRouter(FakeContactsRepository(permission = false), status)
        assertEquals(Routes.PERMISSION_DENIED, router.resolveStartDestination())
    }

    @Test
    fun `permission granted and direction NONE routes to Welcome`() = runTest {
        val router = AppRouter(FakeContactsRepository(permission = true), createStatusRepository())
        assertEquals(Routes.WELCOME, router.resolveStartDestination())
    }

    @Test
    fun `translate running routes to RunTranslate`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val router = AppRouter(FakeContactsRepository(permission = true), status)
        assertEquals(Routes.RUN_TRANSLATE, router.resolveStartDestination())
    }

    @Test
    fun `translate completed routes to Home`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.COMPLETED)
        val router = AppRouter(FakeContactsRepository(permission = true), status)
        assertEquals(Routes.HOME, router.resolveStartDestination())
    }

    @Test
    fun `rollback running routes to RunRollback`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.ROLLBACK)
        status.setPhase(Phase.RUNNING)
        val router = AppRouter(FakeContactsRepository(permission = true), status)
        assertEquals(Routes.RUN_ROLLBACK, router.resolveStartDestination())
    }

    @Test
    fun `rollback completed routes to Home`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.ROLLBACK)
        status.setPhase(Phase.COMPLETED)
        val router = AppRouter(FakeContactsRepository(permission = true), status)
        assertEquals(Routes.HOME, router.resolveStartDestination())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.domain.AppRouterTest"`
Expected: FAIL — `Routes`/`AppRouter` are unresolved.

- [ ] **Step 3: Implement `Routes` and `AppRouter`**

```kotlin
// Routes.kt
package com.sampark.domain

object Routes {
    const val WELCOME = "welcome"
    const val PERMISSION_EXPLAINER = "permission_explainer"
    const val PERMISSION_DENIED = "permission_denied"
    const val RUN_TRANSLATE = "run_translate"
    const val RUN_ROLLBACK = "run_rollback"
    const val HOME = "home"
    const val ROLLBACK_CONFIRM = "rollback_confirm"
    const val COMPLETION = "completion"
}
```

```kotlin
// AppRouter.kt
package com.sampark.domain

import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import kotlinx.coroutines.flow.first

class AppRouter(
    private val contactsRepository: ContactsRepository,
    private val appStatusRepository: AppStatusRepository
) {
    suspend fun resolveStartDestination(): String {
        if (!contactsRepository.hasContactsPermission()) {
            return if (appStatusRepository.permissionRequestedBefore.first()) {
                Routes.PERMISSION_DENIED
            } else {
                Routes.WELCOME
            }
        }

        val direction = appStatusRepository.direction.first()
        val phase = appStatusRepository.phase.first()

        return when {
            direction == Direction.NONE -> Routes.WELCOME
            direction == Direction.TRANSLATE && phase == Phase.RUNNING -> Routes.RUN_TRANSLATE
            direction == Direction.TRANSLATE && phase == Phase.COMPLETED -> Routes.HOME
            direction == Direction.ROLLBACK && phase == Phase.RUNNING -> Routes.RUN_ROLLBACK
            direction == Direction.ROLLBACK && phase == Phase.COMPLETED -> Routes.HOME
            else -> Routes.WELCOME
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.domain.AppRouterTest"`
Expected: PASS (all 7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/domain/Routes.kt app/src/main/java/com/sampark/domain/AppRouter.kt app/src/test/java/com/sampark/domain/AppRouterTest.kt
git commit -m "feat: add navigation routes and cold-start app router"
```

---

## Task 9: OKLCH color conversion and theme palette

**Files:**
- Create: `app/src/main/java/com/sampark/ui/theme/Oklch.kt`
- Create: `app/src/main/java/com/sampark/ui/theme/Color.kt`
- Test: `app/src/test/java/com/sampark/ui/theme/OklchTest.kt`

**Interfaces:**
- Produces: `fun oklch(lightness: Float, chroma: Float, hueDegrees: Float, alpha: Float = 1f): Color`, and named `Color` constants (`BackgroundColor`, `HeadingTextColor`, `BodyTextColor`, `SecondaryTextColor`, `TranslateAmber`, `RollbackRose`, `ResumeAmber`, `SuccessGreen`, `SecondaryBorder`). Consumed by Task 10 (shared components) and every screen task.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OklchTest {

    @Test
    fun `zero lightness and chroma is black`() {
        val color = oklch(0f, 0f, 0f)
        assertTrue(color.red < 0.01f)
        assertTrue(color.green < 0.01f)
        assertTrue(color.blue < 0.01f)
    }

    @Test
    fun `full lightness and zero chroma is white`() {
        val color = oklch(1f, 0f, 0f)
        assertTrue(color.red > 0.99f)
        assertTrue(color.green > 0.99f)
        assertTrue(color.blue > 0.99f)
    }

    @Test
    fun `zero chroma produces a neutral gray regardless of hue`() {
        val color = oklch(0.5f, 0f, 200f)
        assertEquals(color.red, color.green, 0.01f)
        assertEquals(color.green, color.blue, 0.01f)
    }

    @Test
    fun `increasing lightness at fixed chroma and hue increases perceived brightness`() {
        val darker = oklch(0.3f, 0.1f, 45f)
        val lighter = oklch(0.7f, 0.1f, 45f)
        val darkerSum = darker.red + darker.green + darker.blue
        val lighterSum = lighter.red + lighter.green + lighter.blue
        assertTrue(lighterSum > darkerSum)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.theme.OklchTest"`
Expected: FAIL — `oklch` is unresolved.

- [ ] **Step 3: Implement `oklch()`**

```kotlin
// Oklch.kt
package com.sampark.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Converts an OKLCH color (as used verbatim in the design file at
 * docs/design/Swarra Anande Static Gallery/Marathi Contacts App.dc.html)
 * to a Compose Color, via OKLab -> linear sRGB -> gamma-corrected sRGB.
 * Reference: Björn Ottosson, https://bottosson.github.io/posts/oklab/
 */
fun oklch(lightness: Float, chroma: Float, hueDegrees: Float, alpha: Float = 1f): Color {
    val hueRad = Math.toRadians(hueDegrees.toDouble())
    val a = (chroma * cos(hueRad)).toFloat()
    val b = (chroma * sin(hueRad)).toFloat()

    val lPrime = lightness + 0.3963377774f * a + 0.2158037573f * b
    val mPrime = lightness - 0.1055613458f * a - 0.0638541728f * b
    val sPrime = lightness - 0.0894841775f * a - 1.2914855480f * b

    val l = lPrime * lPrime * lPrime
    val m = mPrime * mPrime * mPrime
    val s = sPrime * sPrime * sPrime

    val rLinear = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    val gLinear = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    val bLinear = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

    return Color(
        red = linearToSrgb(rLinear),
        green = linearToSrgb(gLinear),
        blue = linearToSrgb(bLinear),
        alpha = alpha
    )
}

private fun linearToSrgb(component: Float): Float {
    val clamped = component.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) {
        clamped * 12.92f
    } else {
        (1.055f * clamped.toDouble().pow(1.0 / 2.4) - 0.055f).toFloat()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.theme.OklchTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 5: Define the theme palette from the design file's exact values**

```kotlin
// Color.kt
package com.sampark.ui.theme

val BackgroundColor = oklch(0.97f, 0.015f, 75f)
val HeadingTextColor = oklch(0.24f, 0.02f, 50f)
val BodyTextColor = oklch(0.38f, 0.02f, 50f)
val SecondaryTextColor = oklch(0.45f, 0.02f, 50f)
val TranslateAmber = oklch(0.56f, 0.15f, 45f)
val RollbackRose = oklch(0.55f, 0.10f, 350f)
val ResumeAmber = oklch(0.72f, 0.14f, 70f)
val SuccessGreen = oklch(0.55f, 0.11f, 150f)
val SecondaryBorder = oklch(0.85f, 0.02f, 50f)
val CardBackground = Color(0xFFFFFDFA) // "#fffdfa" literal in the design file (not OKLCH there)
```

(`Color` needs `import androidx.compose.ui.graphics.Color` at the top of `Color.kt`.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sampark/ui/theme/Oklch.kt app/src/main/java/com/sampark/ui/theme/Color.kt app/src/test/java/com/sampark/ui/theme/OklchTest.kt
git commit -m "feat: add OKLCH color conversion and theme palette from design file"
```

---

## Task 10: Shared Compose components (buttons, progress ring, type)

**Files:**
- Create: `app/src/main/java/com/sampark/ui/theme/Type.kt`
- Create: `app/src/main/java/com/sampark/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/sampark/ui/components/Buttons.kt`
- Create: `app/src/main/java/com/sampark/ui/components/CircularProgressRing.kt`
- Test: `app/src/test/java/com/sampark/ui/components/ButtonsTest.kt`
- Test: `app/src/test/java/com/sampark/ui/components/CircularProgressRingTest.kt`

**Interfaces:**
- Consumes: theme colors from Task 9.
- Produces: `@Composable fun SamparkTheme(content: @Composable () -> Unit)`, `@Composable fun PrimaryButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier)`, `@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier)`, `@Composable fun CircularProgressRing(progressFraction: Float, percentageLabel: String, ringColor: Color, modifier: Modifier = Modifier)`. Consumed by every screen task (12–15).

- [ ] **Step 1: Add the Google Fonts Noto Sans Devanagari downloadable font and type scale**

```kotlin
// Type.kt
package com.sampark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sampark.R

val NotoSansDevanagari = FontFamily(
    Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari_medium, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari_bold, FontWeight.Bold)
)

val SamparkTypography = Typography(
    headlineLarge = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    bodyLarge = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Normal, fontSize = 19.sp),
    labelLarge = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
)
```

This requires four Noto Sans Devanagari `.ttf` files in `app/src/main/res/font/`: `noto_sans_devanagari_regular.ttf`, `noto_sans_devanagari_medium.ttf`, `noto_sans_devanagari_semibold.ttf`, `noto_sans_devanagari_bold.ttf`, downloaded from Google Fonts (the same family the design file loads via `fonts.googleapis.com`). **This step requires the engineer to download those four files from https://fonts.google.com/noto/specimen/Noto+Sans+Devanagari and place them at that path before the project will compile** — flagged explicitly here rather than glossed over, since it's the one asset this plan can't generate as code.

- [ ] **Step 2: Implement `SamparkTheme`**

```kotlin
// Theme.kt
package com.sampark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SamparkColorScheme = lightColorScheme(
    primary = TranslateAmber,
    background = BackgroundColor,
    surface = BackgroundColor,
    onBackground = HeadingTextColor,
    onSurface = HeadingTextColor
)

@Composable
fun SamparkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SamparkColorScheme,
        typography = SamparkTypography,
        content = content
    )
}
```

(The design's screens are always light-background regardless of system theme — Requirement.md calls for high contrast, not dark-mode support, so `isSystemInDarkTheme()` is imported but intentionally unused here; remove the import if the compiler flags it as unused.)

- [ ] **Step 3: Write the failing button tests**

```kotlin
package com.sampark.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.TranslateAmber
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `PrimaryButton shows its text and is clickable`() {
        var clicked = false
        composeRule.setContent {
            PrimaryButton(text = "सुरू करा", color = TranslateAmber, onClick = { clicked = true })
        }
        composeRule.onNodeWithText("सुरू करा").assertHasClickAction()
        composeRule.onNodeWithText("सुरू करा").performClick()
        assert(clicked)
    }

    @Test
    fun `SecondaryButton shows its text and is clickable`() {
        var clicked = false
        composeRule.setContent {
            SecondaryButton(text = "रद्द करा", onClick = { clicked = true })
        }
        composeRule.onNodeWithText("रद्द करा").assertHasClickAction()
        composeRule.onNodeWithText("रद्द करा").performClick()
        assert(clicked)
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.components.ButtonsTest"`
Expected: FAIL — `PrimaryButton`/`SecondaryButton` are unresolved.

- [ ] **Step 5: Implement `PrimaryButton` and `SecondaryButton`**

Per the design file: primary buttons are 76px-tall full-width pills with 22dp corner radius and a shadow tinted to their own color; secondary buttons are 56px-tall, 18dp corner radius, transparent, with a thin border — visibly lower weight.

```kotlin
// Buttons.kt
package com.sampark.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sampark.ui.theme.SecondaryBorder
import com.sampark.ui.theme.SecondaryTextColor

@Composable
fun PrimaryButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(76.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text, fontSize = 24.sp)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, SecondaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryTextColor)
    ) {
        Text(text, fontSize = 19.sp)
    }
}
```

- [ ] **Step 6: Run the button tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.components.ButtonsTest"`
Expected: PASS.

- [ ] **Step 7: Write the failing progress-ring test**

```kotlin
package com.sampark.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sampark.ui.theme.TranslateAmber
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CircularProgressRingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows the given percentage label`() {
        composeRule.setContent {
            CircularProgressRing(progressFraction = 0.64f, percentageLabel = "६४%", ringColor = TranslateAmber)
        }
        composeRule.onNodeWithText("६४%").assertExists()
    }
}
```

(Add `import androidx.compose.ui.test.assertExists`.)

- [ ] **Step 8: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.components.CircularProgressRingTest"`
Expected: FAIL — `CircularProgressRing` is unresolved.

- [ ] **Step 9: Implement `CircularProgressRing`**

Per the design: a 160dp circle, conic-gradient arc proportional to progress, with a smaller inner circle showing the percentage centered — not a `LinearProgressIndicator`.

```kotlin
// CircularProgressRing.kt
package com.sampark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sampark.ui.theme.CardBackground
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.SecondaryBorder

@Composable
fun CircularProgressRing(
    progressFraction: Float,
    percentageLabel: String,
    ringColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            drawArc(
                color = SecondaryBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx())
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progressFraction.coerceIn(0f, 1f),
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx())
            )
        }
        Box(
            modifier = Modifier.size(124.dp).background(CardBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(percentageLabel, fontSize = 32.sp, color = HeadingTextColor)
        }
    }
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.components.CircularProgressRingTest"`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/sampark/ui/theme/Type.kt app/src/main/java/com/sampark/ui/theme/Theme.kt app/src/main/java/com/sampark/ui/components/ app/src/test/java/com/sampark/ui/components/
git commit -m "feat: add shared theme, buttons, and circular progress ring components"
```

---

## Task 11: RunViewModel

**Files:**
- Create: `app/src/main/java/com/sampark/ui/RunViewModel.kt`
- Test: `app/src/test/java/com/sampark/ui/RunViewModelTest.kt`

**Interfaces:**
- Consumes: `RunEngine` (Task 7), `AppStatusRepository` (Task 5), `Direction`/`Phase` (Task 5).
- Produces: `data class RunUiState(val done: Int, val total: Int, val isPaused: Boolean, val lastProcessed: Pair<String, String>?)`, `class RunViewModel(direction: Direction, runEngine: RunEngine, appStatusRepository: AppStatusRepository, ledgerDao: LedgerDao) : ViewModel()` exposing `val uiState: StateFlow<RunUiState>`, `fun start()`, `fun pause()`, `fun resume()`, `fun cancel()`. Consumed by Task 14 (`RunProgressScreen`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sampark.data.contacts.ContactRef
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.RunEngine
import com.sampark.domain.TransliterationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private class FakeContactsRepository : ContactsRepository {
    val names = mutableMapOf<String, String>()
    override fun hasContactsPermission() = true
    override fun getEligibleContacts(): List<ContactRef> = names.map { ContactRef(it.key, it.value) }
    override fun getCurrentName(lookupKey: String): String? = names[lookupKey]
    override fun updateName(lookupKey: String, newName: String) { names[lookupKey] = newName }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStatusRepository(): AppStatusRepository {
        val file = File(tempFolder.newFolder(), "app_status.preferences_pb")
        return AppStatusRepository(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    private fun createDao() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LedgerDatabase::class.java
    ).allowMainThreadQueries().build().ledgerDao()

    @Test
    fun `start runs to completion and marks phase COMPLETED`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING)))
        val contacts = FakeContactsRepository().apply { names["key1"] = "Nitin" }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Phase.COMPLETED, status.phase.first())
        assertEquals(1, viewModel.uiState.value.done)
        assertEquals(1, viewModel.uiState.value.total)
    }

    @Test
    fun `cancel on translate direction sets phase COMPLETED early`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING)
            )
        )
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.cancel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Phase.COMPLETED, status.phase.first())
    }

    @Test(expected = IllegalStateException::class)
    fun `cancel on rollback direction throws`() = runTest {
        val dao = createDao()
        val status = createStatusRepository()
        val engine = RunEngine(FakeContactsRepository(), dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.ROLLBACK, engine, status, dao)

        viewModel.cancel()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.RunViewModelTest"`
Expected: FAIL — `RunViewModel` is unresolved.

- [ ] **Step 3: Implement `RunViewModel`**

```kotlin
package com.sampark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerStatus
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.RunEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RunUiState(
    val done: Int = 0,
    val total: Int = 0,
    val isPaused: Boolean = false,
    val lastProcessed: Pair<String, String>? = null
)

class RunViewModel(
    private val direction: Direction,
    private val runEngine: RunEngine,
    private val appStatusRepository: AppStatusRepository,
    private val ledgerDao: LedgerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    fun start() {
        runJob = viewModelScope.launch {
            refreshCounts()
            val onProcessed: suspend (String, String) -> Unit = { original, translated ->
                _uiState.value = _uiState.value.copy(lastProcessed = original to translated)
                refreshCounts()
            }
            if (direction == Direction.TRANSLATE) {
                runEngine.runTranslate(onProcessed)
            } else {
                runEngine.runRollback(onProcessed)
            }
            appStatusRepository.setPhase(Phase.COMPLETED)
            _uiState.value = _uiState.value.copy(isPaused = false)
        }
    }

    fun pause() {
        runJob?.cancel()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resume() {
        _uiState.value = _uiState.value.copy(isPaused = false)
        start()
    }

    fun cancel() {
        check(direction == Direction.TRANSLATE) { "Rollback has no Cancel option" }
        runJob?.cancel()
        viewModelScope.launch {
            appStatusRepository.setPhase(Phase.COMPLETED)
        }
    }

    private suspend fun refreshCounts() {
        val total = if (direction == Direction.TRANSLATE) {
            ledgerDao.countAll().first()
        } else {
            ledgerDao.countByStatuses(
                listOf(LedgerStatus.TRANSLATED, LedgerStatus.ROLLED_BACK, LedgerStatus.SKIPPED_EXTERNAL_EDIT)
            ).first()
        }
        val done = if (direction == Direction.TRANSLATE) {
            ledgerDao.countByStatus(LedgerStatus.TRANSLATED).first()
        } else {
            ledgerDao.countByStatus(LedgerStatus.ROLLED_BACK).first()
        }
        _uiState.value = _uiState.value.copy(done = done, total = total)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.RunViewModelTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/ui/RunViewModel.kt app/src/test/java/com/sampark/ui/RunViewModelTest.kt
git commit -m "feat: add RunViewModel driving the translate/rollback loop"
```

---

## Task 12: Welcome and Permission Explainer screens

**Files:**
- Create: `app/src/main/java/com/sampark/ui/screens/WelcomeScreen.kt`
- Create: `app/src/main/java/com/sampark/ui/screens/PermissionExplainerScreen.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/WelcomeScreenTest.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/PermissionExplainerScreenTest.kt`

**Interfaces:**
- Consumes: `PrimaryButton` (Task 10), theme colors (Task 9).
- Produces: `@Composable fun WelcomeScreen(onStart: () -> Unit)`, `@Composable fun PermissionExplainerScreen(onContinue: () -> Unit)`. Consumed by Task 17 (`SamparkNavHost`).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping Start invokes onStart`() {
        var started = false
        composeRule.setContent {
            SamparkTheme { WelcomeScreen(onStart = { started = true }) }
        }
        composeRule.onNodeWithText("सुरू करा").performClick()
        assert(started)
    }

    @Test
    fun `shows the app name and explanation`() {
        composeRule.setContent {
            SamparkTheme { WelcomeScreen(onStart = {}) }
        }
        composeRule.onNodeWithText("मराठी संपर्क").assertExists()
    }
}
```

(Add `import androidx.compose.ui.test.assertExists`.)

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PermissionExplainerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping Continue invokes onContinue`() {
        var continued = false
        composeRule.setContent {
            SamparkTheme { PermissionExplainerScreen(onContinue = { continued = true }) }
        }
        composeRule.onNodeWithText("पुढे जा").performClick()
        assert(continued)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.screens.WelcomeScreenTest" --tests "com.sampark.ui.screens.PermissionExplainerScreenTest"`
Expected: FAIL — both screens are unresolved.

- [ ] **Step 3: Implement `WelcomeScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(148.dp).background(TranslateAmber.copy(alpha = 0.15f), CircleShape)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(20.dp))
        Text(
            "मराठी संपर्क",
            style = MaterialTheme.typography.headlineLarge,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "हे ॲप तुमच्या फोनमधील इंग्रजी नावं आपोआप मराठीत बदलेल, जेणेकरून ती वाचायला सोपी होतील. तुम्ही हे कधीही परत बदलू शकता.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "सुरू करा", color = TranslateAmber, onClick = onStart)
    }
}
```

- [ ] **Step 4: Implement `PermissionExplainerScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun PermissionExplainerScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "एक पायरी बाकी आहे",
            style = MaterialTheme.typography.headlineMedium,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "पुढील स्क्रीनवर \"Allow\" या बटणावर दाबा.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "पुढे जा", color = TranslateAmber, onClick = onContinue)
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.screens.WelcomeScreenTest" --tests "com.sampark.ui.screens.PermissionExplainerScreenTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sampark/ui/screens/WelcomeScreen.kt app/src/main/java/com/sampark/ui/screens/PermissionExplainerScreen.kt app/src/test/java/com/sampark/ui/screens/WelcomeScreenTest.kt app/src/test/java/com/sampark/ui/screens/PermissionExplainerScreenTest.kt
git commit -m "feat: add Welcome and Permission Explainer screens"
```

---

## Task 13: Home, Rollback Confirmation, Completion, Permission Denied screens

**Files:**
- Create: `app/src/main/java/com/sampark/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/com/sampark/ui/screens/RollbackConfirmScreen.kt`
- Create: `app/src/main/java/com/sampark/ui/screens/CompletionScreen.kt`
- Create: `app/src/main/java/com/sampark/ui/screens/PermissionDeniedScreen.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/HomeScreenTest.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/RollbackConfirmScreenTest.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/CompletionScreenTest.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/PermissionDeniedScreenTest.kt`

**Interfaces:**
- Consumes: `PrimaryButton`/`SecondaryButton` (Task 10), `Direction` (Task 5).
- Produces: `@Composable fun HomeScreen(direction: Direction, onRollbackOrTranslateAgain: () -> Unit)`, `@Composable fun RollbackConfirmScreen(onConfirm: () -> Unit, onCancel: () -> Unit)`, `@Composable fun CompletionScreen(summaryText: String, onOk: () -> Unit)`, `@Composable fun PermissionDeniedScreen(onGoToSettings: () -> Unit, onRetry: () -> Unit)`. Consumed by Task 17 (`SamparkNavHost`).

**Note on `HomeScreen`'s `direction` parameter:** per Requirement.md screen 4, Home shows the Marathi state (offer rollback) when `direction = TRANSLATE, phase = COMPLETED`, and the post-rollback state (offer translate-again) when `direction = ROLLBACK, phase = COMPLETED`. Since `HomeScreen` is only ever navigated to when `phase = COMPLETED` (per `AppRouter`), it only needs `direction` to pick its state — `phase` isn't a parameter here.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.data.status.Direction
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Marathi state shows rollback button and invokes callback`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { HomeScreen(direction = Direction.TRANSLATE, onRollbackOrTranslateAgain = { tapped = true }) }
        }
        composeRule.onNodeWithText("इंग्रजी नावं परत आणा").performClick()
        assert(tapped)
    }

    @Test
    fun `post-rollback state shows translate-again button`() {
        composeRule.setContent {
            SamparkTheme { HomeScreen(direction = Direction.ROLLBACK, onRollbackOrTranslateAgain = {}) }
        }
        composeRule.onNodeWithText("पुन्हा मराठीत बदला").assertExists()
    }
}
```

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RollbackConfirmScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Yes invokes onConfirm`() {
        var confirmed = false
        composeRule.setContent {
            SamparkTheme { RollbackConfirmScreen(onConfirm = { confirmed = true }, onCancel = {}) }
        }
        composeRule.onNodeWithText("हो, परत आणा").performClick()
        assert(confirmed)
    }

    @Test
    fun `No invokes onCancel`() {
        var cancelled = false
        composeRule.setContent {
            SamparkTheme { RollbackConfirmScreen(onConfirm = {}, onCancel = { cancelled = true }) }
        }
        composeRule.onNodeWithText("नको").performClick()
        assert(cancelled)
    }
}
```

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompletionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows the summary text and OK invokes onOk`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { CompletionScreen(summaryText = "५० संपर्कांची नावं मराठीत बदलली", onOk = { tapped = true }) }
        }
        composeRule.onNodeWithText("५० संपर्कांची नावं मराठीत बदलली").assertExists()
        composeRule.onNodeWithText("ठीक आहे").performClick()
        assert(tapped)
    }
}
```

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PermissionDeniedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Go to Settings invokes onGoToSettings`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { PermissionDeniedScreen(onGoToSettings = { tapped = true }, onRetry = {}) }
        }
        composeRule.onNodeWithText("सेटिंग्जमध्ये जा").performClick()
        assert(tapped)
    }

    @Test
    fun `retry link invokes onRetry`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { PermissionDeniedScreen(onGoToSettings = {}, onRetry = { tapped = true }) }
        }
        composeRule.onNodeWithText("पुन्हा विचारा").performClick()
        assert(tapped)
    }
}
```

(Each of the above needs `import androidx.compose.ui.test.assertExists` where used.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.screens.HomeScreenTest" --tests "com.sampark.ui.screens.RollbackConfirmScreenTest" --tests "com.sampark.ui.screens.CompletionScreenTest" --tests "com.sampark.ui.screens.PermissionDeniedScreenTest"`
Expected: FAIL — all four screens are unresolved.

- [ ] **Step 3: Implement `HomeScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.data.status.Direction
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.RollbackRose
import com.sampark.ui.theme.TranslateAmber

@Composable
fun HomeScreen(direction: Direction, onRollbackOrTranslateAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (direction == Direction.TRANSLATE) {
            Text(
                "सर्व नावं मराठीत बदलली आहेत",
                style = MaterialTheme.typography.bodyLarge,
                color = BodyTextColor,
                textAlign = TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
            PrimaryButton(
                text = "इंग्रजी नावं परत आणा",
                color = RollbackRose,
                onClick = onRollbackOrTranslateAgain
            )
        } else {
            Text(
                "नावं आता इंग्रजीत आहेत",
                style = MaterialTheme.typography.bodyLarge,
                color = BodyTextColor,
                textAlign = TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
            PrimaryButton(
                text = "पुन्हा मराठीत बदला",
                color = TranslateAmber,
                onClick = onRollbackOrTranslateAgain
            )
        }
    }
}
```

- [ ] **Step 4: Implement `RollbackConfirmScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.components.SecondaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.RollbackRose

@Composable
fun RollbackConfirmScreen(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "तुम्हाला खात्री आहे का?",
            style = MaterialTheme.typography.headlineMedium,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "सर्व नावं परत इंग्रजीत बदलली जातील.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "हो, परत आणा", color = RollbackRose, onClick = onConfirm)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
        SecondaryButton(text = "नको", onClick = onCancel)
    }
}
```

- [ ] **Step 5: Implement `CompletionScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun CompletionScreen(summaryText: String, onOk: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "पूर्ण झाले!",
            style = MaterialTheme.typography.headlineLarge,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            summaryText,
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "ठीक आहे", color = TranslateAmber, onClick = onOk)
    }
}
```

- [ ] **Step 6: Implement `PermissionDeniedScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.SecondaryTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun PermissionDeniedScreen(onGoToSettings: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "काळजी करू नका",
            style = MaterialTheme.typography.headlineMedium,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "या ॲपला काम करण्यासाठी संपर्कांची परवानगी हवी आहे.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "सेटिंग्जमध्ये जा", color = TranslateAmber, onClick = onGoToSettings)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(18.dp))
        Text(
            "पुन्हा विचारा",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryTextColor,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(8.dp).let { base ->
                base.then(androidx.compose.foundation.clickable { onRetry() })
            }
        )
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.screens.HomeScreenTest" --tests "com.sampark.ui.screens.RollbackConfirmScreenTest" --tests "com.sampark.ui.screens.CompletionScreenTest" --tests "com.sampark.ui.screens.PermissionDeniedScreenTest"`
Expected: PASS (all 7 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/sampark/ui/screens/HomeScreen.kt app/src/main/java/com/sampark/ui/screens/RollbackConfirmScreen.kt app/src/main/java/com/sampark/ui/screens/CompletionScreen.kt app/src/main/java/com/sampark/ui/screens/PermissionDeniedScreen.kt app/src/test/java/com/sampark/ui/screens/HomeScreenTest.kt app/src/test/java/com/sampark/ui/screens/RollbackConfirmScreenTest.kt app/src/test/java/com/sampark/ui/screens/CompletionScreenTest.kt app/src/test/java/com/sampark/ui/screens/PermissionDeniedScreenTest.kt
git commit -m "feat: add Home, Rollback Confirm, Completion, and Permission Denied screens"
```

---

## Task 14: RunProgressScreen (shared translate/rollback)

**Files:**
- Create: `app/src/main/java/com/sampark/ui/screens/RunProgressScreen.kt`
- Test: `app/src/test/java/com/sampark/ui/screens/RunProgressScreenTest.kt`

**Interfaces:**
- Consumes: `RunUiState` (Task 11), `Direction` (Task 5), `PrimaryButton`/`SecondaryButton`/`CircularProgressRing` (Task 10).
- Produces: `@Composable fun RunProgressScreen(direction: Direction, uiState: RunUiState, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit)`. Consumed by Task 17 (`SamparkNavHost`).

This is the screen with the pause/cancel asymmetry decided in the spec: translate's paused state shows Resume + Cancel; rollback's paused state shows Resume only.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.sampark.data.status.Direction
import com.sampark.ui.RunUiState
import com.sampark.ui.theme.SamparkTheme
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunProgressScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `running state shows Pause and tapping it invokes onPause`() {
        var paused = false
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.TRANSLATE,
                    uiState = RunUiState(done = 32, total = 50, isPaused = false, lastProcessed = "Swarra Anande" to "स्वारा आनंदे"),
                    onPause = { paused = true },
                    onResume = {},
                    onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("थांबवा").performClick()
        assert(paused)
    }

    @Test
    fun `translate paused state shows both Resume and Cancel`() {
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.TRANSLATE,
                    uiState = RunUiState(done = 32, total = 50, isPaused = true, lastProcessed = null),
                    onPause = {}, onResume = {}, onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("सुरू ठेवा").assertExists()
        composeRule.onNodeWithText("रद्द करा").assertExists()
    }

    @Test
    fun `rollback paused state shows only Resume, no Cancel`() {
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.ROLLBACK,
                    uiState = RunUiState(done = 20, total = 50, isPaused = true, lastProcessed = null),
                    onPause = {}, onResume = {}, onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("सुरू ठेवा").assertExists()
        composeRule.onNodeWithText("रद्द करा").assertDoesNotExist()
    }

    @Test
    fun `resume button invokes onResume`() {
        var resumed = false
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.TRANSLATE,
                    uiState = RunUiState(done = 32, total = 50, isPaused = true, lastProcessed = null),
                    onPause = {}, onResume = { resumed = true }, onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("सुरू ठेवा").performClick()
        assert(resumed)
    }
}
```

(Add `import androidx.compose.ui.test.assertExists` and `import androidx.compose.ui.test.assertDoesNotExist`. Remove the unused `onNodeWithTag`/`assertThrows` imports if the compiler flags them — they aren't used by the tests above.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.screens.RunProgressScreenTest"`
Expected: FAIL — `RunProgressScreen` is unresolved.

- [ ] **Step 3: Implement `RunProgressScreen`**

```kotlin
package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.data.status.Direction
import com.sampark.ui.RunUiState
import com.sampark.ui.components.CircularProgressRing
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.components.SecondaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.CardBackground
import com.sampark.ui.theme.ResumeAmber
import com.sampark.ui.theme.RollbackRose
import com.sampark.ui.theme.SecondaryTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun RunProgressScreen(
    direction: Direction,
    uiState: RunUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val ringColor = if (direction == Direction.TRANSLATE) TranslateAmber else RollbackRose
    val runningStatusText = if (direction == Direction.TRANSLATE) {
        "तुमची नावं मराठीत बदलली जात आहेत, कृपया थांबा"
    } else {
        "इंग्रजी नावं परत आणली जात आहेत"
    }
    val pausedStatusText = if (direction == Direction.TRANSLATE) {
        "काही काळजी नाही — तुमची नावं जशी आहेत तशीच सुरक्षित आहेत."
    } else {
        "काही काळजी नाही — आतापर्यंत बदललेली नावं तशीच राहतील."
    }
    val progressFraction = if (uiState.total == 0) 0f else uiState.done.toFloat() / uiState.total
    val percentageLabel = "${(progressFraction * 100).toInt()}%"
    val countLabel = "${uiState.done} पैकी ${uiState.total} संपर्क पूर्ण"

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressRing(
            progressFraction = progressFraction,
            percentageLabel = percentageLabel,
            ringColor = ringColor
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(18.dp))
        Text(
            if (uiState.isPaused) pausedStatusText else runningStatusText,
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        Text(countLabel, style = MaterialTheme.typography.bodyMedium, color = SecondaryTextColor)

        uiState.lastProcessed?.let { (original, translated) ->
            androidx.compose.foundation.layout.Spacer(Modifier.padding(14.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(original, color = SecondaryTextColor)
                Text(" → ", color = ringColor)
                Text(translated, color = BodyTextColor)
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(18.dp))

        if (!uiState.isPaused) {
            SecondaryButton(text = "थांबवा", onClick = onPause)
        } else {
            PrimaryButton(text = "सुरू ठेवा", color = ResumeAmber, onClick = onResume)
            if (direction == Direction.TRANSLATE) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
                SecondaryButton(text = "रद्द करा", onClick = onCancel)
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.ui.screens.RunProgressScreenTest"`
Expected: PASS (all 4 tests) — including confirming the rollback-paused state has no Cancel button, which is the one place the design file and the implementation deliberately diverge.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sampark/ui/screens/RunProgressScreen.kt app/src/test/java/com/sampark/ui/screens/RunProgressScreenTest.kt
git commit -m "feat: add shared RunProgressScreen with translate/rollback pause-cancel asymmetry"
```

---

## Task 15: AppContainer (manual DI)

**Files:**
- Create: `app/src/main/java/com/sampark/AppContainer.kt`
- Create: `app/src/main/java/com/sampark/SamparkApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/sampark/AppContainerTest.kt`

**Interfaces:**
- Consumes: `AndroidContactsRepository` (Task 6), `LedgerDatabase` (Task 4), `AppStatusRepository` (Task 5), `TransliterationEngine` (Task 3), `RunEngine` (Task 7), `AppRouter` (Task 8).
- Produces: `class AppContainer(context: Context)` exposing `val contactsRepository: ContactsRepository`, `val ledgerDao: LedgerDao`, `val appStatusRepository: AppStatusRepository`, `val transliterationEngine: TransliterationEngine`, `val runEngine: RunEngine`, `val appRouter: AppRouter`, and `fun runViewModelFactory(direction: Direction): ViewModelProvider.Factory`. Consumed by Task 17 (`MainActivity`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sampark

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    @Test
    fun `provides all core dependencies`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(context)

        assertNotNull(container.contactsRepository)
        assertNotNull(container.ledgerDao)
        assertNotNull(container.appStatusRepository)
        assertNotNull(container.transliterationEngine)
        assertNotNull(container.runEngine)
        assertNotNull(container.appRouter)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.AppContainerTest"`
Expected: FAIL — `AppContainer` is unresolved.

- [ ] **Step 3: Implement `AppContainer`**

```kotlin
package com.sampark

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.sampark.data.contacts.AndroidContactsRepository
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.status.AppStatusRepository
import com.sampark.domain.AppRouter
import com.sampark.domain.RunEngine
import com.sampark.domain.TransliterationEngine

private val Context.appStatusDataStore by preferencesDataStore(name = "app_status")

class AppContainer(context: Context) {
    val contactsRepository: ContactsRepository = AndroidContactsRepository(context)

    val ledgerDao: LedgerDao = LedgerDatabase.getInstance(context).ledgerDao()

    val appStatusRepository: AppStatusRepository = AppStatusRepository(context.appStatusDataStore)

    val transliterationEngine: TransliterationEngine = TransliterationEngine()

    val runEngine: RunEngine = RunEngine(contactsRepository, ledgerDao, transliterationEngine)

    val appRouter: AppRouter = AppRouter(contactsRepository, appStatusRepository)
}
```

- [ ] **Step 4: Implement `SamparkApplication` as the DI root holder**

```kotlin
package com.sampark

import android.app.Application

class SamparkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 5: Register `SamparkApplication` in the manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.WRITE_CONTACTS" />

    <application
        android:name=".SamparkApplication"
        android:label="Sampark"
        android:theme="@style/Theme.Sampark">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.sampark.AppContainerTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sampark/AppContainer.kt app/src/main/java/com/sampark/SamparkApplication.kt app/src/main/AndroidManifest.xml app/src/test/java/com/sampark/AppContainerTest.kt
git commit -m "feat: add manual DI container and Application class"
```

---

## Task 16: Navigation graph and MainActivity

**Files:**
- Create: `app/src/main/java/com/sampark/ui/navigation/SamparkNavHost.kt`
- Create: `app/src/main/java/com/sampark/MainActivity.kt`

**Interfaces:**
- Consumes: every screen composable (Tasks 12–14), `Routes` (Task 8), `AppRouter.resolveStartDestination()` (Task 8), `AppContainer` (Task 15), `RunViewModel` (Task 11).
- Produces: the running app. No further tasks consume this — it's the integration point.

This task wires the full navigation graph, matching every transition from Requirement.md and the UC doc: Welcome → Permission Explainer → (system permission dialog) → RunProgress(TRANSLATE) → Completion → Home; Home → RollbackConfirm → RunProgress(ROLLBACK) → Completion → Home; PermissionDenied → Settings/retry.

- [ ] **Step 1: Implement `SamparkNavHost`**

```kotlin
package com.sampark.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sampark.AppContainer
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.Routes
import com.sampark.ui.RunViewModel
import com.sampark.ui.screens.CompletionScreen
import com.sampark.ui.screens.HomeScreen
import com.sampark.ui.screens.PermissionDeniedScreen
import com.sampark.ui.screens.PermissionExplainerScreen
import com.sampark.ui.screens.RollbackConfirmScreen
import com.sampark.ui.screens.RunProgressScreen
import com.sampark.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

@Composable
fun SamparkNavHost(container: AppContainer, startDestination: String) {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        coroutineScope.launch {
            container.appStatusRepository.setPermissionRequestedBefore(true)
            if (granted) {
                container.appStatusRepository.setDirection(Direction.TRANSLATE)
                container.appStatusRepository.setPhase(Phase.RUNNING)
                navController.navigate(Routes.RUN_TRANSLATE) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            } else {
                navController.navigate(Routes.PERMISSION_DENIED) {
                    popUpTo(Routes.WELCOME) { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStart = { navController.navigate(Routes.PERMISSION_EXPLAINER) })
        }

        composable(Routes.PERMISSION_EXPLAINER) {
            PermissionExplainerScreen(
                onContinue = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )
        }

        composable(Routes.PERMISSION_DENIED) {
            PermissionDeniedScreen(
                onGoToSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                onRetry = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )
        }

        composable(Routes.RUN_TRANSLATE) {
            val viewModel: RunViewModel = viewModel(
                factory = container.runViewModelFactory(Direction.TRANSLATE)
            )
            RunProgressScreenRoute(
                direction = Direction.TRANSLATE,
                viewModel = viewModel,
                onFinished = { navController.navigate(Routes.COMPLETION) { popUpTo(Routes.RUN_TRANSLATE) { inclusive = true } } }
            )
        }

        composable(Routes.RUN_ROLLBACK) {
            val viewModel: RunViewModel = viewModel(
                factory = container.runViewModelFactory(Direction.ROLLBACK)
            )
            RunProgressScreenRoute(
                direction = Direction.ROLLBACK,
                viewModel = viewModel,
                onFinished = { navController.navigate(Routes.COMPLETION) { popUpTo(Routes.RUN_ROLLBACK) { inclusive = true } } }
            )
        }

        composable(Routes.COMPLETION) {
            CompletionScreen(
                summaryText = "पूर्ण झाले",
                onOk = { navController.navigate(Routes.HOME) { popUpTo(Routes.COMPLETION) { inclusive = true } } }
            )
        }

        composable(Routes.HOME) {
            var direction by remember { mutableStateOf(Direction.NONE) }
            LaunchedEffect(Unit) {
                container.appStatusRepository.direction.collect { direction = it }
            }
            HomeScreen(
                direction = direction,
                onRollbackOrTranslateAgain = {
                    if (direction == Direction.TRANSLATE) {
                        navController.navigate(Routes.ROLLBACK_CONFIRM)
                    } else {
                        coroutineScope.launch {
                            container.appStatusRepository.setDirection(Direction.TRANSLATE)
                            container.appStatusRepository.setPhase(Phase.RUNNING)
                            navController.navigate(Routes.RUN_TRANSLATE) { popUpTo(Routes.HOME) { inclusive = true } }
                        }
                    }
                }
            )
        }

        composable(Routes.ROLLBACK_CONFIRM) {
            RollbackConfirmScreen(
                onConfirm = {
                    coroutineScope.launch {
                        container.appStatusRepository.setDirection(Direction.ROLLBACK)
                        container.appStatusRepository.setPhase(Phase.RUNNING)
                        navController.navigate(Routes.RUN_ROLLBACK) { popUpTo(Routes.ROLLBACK_CONFIRM) { inclusive = true } }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun RunProgressScreenRoute(direction: Direction, viewModel: RunViewModel, onFinished: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(uiState.done, uiState.total) {
        if (uiState.total > 0 && uiState.done == uiState.total && !uiState.isPaused) {
            onFinished()
        }
    }

    RunProgressScreen(
        direction = direction,
        uiState = uiState,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onCancel = {
            viewModel.cancel()
            onFinished()
        }
    )
}
```

(`rememberCoroutineScope` and `collectAsState` need `import androidx.compose.runtime.rememberCoroutineScope` and `import androidx.compose.runtime.collectAsState` respectively — add both to the import list above.)

Note the `HOME` route reads `direction` reactively from `appStatusRepository` rather than as a nav argument — `AppRouter` only decides the *initial cold-start* destination; once inside the app, `HomeScreen` always reflects live persisted state, so it never goes stale across an in-app translate → rollback → translate-again cycle.

- [ ] **Step 2: Add `runViewModelFactory` to `AppContainer`**

```kotlin
// Add to AppContainer.kt, inside the AppContainer class:
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.sampark.data.status.Direction
import com.sampark.ui.RunViewModel

    fun runViewModelFactory(direction: Direction): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return RunViewModel(direction, runEngine, appStatusRepository, ledgerDao) as T
            }
        }
```

(Merge these imports into the existing import block at the top of `AppContainer.kt` rather than leaving them mid-file — they're written here separately only to show which are newly required for this step.)

- [ ] **Step 3: Implement `MainActivity`**

```kotlin
package com.sampark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.sampark.domain.Routes
import com.sampark.ui.navigation.SamparkNavHost
import com.sampark.ui.theme.SamparkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SamparkApplication).container

        setContent {
            var startDestination by remember { mutableStateOf<String?>(null) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                startDestination = container.appRouter.resolveStartDestination()
            }

            SamparkTheme {
                Surface(modifier = Modifier.fillMaxSizeSafe()) {
                    startDestination?.let { destination ->
                        SamparkNavHost(container = container, startDestination = destination)
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxSizeSafe(): Modifier =
    this.then(androidx.compose.foundation.layout.Modifier.fillMaxSize())
```

The `fillMaxSizeSafe()` helper avoids an import-order footgun (`Modifier.fillMaxSize()` needs `androidx.compose.foundation.layout.fillMaxSize` as an extension import); simplify this to a plain `import androidx.compose.foundation.layout.fillMaxSize` at the top of the file and call `Modifier.fillMaxSize()` directly instead, if preferred — both compile identically.

- [ ] **Step 4: Build and run a manual smoke test**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Then use the `run` skill (or `./gradlew installDebug` + launch on a connected device/emulator) to manually verify the golden path: Welcome → सुरू करा → Permission Explainer → पुढे जा → grant permission → Translation running → Completion → Home (Marathi state) → इंग्रजी नावं परत आणा → Rollback Confirm → हो, परत आणा → Rollback running → Completion → Home (English state, "पुन्हा मराठीत बदला").

- [ ] **Step 5: Run the full test suite one more time**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests from every prior task still passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sampark/ui/navigation/SamparkNavHost.kt app/src/main/java/com/sampark/MainActivity.kt app/src/main/java/com/sampark/AppContainer.kt
git commit -m "feat: wire navigation graph and MainActivity, completing the golden path"
```

---

## Self-Review Notes

**Spec coverage:** every item in the architecture spec's package structure (data/contacts, data/ledger, data/status, domain/*, ui/theme, ui/components, ui/screens, ui/navigation, AppContainer, MainActivity) has a task. The spec's "Error handling / edge cases" table is covered: permission-first routing (Task 8), pause/resume with no persisted paused state (Task 11/14), translate-only cancel (Task 11/14), drift detection including deleted contacts (Task 7), zero-contacts rollback (Task 7's loop is a no-op on an empty list, no guard needed), aggregate-lookupKey ledger keying (Task 4/6, no per-raw-contact tracking anywhere). The one explicitly-out-of-scope item (brand-name detection) is correctly *not* implemented, matching the spec's "known limitation."

**Placeholder scan:** no TBD/TODO left in any task; the one asset gap (Noto Sans Devanagari font files) is called out explicitly as an engineer action, not glossed over as "add appropriate fonts."

**Type consistency fix applied during review:** Task 2's first-draft `isAllCapsAcronym` (whole-string letter check) was caught failing its own task's test case during self-review and corrected to a per-token check before the task was finalized — left in place as a worked example rather than silently fixed, since it's a realistic mistake this exact rule is prone to.

**Type consistency:** `Direction`/`Phase`/`LedgerStatus` names and values are used identically across Tasks 4, 5, 7, 8, 11, 13, 14, 15, 16. `ContactRef`, `ContactsRepository` methods (`hasContactsPermission`, `getEligibleContacts`, `getCurrentName`, `updateName`) match between Task 6's production code and every later task's fakes (Tasks 7, 8, 11). `RunUiState` fields (`done`, `total`, `isPaused`, `lastProcessed`) match between Task 11's definition and Task 14's consumption. `Routes` constants match between Task 8's definition and Task 16's `NavHost`.

## Next step

Once this plan is approved, choose an execution approach below.
