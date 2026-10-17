import { api } from './api.js';
import {
    esc, fmt, STATUS, STATUS_ORDER, WASTE, statusPill, routePill, vehiclePill,
    toast, confirmDialog, busy, cssVar,
} from './ui.js';
import { DistrictMap, vehicleSlot, isDark } from './maps.js';

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

// =====================================================================
// State
// =====================================================================
const state = {
    view: 'overview',
    summary: null,
    bins: [],
    routes: [],
    recent: [],
    fleet: [],
    alerts: [],
    alertFilter: 'open',
    comparison: null,
    daily: [],
    zones: [],
    sim: null,
    sort: { key: 'status', dir: -1 },
    binFilter: { q: '', statuses: new Set(), zone: '' },
    mapStatuses: new Set(Object.keys(STATUS)),
    showRoutes: true,
    drawerBinId: null,
    lastAlertId: null,
};

const VIEWS = {
    overview: ['Overview', 'Live status across the district'],
    map: ['Live map', 'Bins, routes and trucks in real time'],
    bins: ['Bins', 'Sensor readings and overflow forecasts'],
    routes: ['Routes & fleet', 'Plan, dispatch and track collections'],
    impact: ['Sustainability', 'Distance, fuel and CO₂ compared with a fixed schedule'],
    alerts: ['Alerts', 'Overflow warnings and route events'],
};

let overviewMap = null;
let mainMap = null;
let forecastChart = null;
let dailyChart = null;
let picker = null;

// =====================================================================
// Routing
// =====================================================================
function showView(view) {
    if (!VIEWS[view]) view = 'overview';
    state.view = view;
    $$('.view').forEach((v) => { v.hidden = v.dataset.view !== view; });
    $$('.nav-link').forEach((a) => a.classList.toggle('active', a.dataset.view === view));
    $('#viewTitle').textContent = VIEWS[view][0];
    $('#viewSubtitle').textContent = VIEWS[view][1];
    document.title = `${VIEWS[view][0]} · EcoRoute`;
    closeSidebar();

    if (view === 'overview') { ensureOverviewMap(); overviewMap.invalidate(); }
    if (view === 'map') { ensureMainMap(); mainMap.invalidate(); }
    refresh(true);
}
window.addEventListener('hashchange', () => showView(location.hash.slice(1)));

// =====================================================================
// Data refresh (driven by server-sent ticks, throttled)
// =====================================================================
let refreshing = false;
let queued = false;
let tickCount = 0;
let lastRefresh = 0;

async function refresh(full = false) {
    if (refreshing) { queued = true; return; }
    refreshing = true;
    lastRefresh = Date.now();
    try {
        const [summary, bins, routes, fleet, sim] = await Promise.all([
            api.summary(), api.bins(), api.activeRoutes(), api.fleet(), api.simulation(),
        ]);
        routes.sort((a, b) => (a.vehicleId ?? 0) - (b.vehicleId ?? 0) || a.id - b.id);
        Object.assign(state, { summary, bins, routes, fleet, sim });
        tickCount++;

        const v = state.view;
        const extra = [];
        if ((v === 'overview' || v === 'impact') && (full || !state.comparison || tickCount % 3 === 0)) {
            extra.push(api.comparison().then((c) => { state.comparison = c; }));
        }
        if (v === 'overview' && (full || tickCount % 3 === 0 || !state.zones.length)) {
            extra.push(api.zones().then((z) => { state.zones = z; }));
        }
        if (v === 'overview' || v === 'alerts' || full || tickCount % 5 === 0) {
            extra.push(api.alerts(false).then((a) => { state.alerts = a; }));
        }
        if (v === 'impact' && (full || tickCount % 5 === 0 || !state.daily.length)) {
            extra.push(api.daily(7).then((d) => { state.daily = d; }));
        }
        if (v === 'routes') extra.push(api.recentRoutes().then((r) => { state.recent = r; }));
        await Promise.all(extra);

        renderAll();
        if (state.drawerBinId) refreshDrawer();
    } catch (e) {
        console.error(e);
        setLive('off');
    } finally {
        refreshing = false;
        if (queued) {
            queued = false;
            setTimeout(() => refresh(), 300);
        }
    }
}

function scheduleRefresh() {
    const wait = Math.max(0, 1500 - (Date.now() - lastRefresh));
    clearTimeout(scheduleRefresh.t);
    scheduleRefresh.t = setTimeout(() => refresh(), wait);
}

// Live connection --------------------------------------------------------
let stream = null;
function connectStream() {
    stream = new EventSource('/api/stream');
    stream.addEventListener('hello', () => setLive(state.sim?.paused ? 'paused' : 'on'));
    stream.addEventListener('tick', () => {
        setLive('on');
        scheduleRefresh();
    });
    stream.addEventListener('alert', (e) => {
        try {
            const a = JSON.parse(e.data);
            if (a.severity !== 'INFO' || a.type === 'ROUTE_COMPLETED') {
                const title = {
                    OVERFLOW_RISK: 'Overflow risk', URGENT: 'Bin needs collection', ROUTE_COMPLETED: 'Route completed',
                    UNASSIGNED_BINS: 'Fleet at capacity',
                }[a.type] || 'Alert';
                toast(title, a.message, a.severity, a.severity === 'CRITICAL' ? 8000 : 5000);
            }
        } catch { /* ignore malformed */ }
        scheduleRefresh();
    });
    stream.onerror = () => setLive('off');
}
// Fallback: keep data fresh even if the event stream is blocked by a proxy.
setInterval(() => { if (Date.now() - lastRefresh > 8000) refresh(); }, 4000);

function setLive(mode) {
    const pill = $('#livePill');
    const paused = state.sim?.paused;
    const m = mode === 'on' && paused ? 'paused' : mode;
    pill.classList.toggle('on', m === 'on');
    pill.classList.toggle('paused', m === 'paused');
    $('.lbl', pill).textContent = { on: 'Live', paused: 'Paused', off: 'Reconnecting' }[m] || 'Connecting';
}

// =====================================================================
// Rendering
// =====================================================================
function renderAll() {
    renderChrome();
    switch (state.view) {
        case 'overview': renderOverview(); break;
        case 'map': renderMainMap(); break;
        case 'bins': renderBins(); break;
        case 'routes': renderRoutes(); break;
        case 'impact': renderImpact(); break;
        case 'alerts': renderAlerts(); break;
    }
}

