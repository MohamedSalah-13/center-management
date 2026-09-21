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
const put = (url, body) => call('PUT', url, body);
const remove = (url) => call('DELETE', url);

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
            // الأزرار تُبنى على الصفّ الذي أمام العين: التعديل والأرشفة يُطلبان وأنت
            // تنظر إلى سطر صاحبهما، لا بعد أن تكتب رقماً في حقل بعيد عنه
            const cell = line.insertCell();
            const built = action(row);
            (Array.isArray(built) ? built : [built])
                .filter(Boolean)
                .forEach((element) => cell.appendChild(element));
        }
    });
}

/** زرّ صفٍّ بنصّه المترجَم */
function button(key, onClick) {
    const node = document.createElement('button');
    node.type = 'button';
    node.textContent = t(key);
    node.addEventListener('click', onClick);
    return node;
}

/**
 * ملء قائمة اختيار.
 *
 * القيمة هي الثابت والنصُّ هو المترجَم، دائماً: قائمةٌ قيمتها نصٌّ معروض تُرسِل إلى
 * الخادم ما يتغيّر بلغة من يقف أمام الشاشة.
 */
function fill(select, options, selected, emptyLabel) {
    select.innerHTML = '';
    if (emptyLabel !== undefined) {
        const none = document.createElement('option');
        none.value = '';
        none.textContent = emptyLabel;
        select.appendChild(none);
    }
    options.forEach((option) => {
        const node = document.createElement('option');
        node.value = option.value;
        node.textContent = option.label;
        select.appendChild(node);
    });
    select.value = selected === null || selected === undefined ? '' : String(selected);
}

/** "12 / 20" - رقمان بلا نصّ، فلا يحتاجان ترجمة */
function fraction(attended, held) {
    return attended + ' / ' + held;
}

/** الوقت وحده من ختمٍ كامل: الجدول يعرض يوماً واحداً، والتاريخ فيه مكرَّر */
function clockOf(timestamp) {
    return timestamp ? timestamp.substring(11, 16) : null;
}

/** اليوم وحده من ختمٍ كامل: الجدول يعرض فترة، والوقت في عموده */
function dayOf(timestamp) {
    return timestamp ? timestamp.substring(0, 10) : null;
}

/** الختم كاملاً بلا ثوانٍ: سجلُّ الحركات يمتدّ على شهور، فاليوم جزءٌ من الجواب */
function stampOf(timestamp) {
    return timestamp ? timestamp.substring(0, 10) + ' ' + timestamp.substring(11, 16) : null;
}

function today() {
    return new Date().toISOString().substring(0, 10);
}

/** أول الشهر الجاري: المدى الافتراضي لكشف المصروفات هو الشهر الذي يُراجَع */
function monthStart() {
    return today().substring(0, 8) + '01';
}

/* ------------------------------------------------------------------ الشاشات */

