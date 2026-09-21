/*
 * واجهة الويب: طلبٌ إلى /api، وعرضٌ لما يعود.
 *
 * لا حساب هنا ولا قرار. الرصيد يصل محسوباً ومُنسَّقاً بعملة السنتر، وحالُ الحضور
 * تصل باسمها المترجم، وجملةُ التنبيه تُبنى على الخادم. وهذا ليس تقشّفاً بل هو
 * نفس الخطّ الذي يفصل center-app عن الشاشات: حسابٌ يُكتب مرة ثانية في JS هو
 * حسابٌ يختلف عن الأول يوماً ما، ولا أحد يعرف أيّهما الصحيح.
 */

/** نصوص الشاشة، من /api/messages بلغة الطلب */
let texts = {};

function t(key, ...args) {
    const template = texts[key];
    if (template === undefined) {
        // نفس عرف I18n: مفتاحٌ مفقود يُعرض بين علامتَي تعجّب بدل أن يختفي صامتاً
        return '!' + key + '!';
    }
    return template.replace(/\{(\d+)}/g, (match, index) => {
        const value = args[Number(index)];
        return value === undefined ? match : value;
    });
}

/* ------------------------------------------------------------------ الشبكة */

/**
 * كوكي رمز CSRF يقرؤه JS ويعيده في ترويسة.
 *
 * نطاقٌ آخر يستطيع أن يُطلق طلباً يحمل كوكي جلستنا، ولا يستطيع قراءة كوكينا -
 * فلا يستطيع بناء هذه الترويسة. وهذا هو كل الحاجز.
 */
function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

async function call(method, url, body) {
    const headers = {};
    const token = csrfToken();
    if (token) {
        headers['X-XSRF-TOKEN'] = token;
    }
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(url, {
        method,
        headers,
        credentials: 'same-origin',
        body: body === undefined ? undefined : JSON.stringify(body)
    });

    if (response.status === 204) {
        return null;
    }

    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        // الرسالة تأتي مترجمةً من الخادم؛ الرمز وحده لا يقول للموظف شيئاً
        throw new Error(payload && payload.message ? payload.message : String(response.status));
    }
    return payload;
}

const get = (url) => call('GET', url);
const post = (url, body) => call('POST', url, body);

/* ------------------------------------------------------------------ العرض */

function applyTexts(root) {
    root.querySelectorAll('[data-text]').forEach((node) => {
        node.textContent = t(node.dataset.text);
    });
    root.querySelectorAll('[data-placeholder]').forEach((node) => {
        node.placeholder = t(node.dataset.placeholder);
    });
}

function show(id, message, isError) {
    const node = document.getElementById(id);
    node.textContent = message || '';
    node.classList.toggle('bad', Boolean(isError));
}

function report(error) {
    show('appError', error.message, true);
}

/** جدولٌ من صفوف: الرؤوس مفاتيح، والخلايا نصوصٌ جاهزة */
function table(id, headerKeys, rows, cells, emptyKey, action) {
    const node = document.getElementById(id);
    node.innerHTML = '';

    if (!rows.length) {
        const empty = document.createElement('caption');
        empty.textContent = t(emptyKey);
        node.appendChild(empty);
        return;
    }

    const head = node.createTHead().insertRow();
    headerKeys.forEach((key) => {
        const cell = document.createElement('th');
        cell.textContent = t(key);
        head.appendChild(cell);
    });
    if (action) {
        head.appendChild(document.createElement('th'));
    }

    const body = node.createTBody();
    rows.forEach((row) => {
        const line = body.insertRow();
        cells(row).forEach((value) => {
            // textContent لا innerHTML: اسمُ طالبٍ فيه أقواس زاوية نصٌّ لا وسم
            line.insertCell().textContent = value === null || value === undefined ? t('web.common.none') : value;
        });
        if (action) {
            // الزرّ يُبنى على الصفّ الذي أمام العين: الإغلاق يُطلب وأنت تنظر إلى سطره،
            // لا بعد أن تكتب رقماً في حقل بعيد عنه
            const button = action(row);
            const cell = line.insertCell();
            if (button) {
                cell.appendChild(button);
            }
        }
    });
}

/** الوقت وحده من ختمٍ كامل: الجدول يعرض يوماً واحداً، والتاريخ فيه مكرَّر */
function clockOf(timestamp) {
    return timestamp ? timestamp.substring(11, 16) : null;
}