function renderChrome() {
    const s = state.summary;
    const sim = state.sim;
    if (sim) {
        const t = new Date(sim.simTime);
        $('#simTime').textContent = t.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        $('#simDate').textContent = t.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'short' }) + (sim.paused ? ' · paused' : '');
        $$('#simSpeed button').forEach((b) => b.setAttribute('aria-checked', String(Number(b.dataset.speed) === sim.speed)));
        const pb = $('#simPauseBtn');
        pb.innerHTML = `<i class="fa-solid ${sim.paused ? 'fa-play' : 'fa-pause'}"></i>`;
        pb.title = sim.paused ? 'Resume simulation' : 'Pause simulation';
        $('#autopilotToggle').checked = !!sim.autopilot;
        if (sim.paused) setLive('paused');
    }
    if (s) {
        $('#navBinsCount').textContent = s.totalBins;
        $('#navRoutesCount').textContent = s.activeRoutes || '';
        const badge = $('#navAlertCount');
        badge.hidden = !s.openAlerts;
        badge.textContent = s.openAlerts > 99 ? '99+' : s.openAlerts;
    }
}

// ---------------------------------------------------------------- overview
function renderOverview() {
    const s = state.summary;
    if (!s) return;
    $('#kpiPickup').textContent = s.needsPickup;
    $('#kpiPickupOf').textContent = `of ${s.totalBins} bins`;
    $('#kpiStatusBar').innerHTML = STATUS_ORDER
        .map((k) => `<span class="c-${k}" style="flex-grow:${s.statusCounts[k] || 0}" title="${STATUS[k].label}: ${s.statusCounts[k] || 0}"></span>`).join('');
    $('#kpiStatusLegend').innerHTML = STATUS_ORDER
        .map((k) => `<span class="lg"><span class="dot-c c-${k}"></span>${STATUS[k].label} <b>${s.statusCounts[k] || 0}</b></span>`).join('');

    $('#kpiAvgFill').textContent = fmt.pct(s.averageFill);
    $('#kpiLoad').textContent = `${fmt.litres(s.totalLoadLitres)} of waste waiting`;

    $('#kpiFleet').innerHTML = `${s.vehiclesOnRoute}<span class="kpi-of">on route</span>`;
    $('#kpiFleetFoot').textContent = `${s.vehiclesAvailable} available · ${s.activeRoutes} active route${s.activeRoutes === 1 ? '' : 's'}`;

    const c = state.comparison;
    if (c) {
        const saved = Math.max(0, c.baseline.estimatedCo2Kg - c.optimized.estimatedCo2Kg);
        $('#kpiCo2').textContent = fmt.kg(saved);
        $('#kpiCo2Foot').innerHTML = c.optimized.binsCollected
            ? `<span class="delta-good"><i class="fa-solid fa-arrow-down"></i> ${fmt.pct(c.co2SavedPct)}</span> vs visiting all ${c.baseline.binsCollected} bins`
            : `No trip needed: a fixed round would emit this much`;
    }

    ensureOverviewMap();
    overviewMap.update({ bins: state.bins, routes: state.routes, fleet: state.fleet, depot: state.sim?.depot });

    // Next to overflow
    const upcoming = state.bins
        .filter((b) => b.estimatedHoursToFull != null || b.status === 'OVERFLOW_RISK')
        .sort((a, b) => (a.estimatedHoursToFull ?? 0) - (b.estimatedHoursToFull ?? 0))
        .slice(0, 7);
    $('#nextOverflowList').innerHTML = upcoming.length ? upcoming.map((b) => `
        <li data-bin="${b.id}">
            <div>
                <div class="rl-name">${esc(b.binCode)} · ${esc(b.locationName)}</div>
                <div class="rl-sub">${statusPill(b.status)} ${b.onActiveRoute ? '<span class="on-route"><i class="fa-solid fa-truck"></i> scheduled</span>' : ''}</div>
            </div>
            <div class="rl-eta">${b.estimatedHoursToFull != null && b.estimatedHoursToFull > 0 ? fmt.hours(b.estimatedHoursToFull) : 'Full'}
                <small>${fmt.pct(b.currentFillPercentage)} now</small></div>
        </li>`).join('') : '<li class="empty">No bins are trending towards full.</li>';

    // Zones
    $('#zoneBars').innerHTML = state.zones.map((z) => `
        <div class="zone-row">
            <span class="zn" title="${esc(z.zone)}">${esc(z.zone)}</span>
            <span class="track"><span class="bar" style="width:${Math.min(100, z.averageFill).toFixed(1)}%"></span><span class="tick" title="90% urgent threshold"></span></span>
            <span class="zv">${fmt.pct(z.averageFill)}<small>${z.needsPickup ? `${z.needsPickup} due` : `${z.bins} bins`}</small></span>
        </div>`).join('');

    renderFeed($('#activityFeed'), state.alerts.slice(0, 8));
}

function renderFeed(el, alerts) {
    const icon = { OVERFLOW_RISK: 'fa-fire', URGENT: 'fa-triangle-exclamation', ROUTE_COMPLETED: 'fa-flag-checkered', ROUTE_DISPATCHED: 'fa-truck-fast', UNASSIGNED_BINS: 'fa-weight-hanging', BIN_COLLECTED: 'fa-check' };
    el.innerHTML = alerts.length ? alerts.map((a) => `
        <li>
            <span class="ic sev-${a.severity}"><i class="fa-solid ${icon[a.type] || 'fa-bell'}"></i></span>
            <div><div class="msg">${esc(a.message)}</div><time>${fmt.ago(a.createdAt, state.sim?.simTime)}</time></div>
        </li>`).join('') : '<li class="empty">Nothing yet.</li>';
}

function ensureOverviewMap() {
    if (!overviewMap) overviewMap = new DistrictMap($('#overviewMap'), { onBinClick: openBin, scrollWheelZoom: false });
}

// ---------------------------------------------------------------- map view
function ensureMainMap() {
    if (mainMap) return;
    mainMap = new DistrictMap($('#mainMap'), { onBinClick: openBin });
    const counts = () => Object.fromEntries(Object.keys(STATUS).map((k) => [k, state.bins.filter((b) => b.status === k).length]));
    const box = $('#mapStatusFilter');
    box.innerHTML = STATUS_ORDER.map((k) => `<button class="chip" data-status="${k}" aria-pressed="true"><span class="dot-c c-${k}"></span>${STATUS[k].label} <span class="cnt" data-cnt="${k}"></span></button>`).join('');
    box.addEventListener('click', (e) => {
        const chip = e.target.closest('.chip');
        if (!chip) return;
        const k = chip.dataset.status;
        if (state.mapStatuses.has(k)) state.mapStatuses.delete(k); else state.mapStatuses.add(k);
        chip.setAttribute('aria-pressed', String(state.mapStatuses.has(k)));
        renderMainMap();
    });
    $('#mapShowRoutes').addEventListener('change', (e) => { state.showRoutes = e.target.checked; renderMainMap(); });
    renderMainMap.counts = counts;
}

