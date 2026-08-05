# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A JavaFX desktop application for running an Egyptian educational centre (سنتر): student
registration, barcode attendance gate, cashier, teacher commissions and reporting. The UI
and all user-facing strings are **Arabic** — match that when adding messages, comments and
commit-adjacent docs.

## Commands

```bash
mvn -o clean test          # build + run all tests (offline works; everything is cached)
mvn -o compile             # compile only
mvn spring-boot:run        # run the app (needs DB credentials, see below)
```

`.github/workflows/build.yml` runs the same suite on every PR and push to `main`, but
**without `-o`** — the runner's `~/.m2` starts empty, so offline mode fails there. It needs
no database and no secrets: `src/test/resources/application.properties` shadows the main
one and uses in-memory H2.

Run a single test class or method:

```bash
mvn -o test -Dtest=EnrollmentServiceTest
mvn -o test -Dtest=EnrollmentServiceTest#reactivatesPreviousMembershipInsteadOfCreatingDuplicate
```

Tests use in-memory H2 (`src/test/resources/application.properties` overrides the dialect)
and never touch a real database. The app itself needs MySQL 8 plus `DB_USERNAME` /
`DB_PASSWORD` — there is deliberately **no default password**, so it fails loudly rather
than falling back to a committed secret. `application-local.properties` is gitignored but
is only read when the `local` profile is active; environment variables are the simpler
path. See `docs/first-install.md` for the full setup, including the Docker/`localhost`
trap when MySQL runs in a container.

## Architecture

### Spring Boot + JavaFX wiring

`CenterApplication.main` calls `Application.launch(JavaFxApplication.class)`. `JavaFxApplication.init()`
boots Spring, `start()` publishes a `StageReadyEvent`, and `PrimaryStageInitializer` listens
for it and shows `Login.fxml`.

**Load FXML only through `util/ViewLoader`.** It sets three things that must never be
forgotten together: the controller factory (so controllers are Spring beans with
constructor injection via `@RequiredArgsConstructor`), the resource bundle (or the screen
renders literal `%keys`), and the scene's node orientation. `showLogin` / `showDashboard`
also own the window sizing, because switching language rebuilds the scene and needs exactly
the same setup.

**Controllers are `@Scope(SCOPE_PROTOTYPE)` and must stay that way.** `DashboardController.loadView`
re-loads FXML on every navigation; as singletons they accumulated listeners and stale
`selectedX` state across screen changes.

### Threading — the rule that governs most UI code

Controllers run on the JavaFX application thread; every service call must not. Use
`FxAsync.supply` / `FxAsync.run` (`util/FxAsync.java`) rather than hand-rolling
`CompletableFuture` + `Platform.runLater`. It routes both success and failure back to the
UI thread and unwraps `CompletionException` so the Arabic message from the service reaches
the user. `FxAsync.messageOf` is null-safe on both cause and message.

Exception: JavaFX `PrinterJob` and print dialogs **must** run on the UI thread.

This threading model is also why authorization does not use Spring Security's
`@PreAuthorize` — see below.

### Authorization

`@RequiresRole(Role.ADMIN)` on service methods, enforced by `RoleEnforcementAspect` (AOP).
It reads the user from the injectable `UserSession` bean, not from `SecurityContextHolder`,
because service calls run on ForkJoinPool threads where the ThreadLocal security context
would not propagate and every check would fail.

Two constraints to respect when adding guards:

- `BackupService.executeBackup` is intentionally **unguarded** — `BackupScheduler` runs it
  from a scheduler thread with no user session. Only `restoreBackup` (destructive) is
  restricted.
- Callers must check the role *before* invoking an admin-only method when the screen is
  reachable by other roles (see `DashboardController.loadDashboardStats`), otherwise a
  SECRETARY hits `AccessDeniedException` on every open.

Hiding buttons in the UI is presentation only; the service layer is the real boundary.

Every denial is written to the audit trail before the exception is thrown — see below.

### The audit trail

`AuditLog` + `AuditService`, one row per event, read back through the admin-only
`AuditLog.fxml` screen. What it records is the answer to "who did this": sign-ins and
denied attempts, every cash movement, every create/update/delete of a student, group,
teacher or user, settings changes, and backups.

**Two propagation modes, and the difference is the whole design.**

