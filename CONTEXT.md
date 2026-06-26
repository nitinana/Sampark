# Sampark — App Context

## What This App Is

**Sampark** (संपर्क — "contact/connection") is an Android app for elderly Marathi-speaking users who cannot read English. It automatically transliterates contact names from English to Marathi so that callers are displayed in a script the user can read. The entire UI is in Marathi. The app is designed to be installed once and forgotten — it works silently in the background with no ongoing user action required.

**Platform:** Android 8.0+ (API 26), Kotlin

---

## Core Philosophy

- **Install and forget.** The user taps "Start" once, grants permissions, and never needs to think about the app again.
- **One screen, one button.** No menus, no settings, no decisions. The main screen has a single large button whose label reflects what the app can do right now.
- **Silent operation.** Transliteration happens without pop-ups or confirmations. The user's contacts simply appear in Marathi.
- **Safe rollback.** The app always remembers the original English names so the user can revert everything with one tap.

---

## What the App Does

### Phase 1 (Current)

1. **Initial bulk scan** — on first launch (after permissions), scans all contacts and transliterates any with Latin-character names to Marathi.
2. **Background sync** — a WorkManager job runs every 30 minutes and transliterates any new contacts added via the native Contacts app or any other app.
3. **One-tap rollback** — tapping "इंग्रजी नावे परत आणा" restores all original English names and forgets the translations.
4. **Re-translate** — after a rollback, tapping "मराठीत रूपांतरित करा" re-runs the bulk scan.

### Phase 2 (Parked)
- Unknown caller identification with Marathi overlay
- Real-time contact updates (foreground service instead of WorkManager)
- Handling edits to existing contacts via background listener

---

## Transliteration Rules

### Detection
A contact is a candidate for transliteration if its display name contains **any Latin (A–Z, a–z) characters**.

| Name | Action |
|------|--------|
| "Rahul Sharma" | Transliterate → "राहुल शर्मा" |
| "HDFC Bank" | Transliterate → "एचडीएफसी बँक" |
| "राहुल" | Skip — already Devanagari |
| "9876543210" | Skip — no alphabetic characters |
| "WhatsApp" | Transliterate → "व्हॉट्सअॅप" |

### Transformation
**Pure transliteration** — phonetic conversion to Devanagari. Not semantic translation. Uses **ML Kit Transliteration API** (on-device, offline after one-time model download during setup).

### Scope
All contacts the app has write access to: local/device contacts and Google-synced contacts. SIM contacts and WhatsApp-managed contacts are skipped (not writable by third-party apps). The Google sync side-effect is accepted — Marathi names will propagate to Google Contacts in the cloud.

### Failure handling
If transliteration fails for a specific contact (model error, unusual characters, etc.), the contact is silently skipped. It stays in English. No error is shown to the user.

---

## Data Model — No Database

Sampark stores **no local database**. All persistent state lives directly inside Android Contacts as custom data rows via `ContactsContract.Data`.

### Custom Contact Field

MIME type: `vnd.android.cursor.item/com.sampark.original_name`

| Column | Content |
|--------|---------|
| `Data1` | Original English display name (source of truth for rollback) |
| `Data2` | Transliterated Marathi name Sampark wrote (used to detect user edits) |

This design means:
- State survives app uninstall/reinstall (custom fields sync with Google Contacts)
- State survives device migration
- No sync issues between a local DB and contacts

### App-Level State (SharedPreferences)

| Key | Type | Purpose |
|-----|------|---------|
| `ml_kit_model_downloaded` | Boolean | Whether the transliteration model has been downloaded |

---

## Bulk Scan Algorithm

```
fun runScan(context):
  contacts = queryAllWritableContacts()
  count = 0
  for contact in contacts:
    if hasCustomField(contact, MIME_TYPE):
      continue  // already processed by Sampark
    if !containsLatinChars(contact.displayName):
      continue  // already Devanagari or no alphabetic chars
    transliterated = mlKit.transliterate(contact.displayName)
    if transliterated == null:
      continue  // silent failure
    updateContactDisplayName(contact.id, transliterated)
    writeCustomField(contact.id, Data1=contact.displayName, Data2=transliterated)
    count++
  return count
```