function renderMainMap() {
    ensureMainMap();
    mainMap.update({
        bins: state.bins, routes: state.routes, fleet: state.fleet, depot: state.sim?.depot,
        statuses: state.mapStatuses, showRoutes: state.showRoutes,
    });
    const c = renderMainMap.counts();
    $$('#mapStatusFilter [data-cnt]').forEach((el) => { el.textContent = c[el.dataset.cnt]; });
    $('#mapRouteLegend').innerHTML = state.routes.length ? state.routes.map((r) => `
        <div class="map-route-row" style="--c:var(--v${vehicleSlot(state.fleet, r.vehicleId)})">
            <span class="ln ${r.status === 'IN_PROGRESS' ? '' : 'dash'}"></span>
            <b>${esc(r.vehicleNumber || '#' + r.id)}</b>
            <span>${r.stops.filter((s) => s.collected).length}/${r.stops.length} stops · ${r.status === 'IN_PROGRESS' ? 'driving' : 'planned'}</span>
        </div>`).join('') : '<span class="muted small">No active routes. Use “Plan routes”.</span>';
}

// ---------------------------------------------------------------- bins view
function buildBinFilters() {
    const box = $('#binStatusFilter');
    box.innerHTML = STATUS_ORDER.map((k) => `<button class="chip" data-status="${k}" aria-pressed="false"><span class="dot-c c-${k}"></span>${STATUS[k].label} <span class="cnt" data-cnt="${k}"></span></button>`).join('');
    box.addEventListener('click', (e) => {
        const chip = e.target.closest('.chip');
        if (!chip) return;
        const k = chip.dataset.status;
        const set = state.binFilter.statuses;
        if (set.has(k)) set.delete(k); else set.add(k);
        chip.setAttribute('aria-pressed', String(set.has(k)));
        renderBins();
    });
    $('#binSearch').addEventListener('input', (e) => { state.binFilter.q = e.target.value.trim().toLowerCase(); renderBins(); });
    $('#binZoneFilter').addEventListener('change', (e) => { state.binFilter.zone = e.target.value; renderBins(); });
    $$('#binsTable th[data-sort]').forEach((th) => th.addEventListener('click', () => {
        const key = th.dataset.sort;
        state.sort = state.sort.key === key ? { key, dir: -state.sort.dir } : { key, dir: key === 'binCode' || key === 'zone' || key === 'estimatedHoursToFull' ? 1 : -1 };
        renderBins();
    }));
}

function sortValue(b, key) {
    if (key === 'status') return STATUS[b.status].rank * 1000 + b.currentFillPercentage;
    if (key === 'estimatedHoursToFull') return b.estimatedHoursToFull ?? (b.status === 'OVERFLOW_RISK' ? -1 : 1e9);
    const v = b[key];
    return typeof v === 'string' ? v.toLowerCase() : v ?? 0;
}

function renderBins() {
    const { q, statuses, zone } = state.binFilter;
    const zones = [...new Set(state.bins.map((b) => b.zone).filter(Boolean))].sort();
    const zoneSel = $('#binZoneFilter');
    if (zoneSel.options.length - 1 !== zones.length) {
        zoneSel.innerHTML = '<option value="">All zones</option>' + zones.map((z) => `<option>${esc(z)}</option>`).join('');
        zoneSel.value = zone;
        $('#zoneOptions').innerHTML = zones.map((z) => `<option value="${esc(z)}">`).join('');
    }
    Object.keys(STATUS).forEach((k) => {
        const el = $(`#binStatusFilter [data-cnt="${k}"]`);
        if (el) el.textContent = state.bins.filter((b) => b.status === k).length;
    });

    const { key, dir } = state.sort;
    const rows = state.bins
        .filter((b) => (!statuses.size || statuses.has(b.status))
            && (!zone || b.zone === zone)
            && (!q || `${b.binCode} ${b.locationName} ${b.zone}`.toLowerCase().includes(q)))
        .sort((a, b) => {
            const x = sortValue(a, key), y = sortValue(b, key);
            return (x < y ? -1 : x > y ? 1 : 0) * dir;
        });

    $$('#binsTable th[data-sort]').forEach((th) => {
        th.classList.toggle('sorted', th.dataset.sort === key);
        th.classList.toggle('asc', th.dataset.sort === key && dir === 1);
    });
    $('#binsEmpty').hidden = rows.length > 0;
    $('#binsTable tbody').innerHTML = rows.map((b) => {
        const conf = Math.round((b.confidence || 0) * 5);
        const w = WASTE[b.wasteType] || WASTE.GENERAL;
        return `<tr data-bin="${b.id}">
            <td><div class="cell-main">${esc(b.binCode)}${b.onActiveRoute ? '<span class="on-route" title="On an active route"><i class="fa-solid fa-truck"></i></span>' : ''}</div>
                <div class="type-tag"><i class="fa-solid ${w.icon}"></i>${w.label} · ${fmt.litres(b.capacity)}</div></td>
            <td><div>${esc(b.locationName)}</div><div class="cell-sub">${esc(b.zone || '')}</div></td>
            <td><div class="fill-cell c-${b.status}"><span class="val">${fmt.pct(b.currentFillPercentage)}</span><span class="meter"><span style="width:${Math.min(100, b.currentFillPercentage)}%"></span></span></div></td>
            <td class="num">${fmt.pct(b.predictedFillPercentage)}</td>
            <td class="num">${b.estimatedHoursToFull != null ? fmt.hours(b.estimatedHoursToFull) : (b.status === 'OVERFLOW_RISK' ? 'now' : '–')}</td>
            <td class="num"><span class="conf" title="R² ${(b.confidence || 0).toFixed(2)}">${[1, 2, 3, 4, 5].map((i) => `<i class="${i <= conf ? 'on' : ''}"></i>`).join('')}</span></td>
            <td>${statusPill(b.status)}</td>
            <td class="actions"><div class="row-menu">
                <button class="icon-btn sm" data-act="collect" title="Mark emptied" aria-label="Mark ${esc(b.binCode)} emptied"><i class="fa-solid fa-check"></i></button>
                <button class="icon-btn sm" data-act="edit" title="Edit" aria-label="Edit ${esc(b.binCode)}"><i class="fa-solid fa-pen"></i></button>
                <button class="icon-btn sm" data-act="delete" title="Delete" aria-label="Delete ${esc(b.binCode)}"><i class="fa-solid fa-trash"></i></button>
            </div></td>
        </tr>`;
    }).join('');
}

