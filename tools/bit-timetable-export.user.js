// ==UserScript==
// @name         北理工课表导出（课程表 Lite）
// @namespace    coursetable-lite
// @version      1.0.0
// @description  在北理工“我的课表”页面手动点击按钮，抓取课表并导出可导入“课程表 Lite”的 JSON
// @author       CourseTableLite
// @match        https://jxzxehallapp.bit.edu.cn/jwapp/sys/wdkbby/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  if (window.__bitTimetableExporterLoaded) return;
  window.__bitTimetableExporterLoaded = true;

  const API = {
    term: '/jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do',
    student: '/jwapp/sys/wdkbby/modules/xskcb/cxxsjbxx.do',
    rows: '/jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do',
    calendar: '/jwapp/sys/wdkbby/modules/xskcb/cxxljc.do',
    week1: '/jwapp/sys/wdkbby/wdkbByController/cxzkbrq.do',
    sections: '/jwapp/sys/wdkbby/modules/jshkcb/jc.do',
    sectionConfig: '/jwapp/sys/wdkbby/modules/jshkcb/cxjcs.do',
  };

  const state = {
    busy: false,
    dataText: '',
    rawText: '',
    fileBase: 'timetable',
  };

  // ---------- 数据读取 ----------

  async function post(path, params) {
    const body = new URLSearchParams();
    Object.entries(params || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null) body.set(k, String(v));
    });
    const res = await fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest',
      },
      body: body.toString(),
    });
    const text = await res.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch (e) {
      throw new Error('登录状态可能已失效（接口返回的不是 JSON）');
    }
    if (String(json.code) !== '0') {
      throw new Error(json.msg || ('接口返回错误：code=' + json.code));
    }
    return json;
  }

  function getStudentId() {
    const el = document.querySelector('.bh-headerBar-userIfon-name');
    const id = ((el && (el.getAttribute('title') || el.textContent)) || '').trim();
    return /^\d{6,}$/.test(id) ? id : '';
  }

  function cleanCourseName(raw) {
    const s = String(raw || '').trim();
    const m = s.match(/\s*(大物2\[\d+\])\s*$/);
    if (!m) return { name: s, note: '' };
    return {
      name: s.slice(0, m.index).trim(),
      note: '大学物理实验 · ' + m[1],
    };
  }

  function parseWeeks(raw) {
    let s = String(raw || '').trim();
    let parity = 0;
    if (s.includes('单周')) {
      parity = 1;
      s = s.replace(/单周/g, '');
    }
    if (s.includes('双周')) {
      parity = 2;
      s = s.replace(/双周/g, '');
    }
    s = s.replace(/[周\s]/g, '').replace(/，/g, ',');
    const out = new Set();
    for (const part of s.split(',')) {
      const m = part.match(/^(-?\d+)(?:-(-?\d+))?$/);
      if (!m) continue;
      const a = Number(m[1]);
      const b = m[2] === undefined ? a : Number(m[2]);
      for (let w = Math.min(a, b); w <= Math.max(a, b); w++) out.add(w);
    }
    let arr = [...out];
    if (parity === 1) arr = arr.filter((w) => Math.abs(w) % 2 === 1);
    if (parity === 2) arr = arr.filter((w) => Math.abs(w) % 2 === 0);
    return arr.sort((a, b) => a - b);
  }

  function todayMonday() {
    const d = new Date();
    const mondayOffset = (d.getDay() + 6) % 7;
    d.setDate(d.getDate() - mondayOffset);
    return [
      d.getFullYear(),
      String(d.getMonth() + 1).padStart(2, '0'),
      String(d.getDate()).padStart(2, '0'),
    ].join('-');
  }

  function buildPeriodTimes(jcRows, configRow) {
    const morning = Number(configRow && configRow.SFJJ) || 5;
    const afternoon = Number(configRow && configRow.XFJJ) || 5;
    return (jcRows || [])
      .map((r) => {
        const period = Number(r.DM);
        const start = String(r.KSSJ || '').trim();
        const end = String(r.JSSJ || '').trim();
        if (!period || !start || !end) return null;
        return {
          period,
          start,
          end,
          section: period <= morning ? '上午' : period <= morning + afternoon ? '下午' : '晚上',
        };
      })
      .filter(Boolean)
      .sort((a, b) => a.period - b.period);
  }

  function buildCourses(rows) {
    const courseMap = new Map();
    let skipped = 0;

    for (const row of rows) {
      const { name, note } = cleanCourseName(row.KCM);
      const weeks = parseWeeks(row.ZCMC);
      const day = Number(row.SKXQ);
      const startPeriod = Number(row.KSJC);
      const endPeriod = Number(row.JSJC) || startPeriod;

      if (!name || !weeks.length || !(day >= 1 && day <= 7) || !(startPeriod >= 1)) {
        skipped++;
        continue;
      }

      const code = String(row.KCH || '').trim();
      const key = (code || note || name) + '\u0000' + name;
      let course = courseMap.get(key);
      if (!course) {
        course = {
          name,
          code,
          department: String(row.KKDWDM_DISPLAY || '').trim(),
          credit: Number(row.XF) || 0,
          nature: String(row.KCXZDM_DISPLAY || '').trim(),
          category: String(row.KCLBDM_DISPLAY || '').trim(),
          subCategory: String(row.XGXKLBDM_DISPLAY || '').trim(),
          campus: String(row.XXXQDM_DISPLAY || '').trim(),
          schedules: [],
        };
        courseMap.set(key, course);
      }

      const schedule = {
        weeks,
        day,
        startPeriod,
        endPeriod,
        teacher: String(row.SKJS || '').trim(),
        location: String(row.JASMC || '').trim(),
        classNo: String(row.KXH || '').trim(),
        note,
      };
      const same = course.schedules.find(
        (s) =>
          s.day === schedule.day &&
          s.startPeriod === schedule.startPeriod &&
          s.endPeriod === schedule.endPeriod &&
          s.teacher === schedule.teacher &&
          s.location === schedule.location &&
          s.classNo === schedule.classNo
      );
      if (same) {
        same.weeks = [...new Set([...same.weeks, ...weeks])].sort((a, b) => a - b);
      } else {
        course.schedules.push(schedule);
      }
    }

    const courses = [...courseMap.values()];
    courses.forEach((c, i) => {
      c.colorIndex = i % 10;
      c.schedules.sort((a, b) => a.day - b.day || a.startPeriod - b.startPeriod);
    });
    return { courses, skipped };
  }

  async function fetchAll() {
    const termJson = await post(API.term, {});
    const term = termJson.datas && termJson.datas.dqxnxq && termJson.datas.dqxnxq.rows[0];
    if (!term || !term.DM) throw new Error('无法获取当前学期');

    const xnxqdm = String(term.DM);
    const parts = xnxqdm.split('-');
    const xn = parts.length >= 2 ? parts[0] + '-' + parts[1] : '';
    const xq = parts.length >= 3 ? parts[2] : '';
    const studentId = getStudentId();

    const [rowsJson, studentJson, calendarJson, week1Json, jcJson, configJson] = await Promise.all([
      post(API.rows, { XNXQDM: xnxqdm }),
      studentId ? post(API.student, { XH: studentId }).catch(() => null) : Promise.resolve(null),
      xn && xq ? post(API.calendar, { XN: xn, XQ: xq }).catch(() => null) : Promise.resolve(null),
      post(API.week1, {
        requestParamStr: JSON.stringify({ XNXQDM: xnxqdm, ZC: '1' }),
      }).catch(() => null),
      post(API.sections, {}).catch(() => null),
      xn && xq ? post(API.sectionConfig, { XN: xn, XQ: xq }).catch(() => null) : Promise.resolve(null),
    ]);

    const rows = (rowsJson.datas && rowsJson.datas.cxxszhxqkb && rowsJson.datas.cxxszhxqkb.rows) || [];
    if (!rows.length) throw new Error('没有查到课程数据（当前学期可能没有课表）');

    const student =
      (studentJson && studentJson.datas && studentJson.datas.cxxsjbxx && studentJson.datas.cxxsjbxx.rows[0]) || null;
    const calendar =
      (calendarJson && calendarJson.datas && calendarJson.datas.cxxljc && calendarJson.datas.cxxljc.rows[0]) || null;
    const week1 = (week1Json && week1Json.data) || [];
    const jcRows = (jcJson && jcJson.datas && jcJson.datas.jc && jcJson.datas.jc.rows) || [];
    const configRow =
      (configJson && configJson.datas && configJson.datas.cxjcs && configJson.datas.cxjcs.rows[0]) || null;

    const { courses, skipped } = buildCourses(rows);

    const week1Monday = week1.find((r) => Number(r.XQ) === 1);
    let termStart = String((week1Monday && week1Monday.RQ) || (calendar && calendar.XQKSRQ) || '').slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(termStart)) termStart = todayMonday();

    const allWeeks = courses.flatMap((c) => c.schedules.flatMap((s) => s.weeks));
    const maxWeek = allWeeks.length ? Math.max(...allWeeks) : 0;
    const miniTermWeeks = allWeeks.length
      ? Math.max(0, ...allWeeks.filter((w) => w < 0).map((w) => -w))
      : 0;
    const calendarWeeks = Number(calendar && calendar.ZZC);
    const regularWeeks = Math.max(calendarWeeks > 0 ? calendarWeeks : 0, maxWeek, 1);

    const data = {
      version: 1,
      termName: String(term.MC || xnxqdm),
      studentName: String((student && student.XM) || ''),
      studentId: String((student && student.XH) || studentId || ''),
      termStart,
      regularWeeks,
      miniTermWeeks,
      periodTimes: buildPeriodTimes(jcRows, configRow),
      courses,
    };

    const raw = {
      term: {
        XNXQDM: xnxqdm,
        MC: String(term.MC || ''),
        termStart,
        regularWeeks,
        calendarTotalWeeks: calendarWeeks || null,
        miniTermWeeks,
      },
      student: student
        ? {
            XH: student.XH || '',
            XM: student.XM || '',
            YXMC: student.YXMC || '',
          }
        : null,
      fetchedAt: new Date().toISOString(),
      recordCount: rows.length,
      rows,
    };

    const unitCount = courses.reduce((n, c) => n + c.schedules.length, 0);
    const summary = {
      data,
      raw,
      text:
        `${rows.length} 条排课记录 → ${courses.length} 门课程 / ${unitCount} 个排课单元` +
        (skipped ? `（跳过 ${skipped} 条无法识别的记录）` : ''),
    };
    return summary;
  }

  // ---------- 导出 ----------

  function download(filename, text) {
    const blob = new Blob([text], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }

  async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return;
    }
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.cssText = 'position:fixed;left:-9999px;top:0;';
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    if (!ok) throw new Error('浏览器拒绝写入剪贴板');
  }

  function safeFilePart(s) {
    return String(s || '')
      .replace(/[\\/:*?"<>|\s]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'timetable';
  }

  // ---------- 界面 ----------

  const host = document.createElement('div');
  host.id = 'bit-timetable-export-host';
  host.style.cssText = 'position:fixed;right:20px;bottom:20px;z-index:2147483647;';
  document.documentElement.appendChild(host);
  const shadow = host.attachShadow({ mode: 'open' });
  shadow.innerHTML = `
    <style>
      :host { all: initial; }
      .panel {
        width: 300px;
        box-sizing: border-box;
        background: #fff;
        color: #1f2937;
        border: 1px solid #e5e7eb;
        border-radius: 12px;
        box-shadow: 0 12px 32px rgba(15, 23, 42, .18);
        font: 13px/1.55 system-ui, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif;
        overflow: hidden;
      }
      .head {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        background: #f8fafc;
        border-bottom: 1px solid #eef2f7;
      }
      .title { flex: 1; font-weight: 700; font-size: 13px; }
      .collapse {
        border: 0; background: transparent; color: #64748b;
        font-size: 16px; line-height: 1; cursor: pointer; padding: 2px 4px;
      }
      .body { padding: 12px; }
      .hint { color: #64748b; font-size: 11px; margin: 0 0 9px; }
      .primary {
        width: 100%;
        border: 0;
        border-radius: 8px;
        padding: 9px 12px;
        background: #2563eb;
        color: #fff;
        font-size: 13px;
        font-weight: 600;
        cursor: pointer;
      }
      .primary:hover { background: #1d4ed8; }
      .primary:disabled { opacity: .55; cursor: wait; }
      .status {
        display: none;
        margin-top: 9px;
        padding: 8px 9px;
        border-radius: 8px;
        background: #f1f5f9;
        color: #334155;
        font-size: 11px;
        white-space: pre-wrap;
        word-break: break-word;
      }
      .status.show { display: block; }
      .status.ok { background: #ecfdf5; color: #166534; }
      .status.error { background: #fef2f2; color: #b91c1c; }
      .actions { display: none; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 9px; }
      .actions.show { display: grid; }
      .actions button {
        border: 1px solid #dbe3ee;
        border-radius: 7px;
        padding: 6px 4px;
        background: #fff;
        color: #334155;
        font-size: 11px;
        cursor: pointer;
      }
      .actions button:hover { background: #f8fafc; border-color: #bfdbfe; }
      .foot { margin-top: 9px; color: #94a3b8; font-size: 10px; }
    </style>
    <div class="panel">
      <div class="head">
        <div class="title">北理工课表导出</div>
        <button class="collapse" title="收起">—</button>
      </div>
      <div class="body">
        <p class="hint">点击按钮开始抓取当前学期课表</p>
        <button class="primary">抓取课表</button>
        <div class="status"></div>
        <div class="actions">
          <button data-act="download">下载课表 JSON</button>
          <button data-act="copy">复制课表 JSON</button>
          <button data-act="download-raw">下载原始数据</button>
          <button data-act="copy-raw">复制原始数据</button>
        </div>
        <div class="foot">导入 App：导入数据 → 从文件选择 → 选刚下载的 JSON</div>
      </div>
    </div>
  `;

  const panel = shadow.querySelector('.panel');
  const bodyEl = shadow.querySelector('.body');
  const collapseBtn = shadow.querySelector('.collapse');
  const primaryBtn = shadow.querySelector('.primary');
  const statusEl = shadow.querySelector('.status');
  const actionsEl = shadow.querySelector('.actions');

  function setStatus(text, kind) {
    statusEl.textContent = text || '';
    statusEl.className = 'status' + (text ? ' show' : '') + (kind ? ' ' + kind : '');
  }

  function setActionsVisible(visible) {
    actionsEl.classList.toggle('show', !!visible);
  }

  function setBusy(busy) {
    primaryBtn.disabled = busy;
    primaryBtn.textContent = busy ? '正在抓取…' : state.dataText ? '重新抓取' : '抓取课表';
  }

  async function run() {
    if (state.busy) return;
    state.busy = true;
    setBusy(true);
    setActionsVisible(false);
    setStatus('正在读取学期信息和课表，请稍候…');
    try {
      const result = await fetchAll();
      const term = safeFilePart(result.data.termName);
      state.dataText = JSON.stringify(result.data, null, 2);
      state.rawText = JSON.stringify(result.raw, null, 2);
      state.fileBase = `timetable-${safeFilePart(result.data.termStart)}-${term}`;
      setStatus(
        result.text +
          '\n学期：' +
          result.data.termName +
          '（' +
          result.data.termStart +
          ' 起，共 ' +
          result.data.regularWeeks +
          ' 周）\n已自动下载课表 JSON；若被浏览器拦截，请点下方“下载课表 JSON”。',
        'ok'
      );
      setActionsVisible(true);
      download(state.fileBase + '.json', state.dataText);
    } catch (e) {
      setStatus('抓取失败：' + ((e && e.message) || e), 'error');
      setActionsVisible(false);
    } finally {
      state.busy = false;
      setBusy(false);
    }
  }

  primaryBtn.addEventListener('click', run);

  collapseBtn.addEventListener('click', () => {
    const collapsed = bodyEl.style.display === 'none';
    bodyEl.style.display = collapsed ? '' : 'none';
    panel.style.width = collapsed ? '' : 'auto';
    collapseBtn.textContent = collapsed ? '—' : '+';
  });

  actionsEl.addEventListener('click', async (event) => {
    const btn = event.target.closest('button[data-act]');
    if (!btn) return;
    const act = btn.getAttribute('data-act');
    if (!state.dataText) return;
    try {
      if (act === 'download') {
        download(state.fileBase + '.json', state.dataText);
        setStatus('已开始下载课表 JSON。', 'ok');
      } else if (act === 'copy') {
        await copyText(state.dataText);
        setStatus('课表 JSON 已复制到剪贴板。', 'ok');
      } else if (act === 'download-raw') {
        download(state.fileBase + '-raw.json', state.rawText);
        setStatus('已开始下载原始数据（接口原始记录）。', 'ok');
      } else if (act === 'copy-raw') {
        await copyText(state.rawText);
        setStatus('原始数据已复制到剪贴板。', 'ok');
      }
    } catch (e) {
      setStatus('操作失败：' + ((e && e.message) || e), 'error');
    }
  });

  console.log('[BIT课表导出] 已加载，等待手动点击。');
})();