const views = ['day', 'attendance', 'till', 'students', 'groups', 'finance', 'admin', 'alerts', 'reports'];

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
    } else if (name === 'groups') {
        loadGroups().catch(report);
    } else if (name === 'finance') {
        loadFinance().catch(report);
    } else if (name === 'admin') {
        loadAdmin().catch(report);
    } else if (name === 'alerts') {
        loadAlerts().catch(report);
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

/** الصفوف الدراسية كما تصل من الخادم: الثابت يُرسَل والمترجَم يُعرض */
let levels = [];

/** الطالب المعروض في لوحة الاشتراكات - لا في النموذج: اللوحتان تُفتحان معاً */
let enrolmentSubject = null;

async function loadStudents() {
    if (!levels.length) {
        levels = await get('/api/students/levels');
        fill(document.getElementById('studentLevel'),
            levels.map((level) => ({value: level.name, label: level.label})),
            null, t('web.common.none'));
    }
    await refreshStudents();
}

async function refreshStudents() {
    const query = document.getElementById('studentQuery').value.trim();
    const archived = document.getElementById('includeArchived').checked;
    const rows = await get('/api/students?includeArchived=' + archived
        + '&query=' + encodeURIComponent(query));

    table('studentTable',
        ['web.students.col.name', 'web.students.col.barcode', 'web.students.col.phone',
            'web.students.col.parentPhone', 'web.students.col.level', 'web.students.col.state'],
        rows,
        (row) => [row.name, row.barcode, row.phone, row.parentPhone, row.level,
            t(row.active ? 'web.students.state.active' : 'web.students.state.archived')],
        'web.students.empty',
        (row) => [
            button('web.students.edit', () => editStudent(row)),
            button('web.students.details', () => showStudent(row)),
            button(row.active ? 'web.students.archive' : 'web.students.restore',
                () => setArchived(row)),
            button('web.students.delete', () => deleteStudent(row))
        ]);
}

function editStudent(row) {
    document.getElementById('studentId').value = row.id;
    // الصفُّ كما هو محفوظ: السؤال عن الاشتراكات المخالفة يُطرح حين يتغيّر وحده،
    // وطالبٌ له مخالفةٌ قديمة - من قبل هذه الميزة - يُسأل عنها كلما حُفظ هاتفه
    document.getElementById('studentLevelBefore').value = row.level || '';
    document.getElementById('studentName').value = row.name || '';
    document.getElementById('studentBarcode').value = row.barcode || '';
    document.getElementById('studentPhone').value = row.phone || '';
    document.getElementById('studentParentPhone').value = row.parentPhone || '';
    // الصفُّ يُطابَق بالاسم المترجَم لأن القائمة لا تحمل غيره في جدول الطلاب
    const level = levels.find((candidate) => candidate.label === row.level);
    document.getElementById('studentLevel').value = level ? level.name : '';
    show('studentResult', '', false);
}

function clearStudentForm() {
    document.getElementById('studentId').value = '';
    document.getElementById('studentLevelBefore').value = '';
    ['studentName', 'studentBarcode', 'studentPhone', 'studentParentPhone']
        .forEach((id) => { document.getElementById(id).value = ''; });
    document.getElementById('studentLevel').value = '';
}

/**
 * الحفظ، ومعه تأكيدُ تغيير الصف.
 *
 * قيدُ الصف يُفحص عند الاشتراك وحده، فتغييره بعده يتجاوزه باباً خلفياً. والمنع خطأ -
 * الترقية في أول العام تقع لكل طالب مرة كل سنة - فالمخرج أن يُرى الأثر قبل الحفظ،
 * كما تفعل شاشة سطح المكتب. وصفحةٌ بلا هذا السؤال تكون قد ألغت القيد لمن يستعمل الويب.
 */
async function saveStudent() {
    const id = document.getElementById('studentId').value;
    const level = document.getElementById('studentLevel').value;
    const body = {
        name: document.getElementById('studentName').value.trim(),
        barcode: document.getElementById('studentBarcode').value.trim(),
        phone: document.getElementById('studentPhone').value.trim(),
        parentPhone: document.getElementById('studentParentPhone').value.trim(),
        schoolLevel: level || null
    };

    const before = levels.find(
        (candidate) => candidate.label === document.getElementById('studentLevelBefore').value);
    const levelChanged = (before ? before.name : '') !== level;

    if (id && levelChanged && !await levelChangeAccepted(id, body.name, level)) {
        return;
    }

    const saved = id ? await put('/api/students/' + id, body) : await post('/api/students', body);
    show('studentResult', t('web.students.saved', saved.name), false);
    clearStudentForm();
    await refreshStudents();
    if (enrolmentSubject && enrolmentSubject.id === saved.id) {
        await showEnrolments(saved);
    }
}

async function levelChangeAccepted(id, name, level) {
    const clashes = await get('/api/students/' + id + '/level-clashes'
        + (level ? '?level=' + encodeURIComponent(level) : ''));
    if (!clashes.length) {
        return true;
    }
    const chosen = levels.find((candidate) => candidate.name === level);
    const lines = clashes
        .map((clash) => t('web.students.levelChangeGroup', clash.groupName, clash.level))
        .join('\n');
    return window.confirm(t('web.students.levelChangeWarning', name,
        chosen ? chosen.label : t('web.common.none'), lines));
}

async function setArchived(row) {
    try {
        if (row.active && !window.confirm(t('web.students.confirmArchive', row.name))) {
            return;
        }
        await post('/api/students/' + row.id + (row.active ? '/archive' : '/restore'));
        show('studentResult', '', false);
        await refreshStudents();
    } catch (error) {
        show('studentResult', error.message, true);
    }
}

async function deleteStudent(row) {
    try {
        if (!window.confirm(t('web.students.confirmDelete', row.name))) {
            return;
        }
        await remove('/api/students/' + row.id);
        show('studentResult', '', false);
        clearStudentForm();
        await refreshStudents();
    } catch (error) {
        // الرسالة تقول إن له حضوراً أو حركات وتدلّ على الأرشفة - وهي تأتي من الخدمة
        show('studentResult', error.message, true);
    }
}

/* ------------------------------------------------------------- الاشتراكات */

/**
 * لوحتا الطالب تُفتحان معاً بموضوعٍ واحد.
 *
 * <p>زرّان لكلٍّ موضوعُه يجعلان الشاشة تعرض اشتراكات طالبٍ وحركاتِ آخر في وقتٍ واحد،
 * وهو تناقضٌ لا يقول عن نفسه شيئاً. وكلٌّ منهما يُمسك خطأه وحده: قراءةٌ تفشل لا تُفرّغ
 * الأخرى.</p>
 */
function showStudent(student) {
    showEnrolments(student).catch((error) => show('enrolResult', error.message, true));
    showPayments(student).catch((error) => show('paymentSummary', error.message, true));
}

async function showEnrolments(student) {
    enrolmentSubject = student;
    show('enrolmentSubject', t('web.enrolments.for', student.name), false);
    show('enrolResult', '', false);

    // مجموعات صف الطالب وحدها: القيد يُفرض في الخدمة، وعرضُ ما سيُرفض إرباكٌ بلا فائدة
    const level = levels.find((candidate) => candidate.label === student.level);
    const groups = level ? await get('/api/groups?level=' + encodeURIComponent(level.name)) : [];
    fill(document.getElementById('enrolGroup'),
        groups.map((group) => ({value: group.id, label: group.name})),
        null, groups.length ? t('web.common.none') : t('web.enrolments.noGroups'));

    await refreshEnrolments();
}

async function refreshEnrolments() {
    if (!enrolmentSubject) {
        show('enrolmentSubject', t('web.enrolments.pickStudent'), false);
        table('enrolmentTable', [], [], () => [], 'web.enrolments.empty');
        return;
    }

    const rows = await get('/api/students/' + enrolmentSubject.id + '/enrollments');
    table('enrolmentTable',
        ['web.enrolments.col.group', 'web.enrolments.col.joinDate', 'web.enrolments.col.leaveDate',
            'web.enrolments.col.state', 'web.enrolments.col.sessions', 'web.enrolments.col.rate'],
        rows,
        (row) => [row.groupName, row.joinDate, row.leaveDate,
            t(row.active ? 'web.enrolments.state.active' : 'web.enrolments.state.ended'),
            fraction(row.sessionsAttended, row.sessionsHeld),
            row.attendanceRate === null ? null : row.attendanceRate + '%'],
        'web.enrolments.empty',
        // المنتهي لا زرّ له: إنهاؤه وقع، وإعادتُه اشتراكٌ جديد من القائمة أعلاه
        (row) => row.active ? button('web.enrolments.end', () => endEnrolment(row)) : null);
}

async function endEnrolment(row) {
    try {
        if (!window.confirm(t('web.enrolments.confirmEnd', enrolmentSubject.name, row.groupName))) {
            return;
        }
        await remove('/api/students/' + enrolmentSubject.id + '/enrollments/' + row.groupId);
        show('enrolResult', '', false);
        await refreshEnrolments();
    } catch (error) {
        show('enrolResult', error.message, true);
    }
}

/* --------------------------------------------------------- حركات الطالب */

/**
 * سجلُّ حركاته، وهو غير رصيده.
 *
 * <p>الرصيد رقمٌ يقول "كم عليه الآن"، وهذا يقول "من أين جاء": الدفعات ورسومُ الحصص
 * معاً، وكلُّ صفّ يحمل نوعه. وقائمةٌ لا يُعرف فيها الداخلُ من الخارج تُقرأ مدفوعاتٍ
 * كلَّها، فتصير خصوم الحصص دفعاتٍ في عين من ينظر.</p>
 */
async function showPayments(student) {
    const history = await get('/api/students/' + student.id + '/payments');

    show('paymentSummary', t('web.payments.for', student.name) + '   -   '
        + t('web.payments.summary', history.formattedPaid, history.formattedCharged,
            history.formattedBalance), false);

    table('paymentTable',
        ['web.payments.col.date', 'web.payments.col.type', 'web.payments.col.amount',
            'web.payments.col.group', 'web.payments.col.session', 'web.payments.col.description'],
        history.rows,
        (row) => [stampOf(row.at), row.typeName, row.formatted,
            row.groupName, row.sessionDate, row.description],
        'web.payments.empty');
}

/* ------------------------------------------------------------------ المجموعات */

let days = [];
let teachers = [];

async function loadGroups() {
    if (!days.length) {
        days = await get('/api/groups/days');
        const box = document.getElementById('groupDays');
        box.innerHTML = '';
        days.forEach((day) => {
            const label = document.createElement('label');
            const check = document.createElement('input');
            check.type = 'checkbox';
            check.value = day.name;
            label.appendChild(check);
            label.appendChild(document.createTextNode(' ' + day.label));
            box.appendChild(label);
        });
    }
    if (!levels.length) {
        levels = await get('/api/students/levels');
    }
    fill(document.getElementById('groupLevel'),
        levels.map((level) => ({value: level.name, label: level.label})),
        document.getElementById('groupLevel').value || null, t('web.common.none'));

    teachers = await get('/api/teachers');
    fill(document.getElementById('groupTeacher'),
        teachers.map((teacher) => ({value: teacher.id, label: teacher.name})),
        document.getElementById('groupTeacher').value || null, t('web.common.none'));

    await refreshGroups();
}

async function refreshGroups() {
    const rows = await get('/api/groups');
    table('groupTable',
        ['web.groups.col.name', 'web.groups.col.teacher', 'web.groups.col.level',
            'web.groups.col.days', 'web.groups.col.time', 'web.groups.col.price',
            'web.groups.col.capacity', 'web.groups.col.members'],
        rows,
        (row) => [row.name, row.teacherName, row.level, row.meetingDays,
            row.startTime && row.endTime ? row.startTime + ' - ' + row.endTime : null,
            row.price, row.maxCapacity, row.members],
        'web.groups.empty',
        (row) => [
            button('web.groups.edit', () => editGroup(row)),
            button('web.groups.roster', () => showRoster(row).catch(report)),
            button('web.groups.delete', () => deleteGroup(row))
        ]);
}

function editGroup(row) {
    document.getElementById('groupId').value = row.id;
    document.getElementById('groupTeacher').value = row.teacherId === null ? '' : row.teacherId;
    document.getElementById('groupLevel').value = row.levelName || '';
    document.getElementById('groupCapacity').value = row.maxCapacity === null ? '' : row.maxCapacity;
    document.getElementById('groupPrice').value = row.sessionPrice === null ? '' : row.sessionPrice;
    document.getElementById('groupStart').value = row.startTime ? row.startTime.substring(0, 5) : '';
    document.getElementById('groupEnd').value = row.endTime ? row.endTime.substring(0, 5) : '';
    checkedDays(row.dayNames || []);
    setCustomName(!row.autoName, row.name);
    show('groupResult', '', false);
}

function checkedDays(names) {
    document.querySelectorAll('#groupDays input').forEach((box) => {
        box.checked = names.includes(box.value);
    });
}

function selectedDays() {
    return Array.from(document.querySelectorAll('#groupDays input'))
        .filter((box) => box.checked)
        .map((box) => box.value);
}

/**
 * حقل الاسم يتبع المربع.
 *
 * اسمٌ يكتبه المستخدم يعني إيقاف الاشتقاق، والحقلان يتحركان معاً دائماً: حقلٌ مفتوح
 * والاشتقاق شغّال يعني أن ما كُتب فيه يُمحى عند الحفظ بلا أن يقول أحد شيئاً.
 */
function setCustomName(custom, name) {
    const check = document.getElementById('groupCustomName');
    const field = document.getElementById('groupName');
    check.checked = custom;
    field.disabled = !custom;
    field.value = custom ? (name || '') : '';
}

function clearGroupForm() {
    document.getElementById('groupId').value = '';
    ['groupCapacity', 'groupPrice', 'groupStart', 'groupEnd']
        .forEach((id) => { document.getElementById(id).value = ''; });
    document.getElementById('groupTeacher').value = '';
    document.getElementById('groupLevel').value = '';
    checkedDays([]);
    setCustomName(false, '');
}

async function saveGroup() {
    const id = document.getElementById('groupId').value;
    const teacher = document.getElementById('groupTeacher').value;
    const level = document.getElementById('groupLevel').value;
    const price = document.getElementById('groupPrice').value;
    const capacity = document.getElementById('groupCapacity').value;
    const custom = document.getElementById('groupCustomName').checked;

    const body = {
        teacherId: teacher ? Number(teacher) : null,
        schoolLevel: level || null,
        maxCapacity: capacity ? Number(capacity) : null,
        sessionPrice: price === '' ? null : price,
        meetingDays: selectedDays(),
        startTime: document.getElementById('groupStart').value || null,
        endTime: document.getElementById('groupEnd').value || null,
        autoName: !custom,
        name: custom ? document.getElementById('groupName').value.trim() : null
    };

    const saved = id ? await put('/api/groups/' + id, body) : await post('/api/groups', body);
    show('groupResult', t('web.groups.saved', saved.name), false);
    clearGroupForm();
    await refreshGroups();
}

async function deleteGroup(row) {
    try {
        if (!window.confirm(t('web.groups.confirmDelete', row.name))) {
            return;
        }
        await remove('/api/groups/' + row.id);
        show('groupResult', '', false);
        clearGroupForm();
        await refreshGroups();
    } catch (error) {
        show('groupResult', error.message, true);
    }
}

async function showRoster(group) {
    document.getElementById('rosterTitle').textContent = t('web.groups.rosterTitle', group.name);
    const rows = await get('/api/groups/' + group.id + '/roster');
    table('rosterTable',
        ['web.groups.roster.col.name', 'web.groups.roster.col.barcode',
            'web.groups.roster.col.phone', 'web.groups.roster.col.parentPhone',
            'web.groups.roster.col.joinDate', 'web.groups.roster.col.sessions',
            'web.groups.roster.col.rate'],
        rows,
        (row) => [row.studentName, row.barcode, row.phone, row.parentPhone, row.joinDate,
            fraction(row.sessionsAttended, row.sessionsHeld),
            row.attendanceRate === null ? null : row.attendanceRate + '%'],
        'web.groups.roster.empty');
}

/* ------------------------------------------------------------------ المال */

async function loadFinance() {
    const from = document.getElementById('expenseFrom');
    const to = document.getElementById('expenseTo');
    if (!from.value) {
        from.value = monthStart();
    }
    if (!to.value) {
        to.value = today();
    }

    teachers = await get('/api/teachers');
    fill(document.getElementById('payoutTeacher'),
        teachers.map((teacher) => ({value: teacher.id, label: teacher.name})),
        document.getElementById('payoutTeacher').value || null, t('web.common.all'));

    await Promise.all([refreshExpenses(), refreshPayouts()]);
}

/**
 * الإجمالي والورقة يخرجان من القائمة نفسها، والخادم هو من يصفّي.
 *
 * <p>من يبحث عن "كهرباء" ثم يقرأ إجمالياً يشمل كل المصروفات ينسب مصروفات الشهر كلها
 * إلى فاتورة الكهرباء. ولذلك تحمل الورقة نفس المُعاملات التي حملها هذا الطلب.</p>
 */
async function refreshExpenses() {
    const scope = expenseScope();
    const report = await get('/api/expenses?' + scope);
    document.getElementById('expensesPdf').href = '/api/reports/expenses.pdf?' + scope;

    show('expenseSummary', report.scope + '   -   '
        + t('web.expenses.summary', report.rows.length, report.formattedTotal,
            report.formattedLargest), false);

    table('expenseTable',
        ['web.expenses.col.date', 'web.expenses.col.time',
            'web.expenses.col.description', 'web.expenses.col.amount'],
        report.rows,
        (row) => [dayOf(row.at), clockOf(row.at), row.description, row.formatted],
        'web.expenses.empty');
}

function expenseScope() {
    const from = document.getElementById('expenseFrom').value || today();
    const to = document.getElementById('expenseTo').value || today();
    const query = document.getElementById('expenseQuery').value.trim();
    return 'from=' + from + '&to=' + to + '&query=' + encodeURIComponent(query);
}

async function refreshPayouts() {
    const teacher = document.getElementById('payoutTeacher').value;
    const statement = document.getElementById('statementPdf');
    // كشفُ حساب بلا معلم لا معنى له: الرابط يُعطَّل بدل أن يفتح صفحةَ خطأ
    statement.href = teacher ? '/api/reports/teacher-statement.pdf?teacherId=' + teacher : '#';
    statement.title = teacher ? '' : t('web.payouts.pickTeacher');

    const rows = await get('/api/teacher-payouts' + (teacher ? '?teacherId=' + teacher : ''));
    table('payoutTable',
        ['web.payouts.col.date', 'web.payouts.col.group', 'web.payouts.col.teacher',
            'web.payouts.col.attendance', 'web.payouts.col.commission',
            'web.payouts.col.revenue', 'web.payouts.col.payout'],
        rows,
        (row) => [row.sessionDate, row.groupName, row.teacherName,
            fraction(row.attendees, row.enrolled), row.commissionName,
            row.formattedRevenue, row.formattedPayout],
        'web.payouts.empty',
        (row) => button('web.payouts.pay', () => payOut(row)));
}

/** مالٌ يخرج من الدرج ولا يُسترد، فيُسأل عنه - كما يسأل زرُّ النسخ الاحتياطي */
async function payOut(row) {
    try {
        if (!window.confirm(t('web.payouts.confirm', row.formattedPayout, row.teacherName,
                row.groupName, row.sessionDate))) {
            return;
        }
        const teacher = document.getElementById('payoutTeacher').value;
        await post('/api/teacher-payouts/' + row.sessionId
            + (teacher ? '?teacherId=' + teacher : ''));
        show('payoutResult', '', false);
        await Promise.all([refreshPayouts(), refreshTill()]);
    } catch (error) {
        show('payoutResult', error.message, true);
    }
}

/* ------------------------------------------------------------------ الإدارة */

let commissionTypes = [];

/**
 * ثلاث شاشاتٍ تحت عنوانٍ واحد، وكلٌّ تُمسك خطأها وحدها.
 *
 * <p>قراءةُ سجلّ المراقبة قد تُبطئ، وحسابُ مستخدمٍ قد يُرفض - ولا يصحّ أن يُفرّغ أحدُهما
 * نموذجَ الإعدادات الذي كتبه المستخدم توّاً.</p>
 */
async function loadAdmin() {
    const from = document.getElementById('auditFrom');
    const to = document.getElementById('auditTo');
    if (!from.value) {
        from.value = monthStart();
    }
    if (!to.value) {
        to.value = today();
    }

    await Promise.all([
        loadSettings().catch((error) => show('settingsResult', error.message, true)),
        loadTeachers().catch((error) => show('teacherResult', error.message, true)),
        loadUsers().catch((error) => show('userResult', error.message, true)),
        loadAudit().catch((error) => show('auditSummary', error.message, true))
    ]);
}

/* --------------------------------------------------------------- الإعدادات */

async function loadSettings() {
    const [currencies, channels] = await Promise.all([
        get('/api/settings/currencies'),
        get('/api/settings/channels')
    ]);
    fill(document.getElementById('setCurrency'),
        currencies.map((option) => ({value: option.name, label: option.label})), null);
    fill(document.getElementById('setChannel'),
        channels.map((option) => ({value: option.name, label: option.label})), null,
        t('web.common.none'));
    // التكرار ثلاثةُ ثوابت في center-core، وأسماؤها تصل مع بقية النصوص
    fill(document.getElementById('setBackupFrequency'),
        ['DAILY', 'WEEKLY', 'MONTHLY'].map((name) => ({value: name, label: name})), null,
        t('web.common.none'));

    await refreshSettings();
}

async function refreshSettings() {
    const settings = await get('/api/settings');

    document.getElementById('setCenterName').value = settings.centerName || '';
    document.getElementById('setCenterPhone').value = settings.centerPhone || '';
    document.getElementById('setLogoPath').value = settings.logoPath || '';
    document.getElementById('setBackupPath').value = settings.backupPath || '';
    document.getElementById('setAutoBackup').checked = settings.autoBackupEnabled;
    document.getElementById('setCurrency').value = settings.currencyName || '';
    document.getElementById('setBackupFrequency').value = settings.backupFrequency || '';
    document.getElementById('setBackupTime').value =
        settings.backupTime ? settings.backupTime.substring(0, 5) : '';
    document.getElementById('setBackupRetention').value =
        settings.backupRetentionCount === null ? '' : settings.backupRetentionCount;
    document.getElementById('setChannel').value = settings.notificationChannel || '';
    document.getElementById('setApiUrl').value = settings.notificationApiUrl || '';
    document.getElementById('setSenderId').value = settings.notificationSenderId || '';
    document.getElementById('setTemplateName').value = settings.notificationTemplateName || '';
    document.getElementById('setTemplateLanguage').value = settings.notificationTemplateLanguage || '';
    document.getElementById('setBodyTemplate').value = settings.notificationBodyTemplate || '';
    document.getElementById('setLedgerStart').value = settings.ledgerStartDate || '';

    // الثلاثةُ تُعرض ولا تُرسَل: ختمان يكتبهما المجدوِلان، ومفتاحٌ يملكه مركزُ التنبيهات.
    // وتاريخٌ قديم هنا هو الشيء الوحيد الذي يقول إن النسخ الليلية تفشل ليلةً بعد ليلة
    show('settingsState', t('web.settings.state',
        stampOf(settings.lastAutoBackupAt) || t('web.common.none'),
        stampOf(settings.lastAlertScanAt) || t('web.common.none'),
        t(settings.alertsEnabled ? 'web.settings.alertsOn' : 'web.settings.alertsOff')), false);
}

async function saveSettings() {
    const retention = document.getElementById('setBackupRetention').value;
    const saved = await put('/api/settings', {
        centerName: document.getElementById('setCenterName').value.trim(),
        centerPhone: document.getElementById('setCenterPhone').value.trim(),
        logoPath: document.getElementById('setLogoPath').value.trim(),
        backupPath: document.getElementById('setBackupPath').value.trim(),
        autoBackupEnabled: document.getElementById('setAutoBackup').checked,
        currency: document.getElementById('setCurrency').value || null,
        backupFrequency: document.getElementById('setBackupFrequency').value || null,
        backupTime: document.getElementById('setBackupTime').value || null,
        backupRetentionCount: retention === '' ? null : Number(retention),
        notificationChannel: document.getElementById('setChannel').value || null,
        notificationApiUrl: document.getElementById('setApiUrl').value.trim(),
        notificationSenderId: document.getElementById('setSenderId').value.trim(),
        notificationTemplateName: document.getElementById('setTemplateName').value.trim(),
        notificationTemplateLanguage: document.getElementById('setTemplateLanguage').value.trim(),
        notificationBodyTemplate: document.getElementById('setBodyTemplate').value.trim(),
        ledgerStartDate: document.getElementById('setLedgerStart').value || null
    });

    show('settingsResult', t('web.settings.saved'), false);
    show('settingsState', t('web.settings.state',
        stampOf(saved.lastAutoBackupAt) || t('web.common.none'),
        stampOf(saved.lastAlertScanAt) || t('web.common.none'),
        t(saved.alertsEnabled ? 'web.settings.alertsOn' : 'web.settings.alertsOff')), false);
}

/* ---------------------------------------------------------------- المعلمون */

async function loadTeachers() {
    if (!commissionTypes.length) {
        commissionTypes = await get('/api/teachers/commission-types');
        // القائمة تأتي من CommissionTypes.KNOWN: نوعٌ مكتوبٌ في الصفحة قد لا يعرفه الحساب
        fill(document.getElementById('teacherCommissionType'),
            commissionTypes.map((type) => ({value: type.name, label: type.label})), null);
    }
    await refreshTeachers();
}

async function refreshTeachers() {
    const rows = await get('/api/teachers?withCommission=true');
    teachers = rows;

    table('teacherTable',
        ['web.teachers.col.name', 'web.teachers.col.subject',
            'web.teachers.col.commission', 'web.teachers.col.value'],
        rows,
        (row) => [row.name, row.subject, row.commissionName, row.commissionValue],
        'web.teachers.empty',
        (row) => [
            button('web.teachers.edit', () => editTeacher(row)),
            button('web.teachers.delete', () => deleteTeacher(row))
        ]);
}

function editTeacher(row) {
    document.getElementById('teacherId').value = row.id;
    document.getElementById('teacherName').value = row.name || '';
    document.getElementById('teacherSubject').value = row.subject || '';
    document.getElementById('teacherCommissionType').value = row.commissionType || '';
    document.getElementById('teacherCommissionValue').value =
        row.commissionValue === null ? '' : row.commissionValue;
    show('teacherResult', '', false);
}

function clearTeacherForm() {
    document.getElementById('teacherId').value = '';
    ['teacherName', 'teacherSubject', 'teacherCommissionValue']
        .forEach((id) => { document.getElementById(id).value = ''; });
}

async function saveTeacher() {
    const id = document.getElementById('teacherId').value;
    const value = document.getElementById('teacherCommissionValue').value;
    const body = {
        name: document.getElementById('teacherName').value.trim(),
        subject: document.getElementById('teacherSubject').value.trim(),
        commissionType: document.getElementById('teacherCommissionType').value,
        commissionValue: value === '' ? null : value
    };

    const saved = id ? await put('/api/teachers/' + id, body) : await post('/api/teachers', body);
    show('teacherResult', t('web.teachers.saved', saved.name), false);
    clearTeacherForm();
    await refreshTeachers();
}

async function deleteTeacher(row) {
    try {
        if (!window.confirm(t('web.teachers.confirmDelete', row.name))) {
            return;
        }
        await remove('/api/teachers/' + row.id);
        show('teacherResult', '', false);
        clearTeacherForm();
        await refreshTeachers();
    } catch (error) {
        show('teacherResult', error.message, true);
    }
}

/* -------------------------------------------------------------- المستخدمون */

async function loadUsers() {
    const roles = await get('/api/users/roles');
    fill(document.getElementById('userRole'),
        roles.map((role) => ({value: role.name, label: role.label})), null);
    await refreshUsers();
}

async function refreshUsers() {
    const rows = await get('/api/users');
    table('userTable',
        ['web.users.col.username', 'web.users.col.role'],
        rows,
        (row) => [row.username, row.roleName],
        'web.users.empty',
        (row) => [
            button('web.users.edit', () => editUser(row)),
            button('web.users.delete', () => deleteUser(row))
        ]);
}

/**
 * التعديل لا يملأ حقلَي كلمة المرور.
 *
 * <p>لا بصمةَ تصل من الخادم أصلاً، وحقلٌ ممتلئ بنجومٍ يوهم أن كلمةً ما ستُحفظ. فارغاً
 * يعني "اتركها كما هي" - وهو ما تقوله جملةُ التلميح تحت النموذج.</p>
 */
function editUser(row) {
    document.getElementById('userId').value = row.id;
    document.getElementById('userName').value = row.username || '';
    document.getElementById('userRole').value = row.role || '';
    clearPasswordFields();
    show('userResult', '', false);
}

function clearPasswordFields() {
    document.getElementById('userPassword').value = '';
    document.getElementById('userConfirmation').value = '';
}

function clearUserForm() {
    document.getElementById('userId').value = '';
    document.getElementById('userName').value = '';
    clearPasswordFields();
}

async function saveUser() {
    const id = document.getElementById('userId').value;
    const body = {
        username: document.getElementById('userName').value.trim(),
        role: document.getElementById('userRole').value,
        password: document.getElementById('userPassword').value,
        confirmation: document.getElementById('userConfirmation').value
    };

    const saved = id ? await put('/api/users/' + id, body) : await post('/api/users', body);
    show('userResult', t('web.users.saved', saved.username), false);
    clearUserForm();
    await refreshUsers();
}

async function deleteUser(row) {
    try {
        if (!window.confirm(t('web.users.confirmDelete', row.username))) {
            return;
        }
        await remove('/api/users/' + row.id);
        show('userResult', '', false);
        clearUserForm();
        await refreshUsers();
    } catch (error) {
        // آخرُ مدير، ومن يحذف نفسه، والحساب المدمج - ثلاثةُ رفضٍ تأتي مترجَمة من الخدمة
        show('userResult', error.message, true);
    }
}

/* ----------------------------------------------------------- سجلّ المراقبة */

async function loadAudit() {
    const [actors, categories] = await Promise.all([
        get('/api/audit/actors'),
        get('/api/audit/categories')
    ]);
    fill(document.getElementById('auditActor'),
        actors.map((actor) => ({value: actor, label: actor})), null, t('web.common.all'));
    fill(document.getElementById('auditCategory'),
        categories.map((option) => ({value: option.name, label: option.label})), null,
        t('web.common.all'));

    await refreshAudit();
}

async function refreshAudit() {
    const from = document.getElementById('auditFrom').value || today();
    const to = document.getElementById('auditTo').value || today();
    const actor = document.getElementById('auditActor').value;
    const category = document.getElementById('auditCategory').value;

    const page = await get('/api/audit?from=' + from + '&to=' + to
        + (actor ? '&actor=' + encodeURIComponent(actor) : '')
        + (category ? '&category=' + encodeURIComponent(category) : ''));

    // سجلٌّ يعرض جزءاً بصمتٍ يدعو قارئه إلى استنتاج أن الباقي لم يقع
    show('auditSummary', page.truncated
        ? t('web.audit.truncated', page.rows.length, page.totalMatching) : '', page.truncated);

    table('auditTable',
        ['web.audit.col.at', 'web.audit.col.actor', 'web.audit.col.action',
            'web.audit.col.entity', 'web.audit.col.amount', 'web.audit.col.state',
            'web.audit.col.details'],
        page.rows,
        // اسمٌ فارغ يعني النظام: النسخة المجدولة تجري بلا جلسة، ونسبتُها إلى أحدٍ كذبة
        (row) => [stampOf(row.at), row.actor || t('web.audit.system'), row.actionName,
            row.entityLabel, row.amount,
            t(row.successful ? 'web.audit.state.ok' : 'web.audit.state.failed'),
            row.details],
        'web.audit.empty');
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

/* --------------------------------------------------------- مركز التنبيهات */

let alertCategories = [];
let alertSeverities = [];
let alertAudiences = [];
let notifyTypes = [];

/** آخر ما قُرئ من الصندوق: مرشِّح "غير المعالَجة" يعمل عليه بلا طلبٍ جديد */
let alertRows = [];

/** جملةُ "معروض كذا من كذا" حين يعضّ السقف، وعددُ ما لم يُعالَج - كلاهما من الخادم */
let alertTruncation = '';
let alertUnacknowledged = 0;

/** القاعدة المفتوحة في النموذج الآن، وهي مصدرُ كل ما يُعرض فيه */
let editedRule = null;

/** آخر قائمةِ مرشَّحين بُنيت، وما وُصفت به - والوصفُ هو ما يُعاد به بناؤها عند الإرسال */
let notifyCandidates = [];
let notifyScope = null;

/**
 * أربع شاشاتٍ تحت عنوانٍ واحد، وكلٌّ تُمسك خطأها وحدها.
 *
 * <p>نفس ما في شاشة الإدارة: فحصٌ يُرفض لا يصحّ أن يُفرّغ نموذج قاعدةٍ كُتب توّاً،
 * ولا أن يُخفي قائمةً بُنيت للإرسال.</p>
 */
async function loadAlerts() {
    const from = document.getElementById('alertFrom');
    const to = document.getElementById('alertTo');
    if (!from.value) {
        from.value = monthStart();
    }
    if (!to.value) {
        to.value = today();
    }

    if (!alertCategories.length) {
        [alertCategories, alertSeverities, alertAudiences, notifyTypes] = await Promise.all([
            get('/api/alerts/categories'),
            get('/api/alert-rules/severities'),
            get('/api/alert-rules/audiences'),
            get('/api/notifications/types')
        ]);
        const options = (list) => list.map((option) => ({value: option.name, label: option.label}));
        fill(document.getElementById('alertCategory'), options(alertCategories), null, t('web.common.all'));
        fill(document.getElementById('alertSeverity'), options(alertSeverities), null, t('web.common.all'));
        fill(document.getElementById('ruleAudience'), options(alertAudiences), null);
        fill(document.getElementById('ruleSeverity'), options(alertSeverities), null);
        fill(document.getElementById('notifyType'),
            notifyTypes.map((type) => ({value: type.name, label: type.label})), null);
    }

    // قائمةُ المجموعات تُقرأ في كل فتحة: مجموعةٌ أُنشئت في تبويب آخر تكون هنا
    const groups = await get('/api/groups');
    fill(document.getElementById('notifyGroup'),
        groups.map((group) => ({value: group.id, label: group.name})),
        document.getElementById('notifyGroup').value || null, t('web.common.none'));

    await Promise.all([
        refreshAlertInbox().catch((error) => show('alertSummary', error.message, true)),
        refreshScanSettings().catch((error) => show('scanState', error.message, true)),
        refreshRules().catch((error) => show('ruleResult', error.message, true)),
        refreshChannel().catch((error) => show('notifyChannel', error.message, true)),
        refreshNotifyLog().catch((error) => show('notifyResult', error.message, true))
    ]);
    notifyTypeChanged();
}

/* --------------------------------------------------------------- الصندوق */

async function refreshAlertInbox() {
    const category = document.getElementById('alertCategory').value;
    const severity = document.getElementById('alertSeverity').value;
    const page = await get('/api/alerts?from=' + document.getElementById('alertFrom').value
        + '&to=' + document.getElementById('alertTo').value
        + (category ? '&category=' + category : '')
        + (severity ? '&severity=' + severity : ''));

    alertRows = page.rows;
    alertUnacknowledged = page.unacknowledged;
    // سقفُ الصفوف يُقال حين يعضّ: صندوقٌ يعرض ألفاً من عشرة آلاف صامتاً يجعل من
    // ينظر إليه يستنتج أن الباقي لم يقع
    alertTruncation = page.truncated
        ? t('web.alerts.truncated', page.rows.length, page.totalMatching) + '   |   ' : '';
    applyOpenOnly();
}

/** مرشِّحُ عرضٍ لا سؤالٌ جديد: الصفُّ يحمل حالَه، والفترةُ والتصنيفُ وحدهما يُسألان */
function applyOpenOnly() {
    const openOnly = document.getElementById('alertOpenOnly').checked;
    const shown = alertRows.filter((row) => !openOnly || !row.acknowledged);

    table('alertTable',
        ['web.alerts.col.raisedAt', 'web.alerts.col.severity', 'web.alerts.col.type',
            'web.alerts.col.category', 'web.alerts.col.target', 'web.alerts.col.message',
            'web.alerts.col.state'],
        shown,
        (row) => [stampOf(row.raisedAt), row.severityName, row.typeName, row.categoryName,
            row.entityLabel, row.text, stateOfAlert(row)],
        'web.alerts.inboxEmpty',
        // المعالَج لا زرَّ له: العلامة لا تُنقض، فزرٌّ يُعيد ضغطُه لا شيء هو زرٌّ يكذب
        (row) => row.acknowledged ? null
            : button('web.alerts.acknowledge', () => acknowledgeAlert(row).catch(report)));

    show('alertSummary', alertTruncation
        + t('web.alerts.summary', shown.length, alertRows.length, alertUnacknowledged), false);
}

function stateOfAlert(row) {
    if (!row.acknowledged) {
        return t('web.alerts.status.open');
    }
    return row.acknowledgedBy
        ? t('web.alerts.status.doneBy', row.acknowledgedBy)
        : t('web.alerts.status.done');
}

async function acknowledgeAlert(row) {
    const result = await post('/api/alerts/acknowledge', {alertIds: [row.id]});
    show('alertSummary', t('web.alerts.acknowledged', result.acknowledged), false);
    await refreshAlertInbox();
}

/**
 * فحصٌ فوريّ.
 *
 * <p>يُسأل قبله حين تكون هناك قاعدةٌ وجهتها أولياء الأمور: الضغطة قد تُخرج رسائل
 * حقيقية إلى أرقامٍ حقيقية الآن، وما خرج لا يُسحب. والقواعد محمَّلةٌ أصلاً في
 * الجدول أسفل الشاشة، فالسؤال لا يكلّف طلباً.</p>
 */
async function scanNow() {
    if (rulesMessagingParents() && !confirm(t('web.alerts.scanConfirm'))) {
        return;
    }
    const result = await post('/api/alerts/scan');
    const line = t('web.alerts.scanDone', result.raised, result.messaged);
    show('alertSummary', result.failures.length
        ? line + '\n' + result.failures.join('\n') : line, result.failures.length > 0);

    await Promise.all([refreshAlertInbox(), refreshScanSettings()]);
}

/* ------------------------------------------------------------ موعد الفحص */

async function refreshScanSettings() {
    showScanSettings(await get('/api/alerts/scan-settings'));
}

function showScanSettings(settings) {
    document.getElementById('scanEnabled').checked = settings.enabled;
    document.getElementById('scanTime').value =
        settings.time ? settings.time.substring(0, 5) : '';

    // تاريخٌ قديم هنا هو الشيء الوحيد الذي يقول إن الفحص يفشل يوماً بعد يوم
    show('scanState', t('web.alerts.scanState',
        stampOf(settings.lastScanAt) || t('web.alerts.neverScanned'),
        t(settings.enabled ? 'web.rules.on' : 'web.rules.off')), false);
}

async function saveScanSettings() {
    showScanSettings(await put('/api/alerts/scan-settings', {
        enabled: document.getElementById('scanEnabled').checked,
        time: document.getElementById('scanTime').value || null
    }));
    show('ruleResult', t('web.alerts.scanSaved'), false);
}

/* ---------------------------------------------------------------- القواعد */

let rules = [];

async function refreshRules() {
    rules = await get('/api/alert-rules');

    table('ruleTable',
        ['web.rules.col.type', 'web.rules.col.category', 'web.rules.col.state',
            'web.rules.col.audience', 'web.rules.col.severity', 'web.rules.col.updated'],
        rules,
        (row) => [row.typeName, row.categoryName,
            t(row.enabled ? 'web.rules.on' : 'web.rules.off'),
            row.audienceName, row.severityName,
            row.updatedAt ? t('web.rules.updated', stampOf(row.updatedAt),
                row.updatedBy || t('web.audit.system')) : t('web.rules.untouched')],
        'web.rules.empty',
        (row) => button('web.rules.edit', () => editRule(row)));

    show('ruleSummary', t('web.rules.summary',
        rules.filter((rule) => rule.enabled).length, rules.length,
        rules.filter(rulesSendsToParents).length), false);
}

/** قاعدةٌ تُخرج رسالةً إلى ولي أمر فعلاً: مفعَّلةٌ، ووجهتُها تشمله، ونوعُها يقبله */
function rulesSendsToParents(rule) {
    return rule.enabled && rule.parentCapable && rule.audience !== 'INTERNAL';
}

function rulesMessagingParents() {
    return rules.some(rulesSendsToParents);
}

/**
 * فتحُ نموذج قاعدة.
 *
 * <p>المغيِّرات تُخفى لا تُعطَّل، تماماً كنافذة سطح المكتب: عنوانُ الحدّ يأتي من
 * النوع نفسه، وحقلُ أرقامٍ بجوار عنوانٍ فارغ لا يقول ما هو. والوجهةُ لا تُعرض إلا
 * على نوعٍ يقبل الإرسال - عرضُ الخيار ثم رفضُه عند الحفظ أسوأ من عدم عرضه.</p>
 */
function editRule(row) {
    editedRule = row;
    document.getElementById('ruleForm').classList.remove('hidden');
    document.getElementById('ruleSubject').textContent = row.typeName;
    document.getElementById('ruleDescription').textContent = row.description || '';
    document.getElementById('ruleEnabled').checked = row.enabled;
    document.getElementById('ruleSeverity').value = row.severity || '';

    fill(document.getElementById('ruleAudience'),
        alertAudiences
            .filter((audience) => row.parentCapable || audience.name === 'INTERNAL')
            .map((audience) => ({value: audience.name, label: audience.label})),
        row.audience);
    showAudienceNote();

    showRuleField('ruleThreshold', row.thresholdLabel, row.threshold);
    showRuleField('ruleWindow', row.windowLabel, row.windowDays);
    showRuleField('ruleCooldown', row.scheduled ? t('web.rules.cooldown') : null, row.cooldownDays);
    show('ruleResult', '', false);
}

/** الحقلُ وعنوانُه يظهران معاً أو يختفيان معاً؛ عنوانٌ بلا حقل سطرٌ فارغ يقول إن شيئاً كان هنا */
function showRuleField(id, label, value) {
    const field = document.getElementById(id);
    const caption = document.getElementById(id + 'Label');
    const shown = label !== null && label !== undefined;

    caption.textContent = shown ? label : '';
    caption.classList.toggle('hidden', !shown);
    field.classList.toggle('hidden', !shown);
    field.value = shown && value !== null && value !== undefined ? value : '';
}

function showAudienceNote() {
    const toParents = document.getElementById('ruleAudience').value !== 'INTERNAL';
    const note = document.getElementById('ruleAudienceNote');
    note.textContent = t(toParents ? 'web.rules.parentsNote' : 'web.rules.internalNote');
    note.classList.toggle('bad', toParents);
}

function closeRuleForm() {
    editedRule = null;
    document.getElementById('ruleForm').classList.add('hidden');
}

/**
 * حفظُ القاعدة.
 *
 * <p>تأكيدٌ صريح حين تصير الوجهة أولياء الأمور لأول مرة: هذه هي اللحظة التي يبدأ
 * فيها البرنامج بمراسلة أرقامٍ حقيقية باسم السنتر بلا ضغطةٍ من موظف.</p>
 */
async function saveRule() {
    if (!editedRule) {
        return;
    }
    const audience = document.getElementById('ruleAudience').value;
    const enabled = document.getElementById('ruleEnabled').checked;
    const startsMessagingParents = enabled && audience !== 'INTERNAL'
        && !rulesSendsToParents(editedRule);

    if (startsMessagingParents
        && !confirm(t('web.rules.parentConfirm', editedRule.typeName))) {
        return;
    }

    const number = (id) => {
        const field = document.getElementById(id);
        return field.classList.contains('hidden') || field.value === '' ? null : Number(field.value);
    };

    const saved = await put('/api/alert-rules/' + editedRule.type, {
        enabled,
        audience,
        severity: document.getElementById('ruleSeverity').value,
        threshold: number('ruleThreshold'),
        windowDays: number('ruleWindow'),
        cooldownDays: number('ruleCooldown')
    });

    closeRuleForm();
    await refreshRules();
    show('ruleResult', t('web.rules.saved', saved.typeName), false);
}

/* ----------------------------------------------- رسائل أولياء الأمور */

async function refreshChannel() {
    const channel = await get('/api/notifications/channel');
    const note = document.getElementById('notifyChannel');

    // القناة تُقال قبل الضغط لا بعد أربعين رفضاً من المزوّد
    if (channel.problem) {
        note.textContent = t('web.notify.channelNotReady', channel.problem);
        note.classList.add('bad');
        return;
    }
    note.textContent = t(channel.requiresManualConfirmation
        ? 'web.notify.channelManual' : 'web.notify.channelAutomatic');
    note.classList.remove('bad');
}

/** المجموعةُ والمدةُ تخصّان الغياب وحده: النوع هو من يقول ذلك، لا الصفحة */
function notifyTypeChanged() {
    const selected = notifyTypes.find(
        (type) => type.name === document.getElementById('notifyType').value);
    const needsGroup = Boolean(selected && selected.needsGroup);

    ['notifyGroup', 'notifyFrom', 'notifyTo'].forEach((id) => {
        document.getElementById(id).classList.toggle('hidden', !needsGroup);
    });
    if (needsGroup && !document.getElementById('notifyFrom').value) {
        document.getElementById('notifyFrom').value = monthStart();
        document.getElementById('notifyTo').value = today();
    }
}

async function buildCandidates() {
    const type = document.getElementById('notifyType').value;
    const group = document.getElementById('notifyGroup').value;
    const from = document.getElementById('notifyFrom').value;
    const to = document.getElementById('notifyTo').value;

    // الوصفُ يُحفظ كما أُرسل: نفسُه يُعاد به البناء على الخادم لحظة الإرسال، فلو
    // تغيّر الحقل بعد بناء القائمة لَبُنيت قائمةٌ أخرى لا هذه التي أمام العين
    notifyScope = {
        type,
        groupId: group ? Number(group) : null,
        from: from || null,
        to: to || null
    };
    const query = 'type=' + type + (notifyScope.groupId ? '&groupId=' + notifyScope.groupId : '')
        + (notifyScope.from ? '&from=' + notifyScope.from : '')
        + (notifyScope.to ? '&to=' + notifyScope.to : '');

    notifyCandidates = await get('/api/notifications/candidates?' + query);
    showCandidates();
}

function showCandidates() {
    table('notifyTable',
        ['web.notify.col.name', 'web.notify.col.phone', 'web.notify.col.status',
            'web.notify.col.message'],
        notifyCandidates,
        (row) => [row.studentName, row.phone, row.statusLabel, row.message],
        'web.notify.empty',
        (row) => row.sendable
            ? button('web.notify.send', () => sendNotification(row).catch(report))
            : null);

    show('notifySummary', t('web.notify.summary', notifyCandidates.length,
        notifyCandidates.filter((row) => row.sendable).length), false);
}

/**
 * إرسالُ إشعارٍ واحد.
 *
 * <p>ما يُرسَل إلى الخادم هو <b>وصفُ القائمة ورقمُ الطالب</b>، لا الرقمُ ولا النصّ:
 * قبولُهما من جسم الطلب يعني أن أيَّ من يملك جلسةً يُرسل أيَّ نصٍّ إلى أيِّ رقم من
 * حساب السنتر عند المزوّد. والخادمُ يعيد بناء القائمة ويأخذ منها صاحبَ الرقم.</p>
 */
async function sendNotification(row) {
    if (!confirm(t('web.notify.confirm', row.studentName))) {
        return;
    }
    show('notifyHandOff', '', false);
    const result = await post('/api/notifications/send',
        Object.assign({studentId: row.studentId}, notifyScope));

    if (result.needsHandOff) {
        showHandOff(row, result.link);
        return;
    }
    show('notifyResult', result.success
        ? t('web.notify.sent', row.studentName) : result.failureReason, !result.success);
    await Promise.all([buildCandidates(), refreshNotifyLog()]);
}

/**
 * التسليمُ إلى إنسان: رابطٌ يفتحه، ثم يقول هو إنه فُتح.
 *
 * <p>ولا يُفتح من هنا تلقائياً ولا يُسجَّل تلقائياً. سطرُ {@code notification_logs}
 * يعني "فُتحت محادثةُ وليّ الأمر بالرسالة فيها"، وكتابتُه لحظةَ بناء الرابط تسجّل
 * إشعاراً لمحادثةٍ لم تُفتح - ثم يمنع حارسُ التكرار إعادةَ المحاولة، فيبقى وليُّ
 * أمرٍ بلا خبر ولا أحد يدري.</p>
 */
function showHandOff(row, link) {
    const area = document.getElementById('notifyHandOff');
    area.innerHTML = '';
    area.classList.remove('bad');

    const note = document.createElement('span');
    note.textContent = t('web.notify.handOff') + ' ';
    area.appendChild(note);

    const anchor = document.createElement('a');
    anchor.href = link;
    anchor.target = '_blank';
    anchor.rel = 'noopener';
    anchor.textContent = t('web.notify.openChat');
    area.appendChild(anchor);

    area.appendChild(button('web.notify.markOpened', () => markOpened(row).catch(report)));
}

async function markOpened(row) {
    await post('/api/notifications/opened',
        Object.assign({studentId: row.studentId}, notifyScope));

    show('notifyHandOff', '', false);
    show('notifyResult', t('web.notify.opened', row.studentName), false);
    await Promise.all([buildCandidates(), refreshNotifyLog()]);
}

async function refreshNotifyLog() {
    const rows = await get('/api/notifications/log');
    table('notifyLogTable',
        ['web.notify.log.col.at', 'web.notify.log.col.type', 'web.notify.log.col.phone'],
        rows,
        (row) => [stampOf(row.sentAt), row.typeName, row.phone],
        'web.notify.logEmpty');
}

/* ------------------------------------------------------- مجرى التنبيهات */

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

    // ما قُرئ لحسابِ من خرج لا يبقى لمن يدخل بعده: اسمُ طالبٍ معلّقاً فوق شاشة
    // الدخول هو بعينه ما تُغلَق لأجله بطاقاتُ التنبيهات. وعلى منصّةٍ متعددة السناتر
    // القائمةُ المحفوظة تكون قائمةَ سنترٍ آخر
    enrolmentSubject = null;
    levels = [];
    teachers = [];
    days = [];
    commissionTypes = [];

    // وقائمةُ المرشَّحين معها: أسماءُ طلابٍ وأرقامُ أولياء أمورهم ونصوصُ رسائلهم،
    // وهي على منصّةٍ متعددة السناتر قائمةُ سنترٍ آخر
    alertCategories = [];
    alertSeverities = [];
    alertAudiences = [];
    notifyTypes = [];
    alertRows = [];
    rules = [];
    notifyCandidates = [];
    notifyScope = null;
    closeRuleForm();

    // ولا يبقى في حقلٍ ما كُتب فيه: كلمةُ مرورِ حسابٍ يُنشأ لا تُترك لمن يجلس بعده
    clearPasswordFields();
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
        refreshStudents().catch(report);
    });

    document.getElementById('studentEditForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveStudent().catch((error) => show('studentResult', error.message, true));
    });

    document.getElementById('studentClear').addEventListener('click', () => {
        clearStudentForm();
        show('studentResult', '', false);
    });

    document.getElementById('enrolForm').addEventListener('submit', async (event) => {
        event.preventDefault();
        const group = document.getElementById('enrolGroup').value;
        if (!enrolmentSubject || !group) {
            return;
        }
        try {
            await post('/api/students/' + enrolmentSubject.id + '/enrollments',
                {groupId: Number(group)});
            show('enrolResult', '', false);
            await refreshEnrolments();
        } catch (error) {
            show('enrolResult', error.message, true);
        }
    });

    document.getElementById('groupEditForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveGroup().catch((error) => show('groupResult', error.message, true));
    });

    document.getElementById('groupClear').addEventListener('click', () => {
        clearGroupForm();
        show('groupResult', '', false);
    });

    document.getElementById('groupCustomName').addEventListener('change', (event) => {
        setCustomName(event.target.checked, document.getElementById('groupName').value);
    });

    document.getElementById('expenseReportForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshExpenses().catch(report);
    });

    document.getElementById('payoutForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshPayouts().catch(report);
    });

    document.getElementById('settingsForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveSettings().catch((error) => show('settingsResult', error.message, true));
    });

    document.getElementById('teacherForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveTeacher().catch((error) => show('teacherResult', error.message, true));
    });

    document.getElementById('teacherClear').addEventListener('click', () => {
        clearTeacherForm();
        show('teacherResult', '', false);
    });

    document.getElementById('userForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveUser().catch((error) => show('userResult', error.message, true));
    });

    document.getElementById('userClear').addEventListener('click', () => {
        clearUserForm();
        show('userResult', '', false);
    });

    document.getElementById('auditForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshAudit().catch((error) => show('auditSummary', error.message, true));
    });

    document.getElementById('alertForm').addEventListener('submit', (event) => {
        event.preventDefault();
        refreshAlertInbox().catch((error) => show('alertSummary', error.message, true));
    });

    // المرشِّح يعمل على ما هو محمَّل: سؤالُ الخادم عن نفس الفترة لإخفاء صفوفٍ بين يديه
    // طلبٌ بلا سبب
    document.getElementById('alertOpenOnly').addEventListener('change', () => applyOpenOnly());

    document.getElementById('alertScan').addEventListener('click', () => {
        scanNow().catch((error) => show('alertSummary', error.message, true));
    });

    document.getElementById('scanSettingsForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveScanSettings().catch((error) => show('scanState', error.message, true));
    });

    document.getElementById('ruleAudience').addEventListener('change', showAudienceNote);

    document.getElementById('ruleForm').addEventListener('submit', (event) => {
        event.preventDefault();
        saveRule().catch((error) => show('ruleResult', error.message, true));
    });

    document.getElementById('ruleCancel').addEventListener('click', closeRuleForm);

    document.getElementById('notifyType').addEventListener('change', notifyTypeChanged);

    document.getElementById('notifyForm').addEventListener('submit', (event) => {
        event.preventDefault();
        buildCandidates().catch((error) => show('notifySummary', error.message, true));
    });

    document.getElementById('reportForm').addEventListener('input', refreshReportLinks);

    // جلسةٌ ما زالت قائمة من تحميل سابق: الصفحة تُفتح على الشاشة لا على الدخول
    try {
        enterApp(await get('/api/me'));
    } catch (ignored) {
        // لا جلسة؛ شاشة الدخول هي المعروضة أصلاً
    }
});
