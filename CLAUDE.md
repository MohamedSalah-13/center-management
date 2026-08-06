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

`showModal` is the same rule for a **separate window** (`StudentEnrollments.fxml` and
`AlertRuleEditor.fxml` are the two). A new `Stage` is a new scene that inherits nothing from the window that opened it —
not direction, not the stylesheet, not the root font size — exactly like `Dialogs.decorate`
and the bell dropdown. Its `prepare` callback runs on the controller after the load and
*before* the window is shown, because a window opened for one row has to know its subject
before it is drawn, not after.

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
- Enums: `Role`/`AlertType`/`TransactionType` expose `getDisplayName()` reading
  `role.ADMIN`, `alertType.ABSENCE`, … Add the key when you add a constant. `AlertType` needs
  four more per constant — `.desc`, `alert.message.<NAME>`, plus `.threshold`/`.window` when it
  uses them and `alert.parentMessage.<NAME>` when it is parent-capable; `MessageBundleTest`
  fails the build for each one missing. `Currency` needs two — the name and `.symbol`.
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

### Size: the whole UI is a multiple of one number

`util/UiScale` writes `-fx-font-size` in pixels onto the **scene root**, and every size in
`style.css` is written in `em` — a fraction of the node's own font size, which is inherited.
So that one line is the size of the interface: fonts, paddings, corner radii, table row
heights, the sidebar's width. There is no second place to remember, and this is also how
JavaFX's own `modena.css` is written.

**Write `em`, not `px`, for anything around text.** A `px` value is a size that stays put
while the text beside it grows, which is how a heading ends up clipped in its own row. The
exception is a border width or a shadow: a 2px focus ring is a marker, not a measurement,
and scaling it just fattens the screen.

**A size written in FXML wins over CSS and can never scale.** `prefWidth="272.0"` on the
sidebar, or a `<font><Font size="26.0"/></font>` child, is a value "set by the user" as far
as the CSS engine is concerned, so the stylesheet never touches it — and a `<font>` element
also blocks inheritance for that node's children. Both were removed: the sidebar's width is
`-fx-pref-width` in `.sidebar`, and the 77 `<Font>` elements across the screens became
`em` in the nodes' `style` attributes.

Three surfaces get the number written on them explicitly, because a `Popup` and a
`DialogPane` are separate scenes that inherit nothing from the window that opened them:
`Dialogs.decorate`, `Toasts.card` and the bell dropdown in `DashboardController`. Everything
inside them is `em` and follows.

**Printing is deliberately outside all of it.** `Printing` lays its nodes out in an
off-screen scene that never gets the root line, so a printout stays at the base 14px however
large the operator's screen is — otherwise the page breaks computed in `Printing.pageBreaks`
would change with a display preference. The preview window is unscaled for the same reason:
it shows the very nodes that go to the printer, and a preview that does not match the paper
is worse than none.

**Stored per machine** (`java.util.prefs`, no Flyway migration), exactly like the language
and the printer: the size is a property of the screen in front of someone — a 24" reception
terminal beside a manager's laptop — not a policy of the centre like the currency or the
backup schedule. It is changed from the login screen, the sidebar (all roles: the settings
screen is admin-only, and the person reading a screen all day is usually not the admin), the
settings screen, or `Ctrl` `+`/`-`/`0` on any scene. It takes effect without rebuilding
anything, since it is one style line on the root — which is why `UiScaleSelector` needs no
"reload" callback the way `LanguageSelector` does, and why unsaved form input survives it.

Two details that look small and are not: `fontSizeStyle()` builds its number with
`Locale.ROOT`, because the Arabic locale writes a decimal comma and JavaFX's CSS parser
drops the whole declaration silently — a feature that works only in English. And the window
sizes in `ViewLoader` grow with the factor but are clamped to the screen, since a minimum
width of 1100 becomes 1925 at 175% and a window that cannot be made smaller than the display
has no visible close button.

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

#### The other printing path: JasperReports

`PrintDocument` is for what the app lays out itself. **Anything that has to look like a
form — a bordered table, an ID card, a barcode — is a `.jrxml` under
`src/main/resources/reports/`,** filled by `ReportService` and exported to PDF.