$('#binsTable tbody').addEventListener('click', async (e) => {
    const tr = e.target.closest('tr[data-bin]');
    if (!tr) return;
    const id = Number(tr.dataset.bin);
    const act = e.target.closest('[data-act]')?.dataset.act;
    if (act === 'edit') return openBinForm(state.bins.find((b) => b.id === id));
    if (act === 'delete') return deleteBin(id);
    if (act === 'collect') return collectBin(id, e.target.closest('button'));
    openBin(id);
});

async function deleteBin(id) {
    const b = state.bins.find((x) => x.id === id);
    const ok = await confirmDialog(`Delete ${b?.binCode ?? 'bin'}?`, 'Its sensor history is removed as well. Routes that visited it keep their record.', 'Delete bin');
    if (!ok) return;
    await busy(null, () => api.deleteBin(id));
    toast('Bin deleted', `${b?.binCode ?? ''} was removed.`);
    if (state.drawerBinId === id) closeDrawer();
    refresh(true);
}

async function collectBin(id, button) {
    const b = await busy(button, () => api.collectBin(id));
    toast('Bin emptied', `${b.binCode} is back to ${fmt.pct(b.currentFillPercentage)}.`);
    refresh(true);
}

// ---------------------------------------------------------------- routes view
function renderRoutes() {
    const list = $('#routeList');
    const needs = state.summary?.needsPickup ?? 0;
    if (!state.routes.length) {
        list.innerHTML = `<div class="card route-empty">
            <i class="fa-solid fa-route"></i>
            <h3>No active routes</h3>
            <p class="muted">${needs ? `${needs} bin${needs === 1 ? '' : 's'} need${needs === 1 ? 's' : ''} pickup right now.` : 'Every bin is below its collection threshold.'}</p>
            ${needs ? '<p style="margin-top:14px"><button class="btn primary" data-plan><i class="fa-solid fa-wand-magic-sparkles"></i> Plan routes</button></p>' : ''}
        </div>`;
    } else {
        list.innerHTML = state.routes.map(routeCard).join('');
    }
    $('#dispatchAllBtn').disabled = !state.routes.some((r) => r.status === 'PLANNED');

    $('#fleetList').innerHTML = state.fleet.map((v, i) => `
        <li style="--c:var(--v${Math.min(i, 2) + 1})">
            <span class="fleet-ic"><i class="fa-solid fa-truck"></i></span>
            <div class="fl-main"><b>${esc(v.vehicleNumber)}</b><span>${esc(v.driverName || '')} · ${fmt.litres(v.capacity)} · ${v.fuelEfficiency} km/L</span></div>
            ${vehiclePill(v.status)}
        </li>`).join('');

    const history = state.recent.filter((r) => r.status === 'COMPLETED' || r.status === 'CANCELLED').slice(0, 8);
    $('#routeHistory').innerHTML = history.length ? history.map((r) => `
        <li>
            <div><b>#${r.id} · ${esc(r.vehicleNumber || '')}</b>
                <div class="hl-sub">${r.stops.length} bins · ${fmt.km(r.totalDistanceKm)} · ${fmt.kg(r.estimatedCo2Kg)} CO₂ · ${fmt.dayTime(r.completedAt)}</div></div>
            ${routePill(r.status)}
        </li>`).join('') : '<li class="muted small">Completed routes will appear here.</li>';
}

function routeCard(r) {
    const slot = vehicleSlot(state.fleet, r.vehicleId);
    const done = r.stops.filter((s) => s.collected).length;
    const loadPct = r.vehicleCapacityLitres ? (r.totalLoadLitres / r.vehicleCapacityLitres) * 100 : 0;
    const twoOpt = r.unoptimizedDistanceKm - r.totalDistanceKm;
    const eta = r.dispatchedAt ? new Date(new Date(r.dispatchedAt).getTime() + r.estimatedTravelTimeMinutes * 60000).toISOString() : null;
    return `<article class="card route-card" style="--c:var(--v${slot})">
        <div class="route-head">
            <span class="fleet-ic" style="--c:var(--v${slot})"><i class="fa-solid fa-truck"></i></span>
            <div><h3>${esc(r.vehicleNumber || 'Unassigned')} · route #${r.id}</h3>
                <div class="sub">${r.status === 'IN_PROGRESS' ? `Dispatched ${fmt.clock(r.dispatchedAt)} · back ~${fmt.clock(eta)}` : `Planned ${fmt.clock(r.generatedAt)}`}</div></div>
            <span class="spacer"></span>
            ${routePill(r.status)}
        </div>
        <div class="route-metrics">
            <div><b>${r.stops.length}</b><span>stops</span></div>
            <div><b>${fmt.km(r.totalDistanceKm)}</b><span>distance</span></div>
            <div><b>${fmt.minutes(r.estimatedTravelTimeMinutes)}</b><span>duration</span></div>
            <div><b>${r.estimatedFuelLitres.toFixed(1)} L</b><span>diesel</span></div>
            <div><b>${fmt.kg(r.estimatedCo2Kg)}</b><span>CO₂</span></div>
        </div>
        <div class="route-body">
            ${r.status === 'IN_PROGRESS' ? `<div class="load-row"><span>${done}/${r.stops.length} collected</span><span class="progress" style="flex:1"><span style="width:${(r.progress * 100).toFixed(1)}%"></span></span><span>${Math.round(r.progress * 100)}%</span></div>` : ''}
            <div class="load-row"><span>Load</span><span class="meter" style="--c:var(--v${slot})"><span style="width:${Math.min(100, loadPct).toFixed(1)}%"></span></span><span class="tabular">${fmt.litres(r.totalLoadLitres)} / ${fmt.litres(r.vehicleCapacityLitres)}</span></div>
            <ol class="stops">${r.stops.map((s, i) => `<li class="${s.collected ? 'done' : ''}" data-bin="${s.binId ?? ''}" title="${esc(s.locationName)} · ${fmt.pct(s.fillAtPlanning)} at planning">
                <span class="n">${s.collected ? '<i class="fa-solid fa-check"></i>' : i + 1}</span>${esc(s.binCode)}</li>`).join('')}</ol>
            <div class="route-actions">
                ${r.status === 'PLANNED' ? `<button class="btn primary sm" data-route="${r.id}" data-act="dispatch"><i class="fa-solid fa-paper-plane"></i> Dispatch</button>` : ''}
                ${r.status === 'IN_PROGRESS' ? `<button class="btn primary sm" data-route="${r.id}" data-act="complete"><i class="fa-solid fa-flag-checkered"></i> Complete now</button>` : ''}
                <button class="btn ghost sm" data-route="${r.id}" data-act="cancel">Cancel</button>
                <span class="toolbar-spacer"></span>
                ${twoOpt > 0.05 ? `<span class="route-note"><i class="fa-solid fa-scissors"></i> 2-opt saved ${fmt.km(twoOpt)}</span>` : ''}
            </div>
        </div>
    </article>`;
}