function today() {
    return new Date().toISOString().substring(0, 10);
}

/* ------------------------------------------------------------------ الشاشات */

const views = ['day', 'attendance', 'till', 'students', 'reports'];

function openView(name) {
    views.forEach((view) => {
        document.getElementById('view-' + view).classList.toggle('hidden', view !== name);
    });
    document.querySelectorAll('nav button').forEach((button) => {
        button.classList.toggle('active', button.dataset.view === name);
    });
    if (name === 'day') {
        loadDay().catch(report);
    } else if (name === 'attendance') {
        loadAttendance().catch(report);
    } else if (name === 'till') {
        loadTill().catch(report);
    } else if (name === 'students') {
        loadStudents().catch(report);
    }
}

/* ------------------------------------------------------------------ الحضور */

async function loadAttendance() {
    const sessions = await get('/api/class-sessions?open=true');
    const select = document.getElementById('sessionId');
    select.innerHTML = '';

    const any = document.createElement('option');
    any.value = '';
    any.textContent = t('web.attendance.anySession');
    select.appendChild(any);

    sessions.forEach((session) => {
        const option = document.createElement('option');
        option.value = session.id;
        option.textContent = session.groupName;
        select.appendChild(option);
    });

    await refreshAttendanceLog();
}

async function refreshAttendanceLog() {
    const rows = await get('/api/attendance/today');
    table('attendanceTable',
        ['web.attendance.col.name', 'web.attendance.col.barcode', 'web.attendance.col.group',
            'web.attendance.col.timeIn', 'web.attendance.col.timeOut', 'web.attendance.col.state'],
        rows,
        (row) => [row.studentName, row.barcode, row.groupName,
            clockOf(row.timeIn), clockOf(row.timeOut), row.stateName],
        'web.attendance.empty');
}

/* ------------------------------------------------------------ اليوم والحصص */

async function loadDay() {
    const day = document.getElementById('dayDate');
    if (!day.value) {
        day.value = today();
    }
    const sessionDate = document.getElementById('sessionDate');
    if (!sessionDate.value) {
        sessionDate.value = today();
    }

    const groups = await get('/api/groups');
    const select = document.getElementById('sessionGroup');
    select.innerHTML = '';
    groups.forEach((group) => {
        const option = document.createElement('option');
        option.value = group.id;
        option.textContent = group.name;
        select.appendChild(option);
    });

    await Promise.all([refreshDay(), refreshSessions()]);
}

async function refreshDay() {
    const date = document.getElementById('dayDate').value || today();
    const day = await get('/api/day-schedule?date=' + date);

    show('dayBrief', t('web.day.brief', day.brief.total, day.brief.open,
        day.brief.notOpened, day.brief.closed), false);
    show('dayNext', day.brief.nextGroupName
        ? t('web.day.next', day.brief.nextGroupName, day.brief.nextStartTime)
        : t('web.day.noNext'), false);

    table('dayTable',
        ['web.day.col.group', 'web.day.col.teacher', 'web.day.col.level',
            'web.day.col.scheduled', 'web.day.col.status', 'web.day.col.attendance'],
        day.rows,
        (row) => [row.groupName, row.teacherName, row.level, row.scheduledTime,
            row.status, row.attendance],
        'web.day.empty');
}

async function refreshSessions() {
    const openOnly = document.getElementById('openOnly').checked;
    const sessions = await get('/api/class-sessions' + (openOnly ? '?open=true' : ''));

    table('sessionTable',
        ['web.sessions.col.group', 'web.sessions.col.teacher', 'web.sessions.col.date',
            'web.sessions.col.startedAt', 'web.sessions.col.endedAt', 'web.sessions.col.state'],
        sessions,
        (row) => [row.groupName, row.teacherName, row.date,
            clockOf(row.startedAt), clockOf(row.endedAt),
            t(row.open ? 'web.sessions.state.open' : 'web.sessions.state.closed')],
        'web.sessions.empty',
        (row) => {
            if (!row.open) {
                return null;
            }
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = t('web.sessions.close');
            button.addEventListener('click', () => closeSession(row.id));
            return button;
        });
}

async function closeSession(id) {
    try {
        await post('/api/class-sessions/' + id + '/close');
        show('sessionResult', '', false);
        await Promise.all([refreshSessions(), refreshDay()]);
    } catch (error) {
        show('sessionResult', error.message, true);
    }
}