**Every new Jasper report is built the same way. The recipe is three steps:**

1. Declare the five header parameters (`HEADER_REPORT`, `SHOW_CENTER`, `CENTER_NAME`,
   `CENTER_PHONE`, `LOGO_PATH`) and copy the `pageHeader` band from
   `StudentEnrollments.jrxml` — one `<subreport>` element pointing at `$P{HEADER_REPORT}`.
2. Fill the parameter map through **`ReportService.withCenterHeader(map)`**, which compiles
   `CenterHeader.jrxml`, reads the centre row once, and resolves the logo path — and through
   **`withSheetFooter(map)`** for `PRINTED_AT` / `PAGE_LABEL`. Those two are shared keys
   (`report.sheet.*`), not per-report ones: "Page" does not differ between reports, and a copy
   per template is a translation to review in ten places.
3. Put the report's own title and subject in **`columnHeader`, never `title`.** Jasper prints
   the title band *above* the page header on page one, which would place the letterhead under
   the heading; and `columnHeader` repeats, so page two found on the printer still says what
   it is and whose it is.

`CenterHeader.jrxml` is the only file that draws the letterhead — logo right, name and phone
left. Copying that block into each report instead would make "resize the logo" an edit in ten
files, and the one that gets missed only shows up on paper at the customer.
All four templates follow the recipe — `StudentEnrollments.jrxml`, `StudentIdCards.jrxml`,
`GroupStudents.jrxml` and `GroupsList.jrxml`; copy the band from any of them.

**Delivery goes through `ReportService.deliver` → `SheetDelivery` → `util/Sheets.show`.** The
service fills the sheet and then either sends it to the printer or writes a temp PDF, according
to `PrintPreferences.printsSheetsDirectly()`; `Sheets.show` turns the outcome into either "sent
to printer X" or an opened PDF. Both halves are written once because the second screen that
printed a sheet copied the first, and the third would have copied the second — including the
chance of forgetting to say anything when the viewer fails to open.

The band carries `<printWhenExpression>$P{SHOW_CENTER}</printWhenExpression>`, **on the band
and not on the subreport element**: a band collapses to zero height and everything below
moves up, while a hidden element leaves its 78 points of white space at the top of every page.
`PrintPreferences.printsCenterHeader()` (per machine, default on) is the checkbox behind it —
per machine because the reason to switch it off is that *this* printer is loaded with
pre-printed letterhead paper, which is a property of the paper tray, not of the centre.

Three more rules that are not obvious from the existing files:

- **No Arabic in a `.jrxml`.** Every caption arrives as a `$P{}` parameter built with `I18n`
  in Java. The bundles do not see the file and `MessageBundleTest` does not scan it, so a
  string typed inside it prints in its own language whatever the user chose — which is what
  `GroupStudents.jrxml` and `StudentIdCards.jrxml` still do.
- **Rows are beans, not records — and not entities either.** Jasper reads `getGroupName()`; a
  record names its accessor `groupName()` and the column comes out **blank with no error**.
  That is why `EnrollmentReportRow` is a class with getters while `MembershipRow` beside it is
  a record. Entities fail differently and louder: `StudentIdCards.jrxml` declared
  `schoolLevel` as `java.lang.String` while `Student.getSchoolLevel()` returns the enum, so
  the whole export died with `JRExpressionEvalException` for every student who had a level.
  `IdCardRow` is the layer that turns entity values into the strings the sheet declares — and
  it is where a `SchoolLevel` becomes its *translated* name rather than its constant.
- **Columns are laid out right-to-left** (subject first at the right edge). Jasper places
  elements at fixed coordinates and does not mirror them by language the way the UI does, so
  one file cannot serve both — and the centre reads Arabic.

Text uses `fontName="ArabicFont"`, registered by `jasperreports_extension.properties` →
`fonts/fonts.xml`; without it Arabic renders as boxes in the PDF. `ReportTemplateCompileTest`
compiles every template, fills the enrolments sheet, and checks the letterhead both appears
and disappears — a `.jrxml` is parsed at run time only, and a subreport is wired at run time
too: one forgotten parameter means a header missing from every page with no error anywhere.