- Success events (`record`) join the caller's transaction. An operation that rolls back
  leaves no line claiming it happened — and, the other way round, a line that cannot be
  written rolls the operation back. *What cannot be recorded does not happen*, which is
  what makes the absence of an event evidence rather than a guess.
- Failures and denials (`recordFailure`, `recordAs`) use `REQUIRES_NEW`. `ACCESS_DENIED` is
  always followed by an exception that kills the surrounding transaction; sharing it would
  erase exactly the lines the trail exists for. They also swallow write errors, because
  they run on an exception path and would otherwise replace the real reason on screen.

`AuditServiceTest` pins both directions; changing a propagation is invisible otherwise.

Three things that look like details and are not:

- **Nothing translated is stored.** The event is an `AuditAction` constant, rendered at
  display time. `details` is language-neutral `key=value` (`required=ADMIN; actual=SECRETARY`).
  Storing Arabic would freeze each line on whatever language its writer happened to use.
- **`actorUsername` is text, not a foreign key to `users`,** and `entityLabel` carries the
  target's name as it was. A foreign key would either block deleting a user or delete their
  trail with them — and deleting the account is the first thing someone covering their tracks
  does. `entityId` alone reads as "student 412 was deleted", which tells a reviewer nothing.
- **`AuditLogRepository` does not extend `JpaRepository`,** only the bare `Repository`, so
  `delete` and `deleteAll` do not exist on a table whose point is that it cannot be erased.
  That is a barrier inside the program, not inside MySQL: the DB user necessarily holds
  `DELETE`/`DROP` for Flyway and restore.

`actorUsername` is nullable and means *the system* — the scheduled backup runs on a scheduler
thread with no session, and attributing it to whoever logged in last would be a lie.

Attendance and `SESSION_CHARGE` are deliberately **not** audited: they happen hundreds of
times a day, `attendances` and `transactions` are already timestamped logs of them, and
duplicating them here would bury everything else. A trail nobody can read is worse than none.

The screen filters period/user/category in SQL and text/failures-only in memory, capping at
`AuditService.MAX_ROWS`; when the cap bites it says so, because a trail silently showing 1000
of 10000 events invites the reader to conclude the rest never happened.

### Language and direction

The UI ships in Arabic and English. **No user-facing string belongs in code or FXML** — it
goes in `src/main/resources/i18n/`:

- `messages.properties` — Arabic, and the **base** bundle (no locale suffix).
- `messages_en.properties` — English.

Arabic being the base means a key forgotten in the English file falls back to Arabic
instead of throwing `MissingResourceException` at the customer. `I18n` installs a
`ResourceBundle.Control` returning `null` from `getFallbackLocale`: without it,
`ResourceBundle` tries the **JVM default locale before the base bundle**, so asking for
Arabic on an English Windows silently returned the whole English bundle.

How to reach strings:

- FXML: `text="%student.title"`, `promptText="%student.searchPrompt"`.
- Java: `I18n.get(key)` or `I18n.format(key, args…)` (MessageFormat — double any `'`).
- Enums: `Role`/`NotificationType`/`TransactionType` expose `getDisplayName()` reading
  `role.ADMIN`, `notificationType.ABSENCE`, … Add the key when you add a constant.
- Commission type is a `String` column, not an enum — use `util/CommissionTypes`.
- Alerts: `util/Dialogs`. It replaced `AlertUtils` from the old fx-commons dependency, whose
  five static methods had no way to set direction or button labels, so its dialogs rendered
  left-to-right with OK/Cancel whatever the language.

`I18n.setLocale` also calls `Locale.setDefault`, which is what localises `DatePicker` month
names and other strings baked into JavaFX. Arabic uses `ar-EG-u-nu-latn` so amounts and
dates keep Latin digits.

**The language is stored per machine** (`java.util.prefs`), not in `CenterSettings`: the
login screen needs it before there is a session, and terminals in one centre may differ.
That is why this feature has no Flyway migration. Switching rebuilds the current scene via
`ViewLoader`; the selector lives on the login screen, the sidebar and the settings screen,
and is wired by `util/LanguageSelector` (whose re-entrancy guard is load-bearing).

Direction is set on the `Scene` by `ViewLoader`, never hardcoded in FXML.