$('#routeList').addEventListener('click', async (e) => {
    if (e.target.closest('[data-plan]')) return planRoutes(e.target.closest('button'));
    const stop = e.target.closest('.stops li[data-bin]');
    if (stop && stop.dataset.bin) return openBin(Number(stop.dataset.bin));
    const btn = e.target.closest('button[data-route]');
    if (!btn) return;
    const id = Number(btn.dataset.route);
    const act = btn.dataset.act;
    if (act === 'cancel') {
        const ok = await confirmDialog(`Cancel route #${id}?`, 'Bins not yet collected go back into the pickup pool.', 'Cancel route');
        if (!ok) return;
        await busy(btn, () => api.cancelRoute(id));
        toast('Route cancelled');
    } else if (act === 'dispatch') {
        const r = await busy(btn, () => api.dispatchRoute(id));
        toast(`${r.vehicleNumber} dispatched`, `${r.stops.length} stops · back in about ${fmt.minutes(r.estimatedTravelTimeMinutes)} (sim time).`);
    } else if (act === 'complete') {
        const r = await busy(btn, () => api.completeRoute(id));
        toast('Route completed', `${r.stops.length} bins emptied.`);
    }
    refresh(true);
});

async function planRoutes(button) {
    const res = await busy(button, () => api.planRoutes($('#includeSoon').checked));
    if (res.routes.length) {
        toast('Routes planned', res.message);
        if (location.hash !== '#routes') location.hash = '#routes';
    } else {
        toast('Nothing to plan', res.message, res.candidateBins ? 'err' : 'ok');
    }
    refresh(true);
}

$('#planBtn').addEventListener('click', (e) => planRoutes(e.currentTarget));
$('#planBtnTop').addEventListener('click', (e) => planRoutes(e.currentTarget));
$('#dispatchAllBtn').addEventListener('click', async (e) => {
    const planned = state.routes.filter((r) => r.status === 'PLANNED');
    await busy(e.currentTarget, async () => {
        for (const r of planned) await api.dispatchRoute(r.id);
    });
    toast(`${planned.length} route${planned.length === 1 ? '' : 's'} dispatched`, 'Watch the trucks on the live map.');
    refresh(true);
});

// ---------------------------------------------------------------- impact view
function renderImpact() {
    const c = state.comparison;
    if (c) {
        const nothingDue = c.optimized.binsCollected === 0;
        $('#heroCo2Pct').textContent = nothingDue ? `0 of ${c.baseline.binsCollected}` : fmt.pct(c.co2SavedPct);
        $('#heroCo2Pct').nextElementSibling.textContent = nothingDue ? 'bins need emptying' : 'less CO₂';
        $('#heroSentence').textContent = nothingDue
            ? `No trip needed. A fixed schedule would still drive ${fmt.km(c.baseline.totalDistanceKm)} and emit ${fmt.kg(c.baseline.estimatedCo2Kg)} of CO₂ this cycle.`
            : `than a fixed schedule that visits all ${c.baseline.binsCollected} bins. Only ${c.optimized.binsCollected} actually need emptying right now.`;
        $('#heroAnnual').textContent = nothingDue ? '–' : fmt.num(c.annualCo2SavedKg);
        $('#heroTrees').textContent = nothingDue ? '–' : fmt.num(c.treesEquivalent);
        $('#heroSkipped').textContent = c.binsSkipped;

        const metric = (title, base, opt, unit, d) => {
            const max = Math.max(base, opt, 1e-9);
            const saved = base > 0 ? ((base - opt) / base) * 100 : 0;
            return `<div class="cmp"><h3>${title}</h3>
                <div class="cmp-row"><span class="lbl">Fixed</span><div class="cmp-track"><div class="cmp-bar base" style="width:${(base / max) * 100}%"><span class="cmp-val">${base.toFixed(d)} ${unit}</span></div></div></div>
                <div class="cmp-row"><span class="lbl">Optimised</span><div class="cmp-track"><div class="cmp-bar opt" style="width:${(opt / max) * 100}%"><span class="cmp-val">${opt.toFixed(d)} ${unit}</span></div></div></div>
                <div class="cmp-save">${nothingDue ? 'No trip needed right now' : `<b>${saved.toFixed(0)}% less</b> per collection cycle`}</div></div>`;
        };
        $('#compareBars').innerHTML =
            metric('Distance driven', c.baseline.totalDistanceKm, c.optimized.totalDistanceKm, 'km', 1)
            + metric('Diesel burned', c.baseline.estimatedFuelLitres, c.optimized.estimatedFuelLitres, 'L', 1)
            + metric('CO₂ emitted', c.baseline.estimatedCo2Kg, c.optimized.estimatedCo2Kg, 'kg', 1);

        const lifetime = state.summary?.lifetime;
        const gauge = (label, v, note, color) => `<div class="gauge"><div class="g-head"><span>${label}</span><b>${fmt.pct(v)}</b></div>
            <div class="meter" style="--c:${color}"><span style="width:${Math.min(100, v)}%"></span></div><p class="muted">${note}</p></div>`;
        $('#fillAtPickup').innerHTML =
            gauge('Fixed schedule', c.baseline.averageFillAtPickup, 'Empties every bin, however full', 'var(--muted)')
            + (nothingDue ? '' : gauge('Data-driven (now)', c.optimized.averageFillAtPickup, 'Only bins that need it', 'var(--brand)'))
            + (lifetime?.binsCollected ? gauge('Data-driven (completed routes)', lifetime.averageFillAtPickup, `${lifetime.binsCollected} pickups so far`, 'var(--brand)') : '');
    }

    const l = state.summary?.lifetime;
    if (l) {
        $('#lifetimeStats').innerHTML = `
            <div><dt>Routes completed</dt><dd>${l.routesCompleted}</dd></div>
            <div><dt>Bins emptied</dt><dd>${l.binsCollected}</dd></div>
            <div><dt>Waste collected</dt><dd>${fmt.litres(l.wasteCollectedLitres)}</dd></div>
            <div><dt>Distance</dt><dd>${fmt.km(l.distanceKm)}</dd></div>
            <div><dt>Diesel</dt><dd>${l.fuelLitres.toFixed(1)} <small>L</small></dd></div>
            <div><dt>CO₂ emitted</dt><dd>${fmt.kg(l.co2Kg)}</dd></div>`;
    }
    renderDailyChart();
}