**Do not judge Arabic by `JasperPrintManager.printPageToImage`.** The fill marks Arabic text
`RunDirection.RTL` and the PDF renderer honours it; the AWT renderer behind that debug image
does not, so it shows every word reversed while the real PDF is correct. Positions in that
image are trustworthy, letter order is not.

**Delivery is a per-machine checkbox: `PrintPreferences.printsSheetsDirectly()`.** Unticked
(the default) exports a temp PDF and opens it in the system viewer; ticked sends the sheet
straight to the printer through `JRPrintServiceExporter`, no window. It is a *second* setting
beside `PrintMode` and not a duplicate of it — `PrintMode` governs the `Printing` path drawn
from JavaFX nodes, this one governs `.jrxml` sheets, and the two share no code. `PREVIEW` has
no counterpart here because opening the PDF **is** the preview.

Direct printing looks the `javax.print.PrintService` up **by name** against the JavaFX printer
chosen for `DocumentKind.REPORT`; both names come from the same Windows spooler. Without that
lookup the sheet goes to the system default while the settings screen names another printer.
No page or print dialog is ever shown: this runs on a background thread (`FxAsync`), and
opening an AWT dialog from there is a gamble — someone who wants the dialog leaves the box
unticked and prints from the PDF viewer. The default stays "open the PDF" deliberately: an
upgrade that starts pushing paper out of a printer nobody asked is not a bug anyone reports,
it is just wasted paper.

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

### Parent notifications

`NotificationService` decides who gets told and in what words; **it never learns which
channel is in use.** Everything provider-shaped sits behind `MessageSender`, whose only
implementation is `MessageSenderRouter`.

**The channel is read on every send, not at startup.** It used to be
`center.notifications.channel` in `application.properties` selected by
`@ConditionalOnProperty`: in a jpackage build, changing it meant editing a file inside the
program folder and restarting, which no centre owner does. It now lives in `CenterSettings`
and the router resolves it per send, so the Settings screen takes effect on the next message.

Adding a provider means a new `ChannelSender`, not a new `MessageSender`. `ChannelSender`
deliberately does **not** extend `MessageSender` — four beans of the injectable type is
exactly the ambiguity that produced the old `@ConditionalOnMissingBean` bug (the annotation
was evaluated against the class being scanned, so it excluded itself and nothing registered).
The router validates at construction that every `NotificationChannel` has a sender, so a
channel offered in the UI can never be one that fails on every message.

Config arrives as a `NotificationConfig` parameter rather than being read inside each sender:
one read per batch instead of one query per message, and senders that unit-test without Spring.

**Where each setting lives is the same split as backup.** Channel, provider URL, sender id
and templates are centre policy → `CenterSettings` (Flyway `V4`). The API token and the
WhatsApp link style are **per machine** (`NotificationPreferences`, `java.util.prefs`, no
migration): the token is a secret, and storing it in the database ships it inside every
backup — next to the very parent phone numbers it can message — while the link style follows
what is installed on *this* terminal, like the printer. `MachineSecret` holds the obfuscation
both it and `BackupPreferences` use; its purpose string is part of the key, so changing that
string invalidates every value saved with it on customers' machines.

Three channel-specific things worth knowing:

- The link channel builds its URL from a template (`WhatsAppLink`), and spaces encode as
  `%20` not `+`: `whatsapp://` is opened by the OS protocol handler, not a browser, and `+`
  arrives literally inside the message text. A template missing `{phone}` or `{text}` is
  rejected when it is typed — either one opens a chat that looks sent and is not.
- **Cloud API needs a template name for what this app actually sends.** Free-form text is
  only allowed inside 24 hours of the parent's last message to the centre; absence and
  arrears notifications start from the centre, so they are outside it and get error 131047.
  With a template name set, the whole composed message goes in as body parameter `{{1}}`.
- The generic gateway describes the request instead of hard-coding a provider per class —
  Egyptian centres buy WhatsApp/SMS from local resellers that each name their fields
  differently, and that is a difference in one request string, not in the program.