**Do not write direction-specific CSS.** JavaFX mirrors RTL layout itself — the same
`<left>` node renders on the right, and `-fx-alignment` is mirrored with it. So
`center-left` means "start of the line" in both languages, and writing `center-right` for
Arabic flips it a second time and pushes the text to the wrong edge. The old hardcoded
`-fx-alignment: center-right` on `.sidebar-btn` was that bug.

### Printing

**Describe a printout as blocks, not as one node.** Build a `util/PrintDocument` —
`PrintDocument.report()` or `.receipt()`, a `header(Supplier<Node>)`, then `add(...)` for
each row — and hand it to `util/Printing.print(document, owner)`. Never call
`PrinterJob.createPrinterJob()` directly.

The reason is not tidiness. `printPage` renders **one page** and silently drops whatever
does not fit: an arrears report with 200 students printed the first 40 and nothing said so.
`Printing` measures the blocks and packs them into pages, so a block is never cut in half.
A block is therefore the unit that must not split — keep a heading and its first row in one
block if they must stay together.

The header is a `Supplier` because it repeats on every page and a JavaFX node cannot have
two parents. Read whatever it needs (settings, logo) *outside* the lambda: it runs once per
page, so a query inside it is one query per page.

`util/PrintPreferences` holds, **per machine** (`java.util.prefs`, no Flyway migration):

- printer **and paper size per `DocumentKind`** — one machine may drive a thermal receipt
  printer and an A4 printer at once;
- `PrintMode` (`PREVIEW` / `DIALOG` / `DIRECT`), global, `DIALOG` by default because that is
  what every earlier release did.

Paper choices come from `printer.getPrinterAttributes().getSupportedPapers()`, not a list in
code: only the printer knows its roll size, and offering a size it rejects fails at the
customer. Anything saved but now missing — unplugged printer, unsupported paper — falls back
to the printer's default instead of throwing; an unplugged printer must not stop receipts.
`savedPrinterIsMissing` / `savedPaperIsMissing` are what let the settings screen say so.

Two invariants worth keeping:

- `REPORT` paginates and numbers its pages; `RECEIPT` is one page however long, because a
  roll has no page breaks.
- The print dialog is shown **before** pagination, since the user may pick another paper size
  in it and a layout computed for A4 is wrong for A5.

**Content is laid out at the paper's width, not shrunk to it.** `fitToWidth` turns on
`wrapText` and caps widths to the printable area before measuring, so the same document sets
itself on A4 and on an 80 mm roll at its designed font size. `fitScale` is only a safety net
for what wrapping cannot break (one over-long word, an image) — it never enlarges.

Margins come from `MarginType.HARDWARE_MINIMUM`, never `DEFAULT`. JavaFX's `DEFAULT` is 0.75
inch per side, an A4 number: on a 227 pt roll the two margins eat 108 pt and leave 42 mm, so
the receipt printed tiny in the middle of the paper. Breathing room is `REPORT_PADDING` /
`RECEIPT_PADDING` inside the sheet instead.

`Printing.pageBreaks` is package-private and free of JavaFX on purpose: it is the decision
that loses data when it is wrong, and `PaginationTest` covers it without a toolkit.

The preview window pins the sheets to `LEFT_TO_RIGHT` while its own toolbar follows the UI
language. Printing happens on a node outside any scene, i.e. left-to-right; letting the
preview inherit the Arabic scene direction would show a mirrored version of what comes out
of the printer, which is the one thing a preview must not do. For the same reason the
stylesheet is loaded onto the off-screen layout scene too — measuring in one font and
printing in another makes the computed page breaks wrong.

### Backup

Three pieces: `BackupService` runs the tools, `BackupScheduler` decides when, `BackupCrypto`
protects the file. `BackupSchedule` is the pure next-run calculation.

**`executeBackup` throws with a translated message; it does not return a boolean.** The old
signature returned `false` and printed the stack trace, and a jpackage build has no console —
"the operation failed" was all the customer ever saw, whether the folder was gone, the
password was wrong or `mysqldump` was not installed. The tool's own stderr now reaches the
screen through `FxAsync`.

`mysqldump` is invoked with `--host` and `--port` **read from the JDBC URL**. Without them it
always went to `localhost:3306` — a centre running MySQL in Docker on another port had
backups that were failing, or worse, were of a different database. `JdbcUrlParsingTest`
covers the parsing; `docs/first-install.md` §7 has the Docker trap it comes from.