function chartColors() {
    return {
        text: cssVar('--text-2'), muted: cssVar('--muted'), grid: cssVar('--grid'), surface: cssVar('--surface'),
        brand: cssVar('--brand'), urgent: cssVar('--st-urgent'), font: cssVar('--font'),
    };
}

function baseChartOptions(c) {
    return {
        responsive: true, maintainAspectRatio: false, animation: { duration: 300 },
        interaction: { mode: 'index', intersect: false },
        plugins: {
            legend: { display: false },
            tooltip: { backgroundColor: c.surface, titleColor: cssVar('--text'), bodyColor: c.text, borderColor: cssVar('--border-strong'), borderWidth: 1, padding: 10, cornerRadius: 8 },
        },
        scales: {
            x: { grid: { display: false }, border: { color: c.grid }, ticks: { color: c.muted, font: { family: c.font, size: 11 } } },
            y: { grid: { color: c.grid }, border: { display: false }, ticks: { color: c.muted, font: { family: c.font, size: 11 } }, beginAtZero: true },
        },
    };
}

function renderDailyChart() {
    const d = state.daily;
    const c = chartColors();
    const labels = d.map((x) => new Date(x.date + 'T00:00').toLocaleDateString([], { weekday: 'short', day: 'numeric' }));
    const data = d.map((x) => x.binsCollected);
    if (!dailyChart) {
        const opts = baseChartOptions(c);
        opts.scales.y.ticks.precision = 0;
        opts.plugins.tooltip.callbacks = {
            label: (ctx) => {
                const row = d[ctx.dataIndex];
                return ` ${row.binsCollected} bins · ${row.routes} routes · ${row.distanceKm.toFixed(1)} km · ${row.co2Kg.toFixed(1)} kg CO₂`;
            },
        };
        dailyChart = new Chart($('#dailyChart'), {
            type: 'bar',
            data: { labels, datasets: [{ label: 'Bins collected', data, backgroundColor: c.brand, borderRadius: 4, borderSkipped: 'bottom', maxBarThickness: 36 }] },
            options: opts,
        });
    } else {
        dailyChart.data.labels = labels;
        dailyChart.data.datasets[0].data = data;
        dailyChart.update('none');
    }
    $('#dailyTable').innerHTML = `<table><thead><tr><th>Day</th><th>Routes</th><th>Bins</th><th>km</th><th>kg CO₂</th></tr></thead><tbody>
        ${d.map((x, i) => `<tr><td>${labels[i]}</td><td>${x.routes}</td><td>${x.binsCollected}</td><td>${x.distanceKm.toFixed(1)}</td><td>${x.co2Kg.toFixed(1)}</td></tr>`).join('')}</tbody></table>`;
}

// ---------------------------------------------------------------- alerts view
function renderAlerts() {
    const icon = { OVERFLOW_RISK: 'fa-fire', URGENT: 'fa-triangle-exclamation', ROUTE_COMPLETED: 'fa-flag-checkered', ROUTE_DISPATCHED: 'fa-truck-fast', UNASSIGNED_BINS: 'fa-weight-hanging' };
    const list = state.alertFilter === 'open' ? state.alerts.filter((a) => !a.acknowledged) : state.alerts;
    $$('#alertFilter button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.filter === state.alertFilter)));
    $('#alertsEmpty').hidden = list.length > 0;
    $('#ackAllBtn').disabled = !state.alerts.some((a) => !a.acknowledged);
    $('#alertList').innerHTML = list.map((a) => `
        <li class="${a.acknowledged ? 'acked' : ''}">
            <span class="ic sev-${a.severity}"><i class="fa-solid ${icon[a.type] || 'fa-bell'}"></i></span>
            <div><div class="msg">${esc(a.message)}</div>
                <div class="meta">${a.severity.toLowerCase()} · ${fmt.dayTime(a.createdAt)}${a.binId ? ` · <a href="#" data-open-bin="${a.binId}">view bin</a>` : ''}</div></div>
            ${a.acknowledged ? '<span class="muted small">Done</span>' : `<button class="btn ghost sm" data-ack="${a.id}"><i class="fa-solid fa-check"></i> Acknowledge</button>`}
        </li>`).join('');
}
$('#alertFilter').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-filter]');
    if (!b) return;
    state.alertFilter = b.dataset.filter;
    renderAlerts();
});
$('#alertList').addEventListener('click', async (e) => {
    const open = e.target.closest('[data-open-bin]');
    if (open) { e.preventDefault(); return openBin(Number(open.dataset.openBin)); }
    const b = e.target.closest('[data-ack]');
    if (!b) return;
    await busy(b, () => api.ackAlert(Number(b.dataset.ack)));
    refresh(true);
});
$('#ackAllBtn').addEventListener('click', async (e) => {
    await busy(e.currentTarget, () => api.ackAll());
    toast('All alerts acknowledged');
    refresh(true);
});

// =====================================================================
// Bin drawer
// =====================================================================
async function openBin(id) {
    state.drawerBinId = id;
    const d = $('#binDrawer');
    d.classList.add('open');
    d.setAttribute('aria-hidden', 'false');
    await refreshDrawer();
    $('[data-close-drawer]', d).focus();
}

function closeDrawer() {
    state.drawerBinId = null;
    const d = $('#binDrawer');
    d.classList.remove('open');
    d.setAttribute('aria-hidden', 'true');
}
$('[data-close-drawer]').addEventListener('click', closeDrawer);