**Idempotent by design:** already-transliterated contacts have Devanagari names → no Latin characters → skipped on re-run. Safe to restart after interruption.

---

## Rollback Algorithm

```
fun runRollback():
  translatedContacts = queryContactsWithCustomField(MIME_TYPE)
  
  // Step 1: Restore all names FIRST (safe to re-run if interrupted)
  for contact in translatedContacts:
    current = getContactDisplayName(contact.id)
    if current == null:
      continue  // contact was deleted after translation, skip
    if current != contact.Data2:
      continue  // user manually edited the name after translation, respect their change
    updateContactDisplayName(contact.id, contact.Data1)  // restore English name
  
  // Step 2: Delete all custom fields only after all names are restored
  deleteAllCustomFields(MIME_TYPE)
```

**Order matters:** names restored first, custom fields deleted second. If killed mid-rollback, the user can tap the button again — the guard `current != contact.Data2` ensures already-restored contacts are skipped.

---

## Background Job

WorkManager periodic job, **every 30 minutes**, no foreground service, no persistent notification.

The job runs the same `runScan()` function. Because the scan is idempotent, running it on a schedule is safe — it only processes contacts that don't yet have a Sampark custom field.

---

## Main Screen States

| State | Condition | Button Label | Button Action |
|-------|-----------|--------------|---------------|
| Active | At least one contact has a Sampark custom field | "इंग्रजी नावे परत आणा" | Run rollback |
| Inactive | No Sampark custom fields found | "मराठीत रूपांतरित करा" | Run bulk scan |

The app checks which state it is in by querying for any contact with the custom MIME type.

---

## Setup Flow (First Launch)

1. Full-screen welcome in Marathi — explains what the app does
2. Permission requests: `READ_CONTACTS`, `WRITE_CONTACTS`
3. ML Kit model download (requires internet once) — spinner shown, error shown if offline
4. Bulk scan runs — spinner + "नावे बदलत आहे..."
5. Completion screen — "X नावे मराठीत बदलली" (X names converted to Marathi)
6. Main screen appears with "इंग्रजी नावे परत आणा" button

---

## Key Edge Cases

| Scenario | Behaviour |
|----------|-----------|
| Contact deleted after translation | Rollback skips it silently (Contact ID not found) |
| Contact manually edited by user after translation | Compare current name vs `Data2`; if different, user edited it — skip during rollback |
| Transliteration fails for a contact | Skip silently, contact stays in English |
| Bulk scan interrupted mid-run | Safe to restart — idempotent detection skips already-processed contacts |
| Rollback interrupted mid-run | Safe to restart — guard skips already-restored contacts |
| App uninstalled and reinstalled | Custom fields survive via Google sync; rollback works immediately after reinstall |
| User adds contact via native Contacts app | WorkManager job picks it up within 30 minutes |
| Contact already has a Marathi (Devanagari) name | Detection rule skips it (no Latin characters) |
| No contacts in phone | Scan completes with count 0, no error |
| SIM / WhatsApp contacts | Skipped — not writable |

---

## Permissions

| Permission | Why |
|------------|-----|
| `READ_CONTACTS` | Read contact names for scan and rollback |
| `WRITE_CONTACTS` | Update contact names and write/delete custom fields |
| Internet | One-time ML Kit model download during setup only |

---

## What This App Deliberately Does NOT Do (Phase 1)

- No in-app "Add Contact" screen — users add contacts via the native Contacts app as usual
- No overlay on incoming/outgoing calls — Phase 2
- No unknown caller lookup — Phase 2
- No real-time contact monitoring — WorkManager every 30 minutes is sufficient
- No UI for viewing or editing translations — one button, one action
- No translation of contact names (semantic meaning) — only transliteration (phonetic script conversion)