/* ------------------------------------------------------------------ الخزينة */

async function loadTill() {
    const date = document.getElementById('tillDate');
    if (!date.value) {
        date.value = today();
    }
    await refreshTill();
}

async function refreshTill() {
    const date = document.getElementById('tillDate').value || today();
    const [summary, movements] = await Promise.all([
        get('/api/till/summary?date=' + date),
        get('/api/till/day?date=' + date)
    ]);

    show('tillSummary', [
        t('web.till.income') + ': ' + summary.formattedIncome,
        t('web.till.expenses') + ': ' + summary.formattedExpense,
        t('web.till.payouts') + ': ' + summary.formattedPayouts,
        t('web.till.net') + ': ' + summary.formattedNet
    ].join('   -   '), false);

    table('tillTable',
        ['web.till.col.time', 'web.till.col.type', 'web.till.col.amount', 'web.till.col.description'],
        movements,
        (row) => [clockOf(row.at), row.typeName, row.formatted, row.description],
        'web.till.empty');
}

async function loadEnrolments() {
    const barcode = document.getElementById('payBarcode').value.trim();
    if (!barcode) {
        return;
    }
    const groups = await get('/api/till/enrolments?barcode=' + encodeURIComponent(barcode));
    const select = document.getElementById('payGroup');
    select.innerHTML = '';

    const none = document.createElement('option');
    none.value = '';
    none.textContent = t('web.till.noGroup');
    select.appendChild(none);

    groups.forEach((group) => {
        const option = document.createElement('option');
        option.value = group.groupId;
        option.textContent = group.groupName;
        select.appendChild(option);
    });
}

/* ------------------------------------------------------------------ الطلاب */

async function loadStudents() {
    const query = document.getElementById('studentQuery').value.trim();
    const rows = await get('/api/students?query=' + encodeURIComponent(query));
    table('studentTable',
        ['web.students.col.name', 'web.students.col.barcode', 'web.students.col.phone',
            'web.students.col.parentPhone', 'web.students.col.level'],
        rows,
        (row) => [row.name, row.barcode, row.phone, row.parentPhone, row.level],
        'web.students.empty');
}

/* ------------------------------------------------------------------ التقارير */

function refreshReportLinks() {
    const from = document.getElementById('reportFrom').value || today();
    const to = document.getElementById('reportTo').value || today();
    document.getElementById('arrearsPdf').href = '/api/reports/arrears.pdf';
    document.getElementById('shiftPdf').href = '/api/reports/shift.pdf?date=' + to;
    document.getElementById('attendanceLogPdf').href =
        '/api/reports/attendance-log.pdf?from=' + from + '&to=' + to;
}

/* ------------------------------------------------------------------ التنبيهات */

let stream = null;

/**
 * مجرى التنبيهات.
 *
 * EventSource يعيد الاتصال بنفسه حين ينقطع - وهو سبب اختيار SSE أصلاً - فلا
 * حلقةَ إعادةِ محاولةٍ هنا. ويُغلق عند الخروج، وإلا بقي مفتوحاً على شاشة
 * الدخول يحمل تنبيهات عن أرصدة طلاب إلى من لم يعد داخلاً.
 */
function openAlertStream() {
    closeAlertStream();
    stream = new EventSource('/api/alerts/stream');
    stream.addEventListener('alerts', (event) => {
        const batch = JSON.parse(event.data);
        const badge = document.getElementById('alertCount');
        badge.hidden = batch.openCount === 0;
        badge.textContent = t('web.alerts.open', batch.openCount);

        const list = document.getElementById('alertList');
        if (!batch.fresh.length && !list.childElementCount) {
            const empty = document.createElement('li');
            empty.textContent = t('web.alerts.none');
            list.appendChild(empty);
            return;
        }
        batch.fresh.forEach((alert) => {
            const line = document.createElement('li');
            line.className = 'alert ' + alert.severity;
            line.textContent = alert.text;
            list.prepend(line);
        });
    });
}

function closeAlertStream() {
    if (stream) {
        stream.close();
        stream = null;
    }
}

/* ------------------------------------------------------------------ الدخول */

function enterApp(me) {
    document.getElementById('signIn').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
    document.getElementById('who').textContent = me.username + ' - ' + me.roleName;
    refreshReportLinks();
    openView('day');
    openAlertStream();
}