`configurationProblem()` exists so the screen can say "no token on this machine" the day
before a send, and so the router refuses a doomed attempt rather than letting the provider
answer 401 in English forty times. The Settings tab's test send is the only way to catch an
expired token or an unapproved template, both of which surface only in a real reply; test
messages are deliberately not written to `notification_logs`, which exists to stop duplicate
notifications to *students*.

### Alerts

`service/alert/` — one registry of alert kinds serving both the in-app inbox and outgoing
parent messages. `AlertType` **replaced `NotificationType`**; `ABSENCE` and `ARREARS` kept
their exact names so existing `notification_logs` rows read unchanged (`V5` only widened the
column).

**Two axes, not one.** `AlertCategory` is what the alert is *about*, `AlertAudience` is who
*hears* it, and they are independent. Arrears are a finance matter whose audience may be the
admin, the parent, or both — one axis would force every type to describe itself as one or the
other and lose the rest. That is what lets a single type list cover both destinations.

**Adding an alert is a new `AlertDetector` bean plus an `AlertType` constant plus message
keys. Nothing else.** `AlertEngine` injects `List<AlertDetector>` so the context finds it;
there is no second place to remember. A detector describes a fact (`AlertDraft`) and never
writes a sentence or picks a destination — the same split as `NotificationService` vs
`MessageSender`.

**Rules are not seeded by the migration.** `AlertRuleRegistry` builds the list from
`AlertType` on every read and fills missing types with `AlertRule.defaultsFor`. Seeding rows
in `V5` would mean every future type needs its own migration to plant its row, and forgetting
that leaves a type visible in the screen that silently never runs. A row is written the first
time an admin saves a change to it.

**Every rule ships `INTERNAL`.** An upgrade must never make the program start messaging
parents — about money, unprompted, with nobody having asked. `AlertType.isParentCapable()`
says a type *may* be sent; the decision stays with the centre owner, is confirmed explicitly
in the screen, and is written to the audit trail (`ALERT_RULE_UPDATED`).

**Two independent duplicate guards, because they answer different questions.**

- *Inbox*: a cooldown window measured from the last real alert (`findRecentEntityIds`), on top
  of a **unique `dedupe_key`** in the database. A centre has several machines, each running
  its own scheduler, all waking at the same time — without the constraint the same alert is
  written three times and the same message sent three times. The constraint is in the DB, not
  the code, because check-then-insert has a gap two machines fit through exactly. `AlertWriter`
  does the insert in `REQUIRES_NEW`: catching the violation inside the scan's transaction
  would mark it rollback-only and lose every other alert in that scan.
- *Parent messages*: `notification_logs` alone, which **is only written after a send
  succeeds**. The inbox row is written when the condition is found, so using it as the message
  guard would stop retries after a failed send, believing the parent had been told.

`entityId` is `null` for alerts with no subject (a failed backup). It enters the dedupe key as
a literal `-`: a MySQL unique index treats every `NULL` as distinct, so leaving it would let
exactly those alerts repeat without limit.

Nothing translated is stored, same rule as the audit trail: the row holds the type and
language-neutral `args`, and `Alert.describe()` builds the sentence at display time. Currency
symbols live in the message template per language, so amounts are stored as plain
`MoneyUtils.format` output. **Parent templates always take the centre name as `{0}`**, then the
draft's args — a message from an unknown number that does not name its sender reads as a scam.

`AlertScheduler` mirrors `BackupScheduler`, `isOverdue` included: the centre's machine may be
switched on after the scan time every single day, and without catch-up the practical result is
no alert ever while the screen shows the system enabled. `lastAlertScanAt` is written only on
a completed scan, so repeated failure shows as a stale date instead of passing silently.

Neither `AlertEngine` nor `NotificationService.sendAutomatic` is guarded — the scheduler thread
has no `UserSession`, exactly like `BackupService.executeBackup`. The guard belongs on
`AlertService`: who reads the inbox, and who decides the program may speak for the centre.