async function refreshDrawer() {
    const id = state.drawerBinId;
    if (!id) return;
    let bin, fc;
    try {
        [bin, fc] = await Promise.all([api.bin(id), api.forecast(id)]);
    } catch (e) {
        if (e.status === 404) closeDrawer();
        return;
    }
    if (state.drawerBinId !== id) return;
    const w = WASTE[bin.wasteType] || WASTE.GENERAL;
    $('#drawerZone').innerHTML = `${esc(bin.zone || '')} · <i class="fa-solid ${w.icon}"></i> ${w.label}`;
    $('#drawerTitle').textContent = `${bin.binCode} · ${bin.locationName}`;
    $('#drawerStatus').innerHTML = statusPill(bin.status) + (bin.onActiveRoute ? ' <span class="on-route"><i class="fa-solid fa-truck"></i> on an active route</span>' : '');
    $('#drawerStats').innerHTML = `
        <div><dt>Fill now</dt><dd>${fmt.pct(bin.currentFillPercentage, 1)} <small>${fmt.litres(bin.capacity * bin.currentFillPercentage / 100)}</small></dd></div>
        <div><dt>Full in</dt><dd>${bin.estimatedHoursToFull != null ? fmt.hours(bin.estimatedHoursToFull) : (bin.status === 'OVERFLOW_RISK' ? 'now' : 'no rising trend')}</dd></div>
        <div><dt>Fill rate</dt><dd>${bin.fillRatePerHour ? bin.fillRatePerHour.toFixed(2) : '0'} <small>% / hour</small></dd></div>
        <div><dt>Forecast confidence</dt><dd>${Math.round((bin.confidence || 0) * 100)}<small>% (R²)</small></dd></div>
        <div><dt>Capacity</dt><dd>${fmt.litres(bin.capacity)}</dd></div>
        <div><dt>Last emptied</dt><dd><small>${fmt.ago(bin.lastCollectedAt, state.sim?.simTime)}</small></dd></div>`;
    renderForecastChart(fc);
}

function renderForecastChart(fc) {
    const c = chartColors();
    const now = new Date(fc.forecast[0].timestamp).getTime();
    const toX = (p) => ({ x: (new Date(p.timestamp).getTime() - now) / 3600000, y: p.fillPercentage });
    const history = fc.history.map(toX);
    const forecast = fc.forecast.map(toX);
    // Snap the left edge to a 12 h boundary so ticks read -72h, -60h, ... now, +12h, +24h.
    const minX = Math.max(-72, Math.min(-24, Math.ceil((history.length ? history[0].x : -24) / 12) * 12));
    const datasets = [
        { label: 'Measured', data: history, borderColor: c.brand, backgroundColor: c.brand + '22', fill: true, borderWidth: 2, pointRadius: 0, pointHoverRadius: 4, tension: 0.15 },
        { label: 'Forecast', data: forecast, borderColor: c.brand, borderDash: [5, 5], borderWidth: 2, pointRadius: 0, pointHoverRadius: 4, fill: false },
        { label: 'Urgent line', data: [{ x: minX, y: 90 }, { x: 24, y: 90 }], borderColor: c.urgent, borderDash: [3, 4], borderWidth: 1, pointRadius: 0, pointHoverRadius: 0, fill: false },
    ];
    if (forecastChart && forecastChart._theme === isDark()) {
        forecastChart.data.datasets.forEach((ds, i) => { ds.data = datasets[i].data; });
        forecastChart.options.scales.x.min = minX;
        forecastChart.update('none');
        return;
    }
    if (forecastChart) forecastChart.destroy();
    const opts = baseChartOptions(c);
    opts.parsing = false;
    opts.interaction = { mode: 'nearest', axis: 'x', intersect: false };
    opts.scales.x = {
        type: 'linear', min: minX, max: 24, grid: { color: c.grid }, border: { color: c.grid },
        ticks: { color: c.muted, stepSize: 12, font: { family: c.font, size: 11 }, callback: (v) => (v === 0 ? 'now' : `${v > 0 ? '+' : ''}${v}h`) },
    };
    opts.scales.y.max = 100;
    opts.scales.y.ticks.callback = (v) => `${v}%`;
    opts.plugins.tooltip.filter = (item) => item.datasetIndex < 2;
    opts.plugins.tooltip.callbacks = {
        title: (items) => {
            const h = items[0].parsed.x;
            return new Date(now + h * 3600000).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' });
        },
        label: (item) => ` ${item.dataset.label}: ${item.parsed.y.toFixed(1)}%`,
    };
    forecastChart = new Chart($('#forecastChart'), { type: 'line', data: { datasets }, options: opts });
    forecastChart._theme = isDark();
}

$('#drawerEdit').addEventListener('click', () => openBinForm(state.bins.find((b) => b.id === state.drawerBinId)));
$('#drawerDelete').addEventListener('click', () => deleteBin(state.drawerBinId));
$('#drawerCollect').addEventListener('click', (e) => collectBin(state.drawerBinId, e.currentTarget));

// =====================================================================
// Bin form
// =====================================================================
const form = $('#binForm');
function openBinForm(bin) {
    form.reset();
    clearFormErrors();
    $('#binModalTitle').textContent = bin ? `Edit ${bin.binCode}` : 'Add bin';
    form.elements.binId.value = bin?.id ?? '';
    if (bin) {
        ['binCode', 'locationName', 'zone', 'wasteType', 'latitude', 'longitude', 'capacity'].forEach((k) => { form[k].value = bin[k] ?? ''; });
        form.currentFillPercentage.value = Math.round(bin.currentFillPercentage);
    } else {
        const next = 1 + Math.max(0, ...state.bins.map((b) => Number(String(b.binCode).replace(/\D/g, '')) || 0));
        form.binCode.value = `B${String(next).padStart(3, '0')}`;
        form.currentFillPercentage.value = 0;
    }
    $('#fillOut').textContent = `${form.currentFillPercentage.value}%`;
    $('#binModal').hidden = false;
    if (!picker) {
        picker = DistrictMap.picker($('#pickMap'), (lat, lon) => {
            form.latitude.value = lat.toFixed(6);
            form.longitude.value = lon.toFixed(6);
        });
    }
    setTimeout(() => {
        picker.map.invalidateSize();
        if (bin) picker.set(bin.latitude, bin.longitude, true);
        else { picker.clear(); picker.map.setView([state.sim?.depot?.lat ?? 17.445, state.sim?.depot?.lon ?? 78.36], 13); }
    }, 60);
    form.binCode.focus();
}
function closeBinForm() { $('#binModal').hidden = true; }
$$('#binModal [data-close-modal]').forEach((b) => b.addEventListener('click', closeBinForm));
$('#binModal').addEventListener('click', (e) => { if (e.target.id === 'binModal') closeBinForm(); });
form.currentFillPercentage.addEventListener('input', (e) => { $('#fillOut').textContent = `${e.target.value}%`; });
['latitude', 'longitude'].forEach((k) => form[k].addEventListener('change', () => {
    const lat = parseFloat(form.latitude.value), lon = parseFloat(form.longitude.value);
    if (picker && !Number.isNaN(lat) && !Number.isNaN(lon)) picker.set(lat, lon, true);
}));