The flags are chosen for the **least-privilege database user** that `docs/first-install.md`
tells the installer to create — table-level rights on `center_db` only, nothing server-wide.
`--single-transaction` is not a performance tweak: without it mysqldump falls
back to `LOCK TABLES`, a privilege that user does not have, and the backup fails outright.
`--skip-triggers` is there for the same reason — reading triggers needs the TRIGGER
privilege, and the Flyway-owned schema has no triggers, routines or events to lose.
`--skip-add-locks` is the one that is easy to miss: `--single-transaction` governs how
mysqldump reads, while `--add-locks` (on by default) writes `LOCK TABLES` statements *into
the file* that are executed at restore time and need the LOCK TABLES privilege — the restore
dies with error 1044 at the first table while every grant looks correct.
`--no-tablespaces` avoids PROCESS (a server-wide privilege, not a `center_db` one), and
`--set-gtid-purged=OFF` keeps `SET @@SESSION.SQL_LOG_BIN` / `SET @@GLOBAL.GTID_PURGED` out of
the file: on a server with GTID enabled — most Docker images — those two lines are the first
thing `mysql` executes on restore, they need SUPER, and the restore dies with **error 1227**
before touching a single table. They are replication bookkeeping and mean nothing to a
single-server centre.

**Restore needs `DROP` on `center_db`** — the dump replaces every table, so it drops them
first. That one is a real privilege the installer must grant, not something a flag can avoid;
`docs/first-install.md` §3 grants it and explains why. When the tool exits with MySQL error
1044/1142/1227, `privilegeHint` appends the exact `GRANT` to run, because "DROP command
denied" tells the person in front of the screen nothing about what to do next.

Output is piped from the process, not written with `-r`, so it can pass through the cipher
before it touches the disk: there is never a moment where a plaintext dump of the whole
centre sits on the machine. Filenames carry a full timestamp — with the date alone, the
second backup of a day silently replaced the first.

**Encryption is AES-256-GCM with a PBKDF2 key** (`BackupCrypto`). GCM because a backup that
rotted on a USB stick must fail loudly instead of half-restoring. Decryption writes a
verified temp file *before* `mysql` is started, since the GCM tag is only checked at the last
byte and a wrong password would otherwise pour garbage into a live database.

The passphrase lives in **`BackupPreferences` (per machine, `java.util.prefs`)**, never in
`CenterSettings`: storing it in the database it protects would ship the key inside the
backup, and losing the database — the case backups exist for — would lose the passphrase with
it. That is also why there is no Flyway migration for it. The stored value is obfuscated,
which stops someone reading it out of the registry; it is not protection against an attacker
already running as that user, and the doc comment says so.

**The schedule is `CenterSettings`, not per machine** — unlike the printer and the language.
It is one data-protection policy: the hour at which the centre is closed and the database is
quiet. `BackupScheduler` builds a `Trigger` over `BackupSchedule` and reschedules on
`SettingsChangedEvent` (after commit), because a cron string baked into `@Scheduled` is read
once at startup and cannot be changed from a screen.

`BackupSchedule.isOverdue` is the reason the feature works at all. The machine in a centre is
switched off at night and the default slot is 02:00, so the practical result of the old
`@Scheduled(cron = "0 0 2 * * ?")` was: no backup, ever, with the checkbox showing as
enabled. A run whose slot has passed is taken a few minutes after the next startup, and
`lastAutoBackupAt` — written only on success — is what makes a nightly failure visible in the
settings screen instead of silent.

### Money

All amounts are `BigDecimal` with `DECIMAL(12,2)`. Never introduce `Double` for money.
`util/MoneyUtils` owns scale, rounding (`HALF_UP`) and display formatting — route new
display sites through it rather than `String.valueOf`.

### The balance ledger

Attendance is gated on a student's balance, not on a payment flag:

- `INCOME` — student pays at the cashier (cash into the till).
- `SESSION_CHARGE` — session fee deducted when attendance is registered. An accrual, **not**
  a cash movement, so it must stay out of `calculateTodayNetBalance`, which names its types
  explicitly.

Balance = `SUM(INCOME) - SUM(SESSION_CHARGE)` from `CenterSettings.ledgerStartDate` onward;
`null` means count everything (correct for a fresh install). `AttendanceService.processAttendance`
writes the attendance row and the charge in one transaction, and checks "already attended"
*before* the balance check so a double scan neither charges twice nor reports arrears.

