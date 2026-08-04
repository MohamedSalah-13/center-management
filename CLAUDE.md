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

- `BackupService.executeBackup` is intentionally **unguarded** — the 2 AM `@Scheduled` job
  runs with no user session. Only `restoreBackup` (destructive) is restricted.
- Callers must check the role *before* invoking an admin-only method when the screen is
  reachable by other roles (see `DashboardController.loadDashboardStats`), otherwise a
  SECRETARY hits `AccessDeniedException` on every open.

Hiding buttons in the UI is presentation only; the service layer is the real boundary.

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
- Alerts: `util/Dialogs`, **not** `AlertUtils` from fx-commons. The vendored jar has no way
  to set direction or button labels, so its dialogs rendered left-to-right with OK/Cancel
  whatever the language.

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

`com.codejava.commons:fx-commons` (`AlertUtils`, `FormUtils`, `InputValidator`) is not on
Maven Central. It is vendored in `lib/` and resolved through a `file://` repository declared
in the POM — do not remove either, or the build breaks on every machine but the one where it
was originally installed.