function leaveApp() {
    closeAlertStream();
    document.getElementById('app').classList.add('hidden');
    document.getElementById('signIn').classList.remove('hidden');
    document.getElementById('alertList').innerHTML = '';
}

async function loadMessages() {
    const bundle = await get('/api/messages');
    texts = bundle.texts;
    document.documentElement.lang = bundle.locale;
    document.documentElement.dir = bundle.rightToLeft ? 'rtl' : 'ltr';
    applyTexts(document);
}

/* ------------------------------------------------------------------ التركيب */

document.addEventListener('DOMContentLoaded', async () => {
    await loadMessages();

    document.getElementById('loginForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        show('loginError', '', false);
        try {
            enterApp(await post('/api/session', {
                centre: document.getElementById('centre').value.trim(),
                username: document.getElementById('username').value.trim(),
                password: document.getElementById('password').value
            }));
        } catch (error) {
            show('loginError', error.message, true);
        }
    });

    document.getElementById('signOut').addEventListener('click', async () => {
        try {
            await call('DELETE', '/api/session');
        } finally {
            leaveApp();
        }
    });

    document.querySelectorAll('nav button').forEach((button) => {
        button.addEventListener('click', () => openView(button.dataset.view));
    });

    document.getElementById('dayForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshDay().catch(report);
    });

    document.getElementById('sessionFilterForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshSessions().catch(report);
    });

    document.getElementById('openSessionForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const group = document.getElementById('sessionGroup').value;
        try {
            const opened = await post('/api/class-sessions', {
                groupId: group ? Number(group) : null,
                date: document.getElementById('sessionDate').value || null
            });
            show('sessionResult', opened.groupName + '   -   ' + opened.date, false);
            await Promise.all([refreshSessions(), refreshDay()]);
        } catch (error) {
            show('sessionResult', error.message, true);
        }
    });

    document.getElementById('scanForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const barcode = document.getElementById('barcode');
        const sessionId = document.getElementById('sessionId').value;
        try {
            const result = await post('/api/attendance/scan', {
                barcode: barcode.value.trim(),
                sessionId: sessionId ? Number(sessionId) : null
            });
            show('scanResult', [result.studentName, result.message, result.remainingBalance]
                .filter(Boolean).join('   -   '), !result.success);
            await refreshAttendanceLog();
        } catch (error) {
            show('scanResult', error.message, true);
        } finally {
            // الحقل يُفرَّغ ويستعيد التركيز: القارئ يكتب في أيّ حقل عليه المؤشر،
            // وباركودٌ باقٍ من التمريرة السابقة يُرسَل ملتصقاً بالتالي
            barcode.value = '';
            barcode.focus();
        }
    });

    document.getElementById('loadEnrolments')
        .addEventListener('click', () => loadEnrolments().catch(report));

    document.getElementById('payForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const group = document.getElementById('payGroup').value;
        try {
            const result = await post('/api/till/payments', {
                barcode: document.getElementById('payBarcode').value.trim(),
                groupId: group ? Number(group) : null,
                amount: document.getElementById('payAmount').value,
                description: document.getElementById('payDescription').value
            });
            show('tillResult', result.studentName + '   -   '
                + t('web.students.balance') + ': ' + result.formattedBalance, false);
            await refreshTill();
        } catch (error) {
            show('tillResult', error.message, true);
        }
    });

    document.getElementById('expenseForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            await post('/api/till/expenses', {
                amount: document.getElementById('expenseAmount').value,
                description: document.getElementById('expenseDescription').value
            });
            show('tillResult', '', false);
            await refreshTill();
        } catch (error) {
            show('tillResult', error.message, true);
        }
    });

    document.getElementById('tillDayForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshTill().catch(report);
    });

    document.getElementById('studentForm').addEventListener('submit', (event) => {
        event.preventDefault();
        loadStudents().catch(report);
    });

    document.getElementById('reportForm').addEventListener('input', refreshReportLinks);

    // جلسةٌ ما زالت قائمة من تحميل سابق: الصفحة تُفتح على الشاشة لا على الدخول
    try {
        enterApp(await get('/api/me'));
    } catch (ignored) {
        // لا جلسة؛ شاشة الدخول هي المعروضة أصلاً
    }
});