function clearFormErrors() {
    $$('.field', form).forEach((f) => { f.classList.remove('invalid'); $('.err', f).textContent = ''; });
    $('#formError').hidden = true;
}
function fieldError(name, msg) {
    const f = form[name]?.closest('.field');
    if (!f) return false;
    f.classList.add('invalid');
    $('.err', f).textContent = msg;
    return true;
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearFormErrors();
    let ok = true;
    for (const el of form.elements) {
        if (el.name && el.willValidate && !el.checkValidity()) {
            ok = false;
            fieldError(el.name, el.name === 'latitude' || el.name === 'longitude' ? 'Pick a point on the map or enter a value' : el.validationMessage);
        }
    }
    if (!ok) return;
    const body = {
        binCode: form.binCode.value.trim(),
        locationName: form.locationName.value.trim(),
        zone: form.zone.value.trim(),
        wasteType: form.wasteType.value,
        latitude: parseFloat(form.latitude.value),
        longitude: parseFloat(form.longitude.value),
        capacity: parseFloat(form.capacity.value),
        currentFillPercentage: parseFloat(form.currentFillPercentage.value),
    };
    const id = form.elements.binId.value;
    const btn = $('#saveBinBtn');
    btn.disabled = true;
    try {
        const saved = id ? await api.updateBin(id, body) : await api.createBin(body);
        closeBinForm();
        toast(id ? 'Bin updated' : 'Bin added', `${saved.binCode} · ${STATUS[saved.status].label}`);
        await refresh(true);
        if (!id) openBin(saved.id);
    } catch (err) {
        const fe = err.body?.fieldErrors;
        let shown = false;
        if (fe) Object.entries(fe).forEach(([k, v]) => { shown = fieldError(k, v) || shown; });
        if (err.status === 409) shown = fieldError('binCode', err.message);
        if (!shown) { $('#formError').textContent = err.message; $('#formError').hidden = false; }
    } finally {
        btn.disabled = false;
    }
});

$('#addBinBtn').addEventListener('click', () => openBinForm(null));

$('#csvInput').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    try {
        const res = await busy(null, () => api.importCsv(file));
        toast('Readings imported', `${res.imported} sensor readings added from ${file.name}.`);
        refresh(true);
    } catch { /* toast already shown */ }
});

// =====================================================================
// Global search
// =====================================================================
const search = $('#globalSearch');
const results = $('#searchResults');
function runSearch() {
    const q = search.value.trim().toLowerCase();
    if (!q) { results.hidden = true; return; }
    const hits = state.bins.filter((b) => `${b.binCode} ${b.locationName} ${b.zone}`.toLowerCase().includes(q)).slice(0, 8);
    results.innerHTML = hits.length ? hits.map((b, i) => `<button data-bin="${b.id}" class="${i === 0 ? 'hl' : ''}">
        <span class="dot-c c-${b.status}"></span>
        <span class="sr-name">${esc(b.binCode)} · ${esc(b.locationName)}<small>${esc(b.zone || '')} · ${fmt.pct(b.currentFillPercentage)} · ${STATUS[b.status].label}</small></span></button>`).join('')
        : '<div class="muted small" style="padding:8px 10px">No matching bins</div>';
    results.hidden = false;
}
function pickSearch(id) {
    results.hidden = true;
    search.value = '';
    const b = state.bins.find((x) => x.id === id);
    if (state.view === 'map' && b) mainMap.focus(b.latitude, b.longitude);
    openBin(id);
}
search.addEventListener('input', runSearch);
search.addEventListener('focus', runSearch);
search.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        const first = $('button[data-bin]', results);
        if (first) pickSearch(Number(first.dataset.bin));
    } else if (e.key === 'Escape') {
        results.hidden = true;
        search.blur();
    }
});
results.addEventListener('click', (e) => {
    const b = e.target.closest('button[data-bin]');
    if (b) pickSearch(Number(b.dataset.bin));
});
document.addEventListener('click', (e) => { if (!e.target.closest('.search')) results.hidden = true; });

// Clicks on "next to overflow" rows
$('#nextOverflowList').addEventListener('click', (e) => {
    const li = e.target.closest('li[data-bin]');
    if (li) openBin(Number(li.dataset.bin));
});

// =====================================================================
// Simulation controls
// =====================================================================
async function updateSim(patch) {
    state.sim = await busy(null, () => api.updateSimulation(patch));
    renderChrome();
    setLive(state.sim.paused ? 'paused' : 'on');
}
$('#simSpeed').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-speed]');
    if (b) updateSim({ speed: Number(b.dataset.speed) });
});
$('#simPauseBtn').addEventListener('click', () => updateSim({ paused: !state.sim?.paused }));
$('#autopilotToggle').addEventListener('change', (e) => {
    updateSim({ autopilot: e.target.checked });
    toast(e.target.checked ? 'Autopilot on' : 'Autopilot off',
        e.target.checked ? 'Routes are planned and dispatched automatically when bins need pickup.' : 'Routes are only planned when you ask.');
});
$('#resetDemoBtn').addEventListener('click', async (e) => {
    const ok = await confirmDialog('Reset demo data?', 'All bins, routes, alerts and readings are replaced with the original demo district.', 'Reset');
    if (!ok) return;
    await busy(e.currentTarget, () => api.resetDemo());
    closeDrawer();
    toast('Demo data restored', '24 bins with 7 days of sensor history.');
    refresh(true);
});

// =====================================================================
// Theme, sidebar, keyboard
// =====================================================================
function applyThemeIcon() {
    $('#themeBtn').innerHTML = `<i class="fa-solid ${isDark() ? 'fa-sun' : 'fa-moon'}"></i>`;
}
$('#themeBtn').addEventListener('click', () => {
    const next = isDark() ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    try { localStorage.setItem('ecoroute-theme', next); } catch { /* private mode */ }
    onThemeChange();
});
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', onThemeChange);
function onThemeChange() {
    applyThemeIcon();
    overviewMap?.applyTheme();
    mainMap?.applyTheme();
    if (dailyChart) { dailyChart.destroy(); dailyChart = null; }
    if (forecastChart) { forecastChart.destroy(); forecastChart = null; }
    renderAll();
    if (state.drawerBinId) refreshDrawer();
}

function closeSidebar() {
    $('#sidebar').classList.remove('open');
    $('#scrim').hidden = true;
}
$('#menuBtn').addEventListener('click', () => {
    $('#sidebar').classList.add('open');
    $('#scrim').hidden = false;
});
$('#scrim').addEventListener('click', closeSidebar);

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        if (!$('#binModal').hidden) closeBinForm();
        else if (state.drawerBinId) closeDrawer();
    }
    if (e.key === '/' && document.activeElement.tagName !== 'INPUT') {
        e.preventDefault();
        search.focus();
    }
});

// =====================================================================
// Boot
// =====================================================================
buildBinFilters();
applyThemeIcon();
connectStream();
showView(location.hash.slice(1) || 'overview');