A cashier top-up is deliberately not tied to a session — repeat top-ups are legitimate. The
duplicate guard lives on the charge side (`chargeSession`).

### Sessions

Several sessions may be open at once (parallel rooms). The only constraint is that one group
cannot have two open sessions — `findByGroupAndIsActiveTrue`. The attendance screen either
binds to one session (a terminal serving one room) or infers it from the student's enrolments
via `findActiveForStudent`, reporting ambiguity by name rather than guessing.

### Entities

Use `@Getter @Setter` plus `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with the id
included — **not** `@Data`. Lombok's `@Data` generates `equals`/`hashCode`/`toString` that
dereference `FetchType.LAZY` associations, causing `LazyInitializationException` outside a
transaction and broken equality against proxies. Repositories use `JOIN FETCH` where results
cross a transaction boundary into the UI.

## Testing

The three test classes exist because these failure modes are invisible to the compiler:

- `@Query` JPQL is parsed at runtime.
- Derived query names resolve against property names — the field is `isActive` while the
  getter is `isActive()`, a classic spot for resolution to fail.
- AOP advice silently does not run if the starter is missing or the call is self-invocation,
  so unexercised security can look applied while doing nothing.
- Translation fails silently, never at compile time: a key missing from the English bundle
  shows Arabic, and a mistyped key shows `!some.key!` on screen. `MessageBundleTest` turns
  both into build failures — it compares the two key sets, scans every FXML `%ref` and
  literal `I18n.get`/`format` call, checks each enum constant has a display name, and
  asserts both languages use the same `{n}` placeholders.

**Never assert a user-facing string as a literal.** The UI language is stored per machine,
so a test comparing against Arabic text starts failing the moment someone switches the app
to English. Compare against the key instead — `hasMessage(I18n.get("error.session.notFound"))`,
`isEqualTo(Role.ADMIN.getDisplayName())`, `contains(MoneyUtils.formatWithCurrency(…))`.

Add coverage when touching any of those. `@DataJpaTest` needs `@Import(SecurityConfig.class)`
because `CenterApplication` is itself a bean injecting `PasswordEncoder`.

## Schema changes

The schema is owned by **Flyway** (`src/main/resources/db/migration`), and
`ddl-auto=validate` means Hibernate creates nothing — it refuses to start if the schema and
the entities disagree. A missing migration is therefore a loud startup failure, not a
mystery error later.

After changing any entity:

1. Run the generator — it is not part of the normal suite, so name it explicitly:
   ```bash
   mvn -o test -Dtest=SchemaScriptGenerator
   ```
2. Diff `target/schema-mysql.sql` against the existing migrations.
3. Add a **new** `V<n>__*.sql` with just the delta. Never edit a migration that has been
   applied anywhere — Flyway checksums them and will refuse to run.

Write migrations by hand only as a last resort; the generator exists because a
hand-written schema that differs from the entities fails `validate` at the customer's
first launch rather than here.

Two MySQL facts worth remembering when writing migrations: DDL commits implicitly so
nothing rolls back mid-script, and `ADD COLUMN IF NOT EXISTS` is MariaDB syntax that fails
on MySQL. `docs/first-install.md` §7 has the full list.

Tests set `spring.flyway.enabled=false` and build the schema with `create-drop` on H2,
because the migrations are MySQL-dialect. That means the suite does **not** exercise the
migrations; they are verified by the generator being their source and by `validate` at
startup.

## Packaging

`packaging/build-installer.ps1` produces a self-contained Windows app via jpackage —
bundled JRE, no Java needed on the customer's machine. `app-image` (default) needs no extra
tooling; `-Type msi` needs WiX Toolset 3.x.

The script must stay UTF-8 **with BOM**: Windows PowerShell 5.1 reads `.ps1` as the system
codepage otherwise, which mangles the Arabic strings and breaks parsing.

## Dependencies

Everything resolves from Maven Central. **Keep it that way** — no `<repositories>` block, no
jar committed to the repo.

The project used to depend on `com.codejava.commons:fx-commons`, which is not on Maven
Central and was vendored under `lib/` behind a `file://` repository. `Dialogs` had already
replaced its `AlertUtils` (see above), and the rest of it came down to three small helpers,
now `util/Forms.java`. Reach for `util/Forms` — `numericOnly`, `decimalOnly`,
`focusNextOnEnter` — when a form field needs an input restriction.