Two types are event-driven and have no detector, because their moment is known exactly and
their detail does not survive it: `BACKUP_FAILED` (raised from `BackupScheduler`'s catch) and
`PAYMENT_RECEIPT` (raised from `PaymentReceiptListener` on `AFTER_COMMIT` — a message
confirming money cannot be unsent if the transaction later rolls back). Both go through
`AlertEngine.raise`, which never throws: it runs on an exception path or after a successful
sale, and its own error must not replace either.

**Two types run on a short tick, not the daily scan.** `AlertCadence` on `AlertType` says
which. A once-a-day scan cannot express "starts in fifteen minutes": the reminder would land
at 08:00 for a class that meets at 16:00. `AlertScheduler` therefore runs a second, five-minute
`scanFrequent()` — **with no catch-up**, because a reminder replayed two hours late is a false
statement about the present, which is worse than silence.

- `SESSION_STARTING_SOON` — a group is due today and no session is open for it. It goes quiet
  the moment one is opened; without that check it nags the person who already did the work.
  Window is `[start − threshold, start)` only: past the start it is no longer "soon", and
  saying "starts at 16:00" at 16:30 is simply false.
- `SESSION_ENDING_SOON` — an open session at or past its group's `endTime`. No upper bound, so
  it covers both "about to end" and "ended an hour ago and is still open".

**Their dedupe is by occurrence, not by cooldown** (`AlertDraft.occurrence`). The cooldown is
measured in whole days and bucketed, which is the wrong shape for "once for this session": a
group meeting Monday and Tuesday at the same hour is exactly 24 hours apart, so a one-day
window swallows Tuesday's alert on the arithmetic boundary — a miss nobody reports, because a
missing alert is invisible. The occurrence key (`groupId + date + time`, `sessionId + date`)
is exact, and identical on every terminal, which is what lets the unique constraint still do
its job. When a draft carries one, the engine skips the sliding-window check entirely.

That is also what keeps the five-minute tick from becoming spam: a session open for two hours
is scanned 24 times and alerted once. Deliberately once — repeating teaches the user to
dismiss cards unread. The escalation for an ignored reminder is `SESSION_LEFT_OPEN`, which
fires `CRITICAL` in the next morning's scan.

Neither type reaches parents. A session-start reminder *to parents* is a different alert: these
drafts are group-scoped, and a parent message needs a student and a phone number.

#### Getting an alert in front of somebody

Three surfaces, weakest last: the **bell** in the sidebar header (count + dropdown of open
alerts), a **toast** (`util/Toasts`) that slides into the window corner, and a **Windows tray
balloon** (`util/TrayNotifier`). The tray one is the only surface that reaches a user whose app
is minimised — but Focus Assist can swallow it silently, so nothing is ever *only* there.
Everything announced is in the inbox as well.

**`AlertFeed` is the single source, and both paths go through one read.** An alert raised on
this machine publishes `AlertRaisedEvent`, which does not carry the row — it only says "read
now". A second, two-minute poll picks up what *another* terminal's scan wrote, which the event
bus cannot see. Both call `poll()`. If the event carried the row there would be two delivery
paths, each needing its own duplicate guard, and the same alert would appear twice whenever
they raced — which is exactly what a manual scan makes happen.

The guard is `lastSeenId`, **an id and not a timestamp**: the terminals' clocks do not agree,
and a machine running a minute fast would skip alerts its neighbour really did write. `attach`
sets it to the current maximum, so signing in does not replay a week of alerts as popups.

`AlertFeed` owns the timer, not `DashboardController` — the controller is `PROTOTYPE` and is
rebuilt on every language switch, so a timer inside it would outlive its own window and keep
querying until the app closed. The feed holds one sink, so a new screen registering displaces
the old one; `stopAlertFeed()` also runs on logout, since a card about student balances must
not float over the login screen.

`Platform.runLater` sits behind an injectable field (`dispatchOn`) so `AlertFeedTest` can
exercise the duplicate-suppression logic without a JavaFX toolkit — the CI runner has none.
Same reasoning as `Printing.pageBreaks` being JavaFX-free.

**Which alerts actually pop is a per-machine choice** (`util/AlertPreferences`, `java.util.prefs`,
no migration). What is alerted on is centre policy; how loudly it appears is not. The reception
terminal faces parents all day, and a card reading "student X owes 300" exposes it to whoever is
queueing. The default floor is `WARNING`: payment receipts fire dozens of times a day, and a
card for each teaches the user to dismiss cards unread — including the critical one.
`AlertSeverity.isAtLeast` exists so that comparison is written once; the ordinal order is
inverted (`CRITICAL` is lowest) and an inline `>=` would silently mute critical alerts while
still showing notifications, so nothing would look broken.

More than three announceable alerts in one batch collapse into a single summary card. Fifteen
toasts with fifteen tray balloons behind them cover the screen and none of them get read.

`TrayNotifier` is AWT, so `JavaFxApplication` calls `.headless(false)` on the Spring builder —
Boot defaults to headless and `SystemTray.getSystemTray()` throws without it. The tray icon is
installed on the first real notification, not at startup, so a machine with the feature off
carries no dead icon; the icon is drawn in code because the project ships no image asset, and a
customer's print logo is not legible at 16px. Windows balloons have no RTL support, which is
why the text is kept to one short line.

### Money

All amounts are `BigDecimal` with `DECIMAL(12,2)`. Never introduce `Double` for money.
`util/MoneyUtils` owns scale, rounding (`HALF_UP`) and display formatting — route new
display sites through it rather than `String.valueOf`.

**One currency for the centre, chosen in Settings** (`Currency` + `CenterSettings.currency`,
Flyway `V7`). It used to be a single translated string (`app.currency` → `ج.م` / `EGP`),
i.e. the program assumed everyone using it was in Egypt.

The currency is centre policy, not a machine preference like the printer and the language:
the amounts are the same rows in the same database, and letting each terminal pick would
have two people reading one number as two different sums. Null means `Currency.DEFAULT`
(EGP) — an upgraded database carries no value and every amount in it really was pounds.

`MoneyUtils` holds it in a static field that `config/CurrencyInitializer` fills at startup
and refreshes on `SettingsChangedEvent` (after commit). Static for the same reason as
`I18n`: `formatWithCurrency` is called once per table cell and per report line, so reading
the settings row each time is one query per row on screen.

**Changing the currency converts nothing.** 500 pounds becomes 500 riyals with the same
digits, in balances, in dues, and in receipts already printed. That is why the settings
screen confirms it explicitly and `SettingsService.summarize` writes it to the audit trail:
nothing in `transactions` records when the meaning of every row in it changed. Mixed
currencies with exchange rates are a different feature and would need a currency + rate
per transaction; `Currency` says so at the top.

**Never write a currency into a translated string** — `MessageBundleTest.noTranslationHardCodesACurrency`
fails the build for it. The symbol reaches text as an *argument*: last one always, appended
by `Alert.describe()` and `AlertEngine.messageParent` (after the centre name at `{0}`), and
by `AlertType.getThresholdLabel`. Amounts stay stored as bare numbers — same rule as the
audit trail — because a stored symbol freezes on the currency *and* language of the machine
that wrote it.

Adding a currency is a constant in `Currency` plus `currency.<NAME>` and
`currency.<NAME>.symbol` in both bundles. No migration: the column is deliberately
`varchar` rather than the `enum` the schema generator produces for every other constant,
so a new currency is never a database change. Two decimal places are not negotiable per
currency, though — `SCALE` is the shape of every `DECIMAL(12,2)` column, so a three-decimal
currency (dinar) or a zero-decimal one (yen) is a migration over the whole schema, not a
line in the enum.

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

### Groups: level, schedule, membership

A group carries three things beyond its price: the **school level** it serves, the **days and
hours** it meets, and a **name derived from both**. Each exists to stop a different mistake.

**The level is an enrolment gate, not a label.** `SchoolLevel` replaced the free text that the
student screen used to save — it stored the *translated* string it happened to display, so a
student registered on an English terminal could never match a group created on an Arabic one.
`V6` maps both languages' strings onto the constants and changes the column type; anything it
cannot recognise becomes `NULL` rather than being guessed, because a wrong level puts a student
in front of the wrong syllabus. `EnrollmentService.subscribe` refuses a mismatch, a student
with no level, **and a group with no level** — the last one matters: groups created before this
feature have none, and letting them accept everyone would mean the migration silently switched
the constraint off for every existing group.

Screens filter the group list to the student's level as a convenience. The rule is enforced in
the service, same as `@RequiresRole`.

**The gate is at enrolment, so changing a level afterwards walks around it.** Blocking that
would be wrong — every student is promoted a year once a year — so `findEnrolmentsOutsideLevel`
names the live enrolments the new level contradicts and the screen asks for confirmation before
saving. Clearing the level is the same case, not another one (a student with no level fits no
group); a group with no level is excluded, since it contradicts nothing yet. Ending an enrolment
stays a decision the user makes in the enrolments table, never a side effect of saving a form.

**A teacher cannot be in two rooms at once.** `GroupSchedule` is the pure part — day overlap
plus time overlap — kept free of Spring and JPA like `BackupSchedule`, because it is the
decision that silently double-books a person. Two details it encodes: the comparison is scoped
to *one teacher* (parallel rooms at the same hour are the normal case, and forbidding them would
forbid half the timetable), and touching times do not overlap (`<` not `<=`, so a group ending at
six and one starting at six are both valid). `CourseGroupService` skips the row being edited,
without which an existing group conflicts with its own saved copy and its price can never change.

`meetingDays` is a `Set<DayOfWeek>` behind an `AttributeConverter`, not a child table: element
collections are lazy, and this app reads groups **outside** the transaction, so a side table
meant a `LazyInitializationException` on every screen that lists groups. Names, not ordinals,
are stored — `"1,3"` in a backup read a year later says nothing.

**The name is composed, not typed.** `autoName` (default true) rebuilds it from level, teacher,
days and start time on every save, so moving a group to another day cannot leave a name that
lies. It is *stored* rather than composed at display time because the name appears in receipts,
the audit trail and teacher statements — records of what was, not of what is now. An admin who
wants "مجموعة المتفوقين" ticks the custom-name box, and the system stops touching it.

**Membership has two ends.** `StudentGroup.leaveDate` (`null` = ongoing) is what makes "how many
sessions did this student attend" answerable: without it the count is measured against every
session the group ever held, and a student who joined last week reads as absent from twenty
sessions held before the centre knew them. `MembershipRow` counts sessions **inside the
membership window** on both sides of the fraction. Re-joining clears `leaveDate` and reuses the
row — a second row for the same pair would double the group's occupied seats.

The same window answers "how many were enrolled the day this session ran"
(`countEnrolledOn`), which the payout screen shows beside the attendee count. It is
**information, not arithmetic**: the payout stays governed by the teacher's agreement
(percentage, fixed, rent). A membership ended before this feature (no `leaveDate`, inactive) is
excluded from that count rather than assumed still open.

Two printouts, both Jasper sheets: `deliverGroupRoster` for one group (button per table row —
the roster is asked for while looking at its line) and `deliverGroupsList` for whatever the
filters currently show. The filter description is printed on the sheet and **repeats in the
column header on every page**, so neither a page found later nor a second page reads as a list
of all the centre's groups when it is not.

The list's columns are the screen's columns, down to the same `group.col.*` keys: the sheet is
a copy of what was in front of the user when they pressed the button, not a second arrangement
they have to read afresh.

### Sessions

Several sessions may be open at once (parallel rooms). The only constraint is that one group
cannot have two open sessions — `findByGroupAndIsActiveTrue`. The attendance screen either
binds to one session (a terminal serving one room) or infers it from the student's enrolments
via `findActiveForStudent`, reporting ambiguity by name rather than guessing.

`CourseGroup` now carries a weekly timetable, but a `Session` is still opened by hand and has no
time of its own — the timetable says when the group *should* meet, not when it did.

### Entities

Use `@Getter @Setter` plus `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with the id
included — **not** `@Data`. Lombok's `@Data` generates `equals`/`hashCode`/`toString` that
dereference `FetchType.LAZY` associations, causing `LazyInitializationException` outside a
transaction and broken equality against proxies. Repositories use `JOIN FETCH` where results
cross a transaction boundary into the UI.

## Testing

The test classes below exist because these failure modes are invisible to the compiler:

- `@Query` JPQL is parsed at runtime.
- `.jrxml` report templates are compiled at runtime too, and a wrong field name prints an
  empty column rather than failing — `ReportTemplateCompileTest` compiles them all and fills
  one with values it then asserts are on the page.
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
