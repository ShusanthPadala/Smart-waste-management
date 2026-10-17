// Formatting, status metadata, toasts and the confirm dialog.

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
/** Escape untrusted text before it goes into innerHTML. */
export const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ESC[c]);

export const STATUS = {
    NORMAL: { label: 'Normal', icon: 'fa-circle-check', rank: 0 },
    SOON: { label: 'Soon', icon: 'fa-clock', rank: 1 },
    URGENT: { label: 'Urgent', icon: 'fa-triangle-exclamation', rank: 2 },
    OVERFLOW_RISK: { label: 'Overflow risk', icon: 'fa-circle-exclamation', rank: 3 },
};
export const STATUS_ORDER = ['OVERFLOW_RISK', 'URGENT', 'SOON', 'NORMAL'];

export const ROUTE_STATUS = {
    PLANNED: { label: 'Planned', icon: 'fa-pen-ruler' },
    IN_PROGRESS: { label: 'On the road', icon: 'fa-truck-fast' },
    COMPLETED: { label: 'Completed', icon: 'fa-flag-checkered' },
    CANCELLED: { label: 'Cancelled', icon: 'fa-ban' },
};
export const VEHICLE_STATUS = {
    AVAILABLE: { label: 'Available', icon: 'fa-circle-check' },
    ON_ROUTE: { label: 'On route', icon: 'fa-truck-fast' },
    MAINTENANCE: { label: 'Maintenance', icon: 'fa-wrench' },
};
export const WASTE = {
    GENERAL: { label: 'General', icon: 'fa-trash-can' },
    RECYCLABLE: { label: 'Recyclable', icon: 'fa-recycle' },
    ORGANIC: { label: 'Organic', icon: 'fa-apple-whole' },
};

export function statusPill(status) {
    const m = STATUS[status] || STATUS.NORMAL;
    return `<span class="pill ${esc(status)}"><i class="fa-solid ${m.icon}"></i>${m.label}</span>`;
}
export function routePill(status) {
    const m = ROUTE_STATUS[status] || { label: status, icon: 'fa-circle' };
    return `<span class="pill ${esc(status)}"><i class="fa-solid ${m.icon}"></i>${m.label}</span>`;
}
export function vehiclePill(status) {
    const m = VEHICLE_STATUS[status] || { label: status, icon: 'fa-circle' };
    return `<span class="pill ${esc(status)}"><i class="fa-solid ${m.icon}"></i>${m.label}</span>`;
}

/** Vehicle colour slot by fleet position (fixed order, never cycled past 3). */
export const vehicleVar = (index) => `var(--v${Math.min(index, 2) + 1})`;

export const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

export const fmt = {
    pct: (v, d = 0) => (v == null || Number.isNaN(v) ? '–' : `${Number(v).toFixed(d)}%`),
    num: (v, d = 0) => (v == null ? '–' : Number(v).toLocaleString(undefined, { minimumFractionDigits: d, maximumFractionDigits: d })),
    km: (v) => (v == null ? '–' : `${Number(v).toFixed(v < 10 ? 1 : 0)} km`),
    kg: (v) => (v == null ? '–' : `${Number(v).toFixed(v < 10 ? 1 : 0)} kg`),
    litres: (v) => (v == null ? '–' : `${Math.round(v).toLocaleString()} L`),
    minutes: (m) => {
        if (m == null) return '–';
        const h = Math.floor(m / 60), mm = Math.round(m % 60);
        return h ? `${h} h ${mm} min` : `${mm} min`;
    },
    hours: (h) => {
        if (h == null) return '–';
        if (h <= 0) return 'now';
        if (h < 1) return `${Math.max(1, Math.round(h * 60))} min`;
        if (h < 48) return `${h.toFixed(h < 10 ? 1 : 0)} h`;
        return `${Math.round(h / 24)} days`;
    },
    clock: (iso) => (iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '–'),
    dayTime: (iso) => (iso ? new Date(iso).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' }) : '–'),
    /** "3 h ago" relative to the simulated now. */
    ago: (iso, nowIso) => {
        if (!iso) return 'never';
        const diff = (new Date(nowIso || Date.now()) - new Date(iso)) / 60000;
        if (diff < 1) return 'just now';
        if (diff < 60) return `${Math.round(diff)} min ago`;
        if (diff < 60 * 36) return `${Math.round(diff / 60)} h ago`;
        return `${Math.round(diff / 1440)} days ago`;
    },
};

/** Colour for a fill level, following status thresholds. */
export function fillStatus(bin) {
    return bin.status || 'NORMAL';
}

// ---------------------------------------------------------------- toasts
const toastBox = () => document.getElementById('toasts');
export function toast(title, message = '', kind = 'ok', ttl = 4500) {
    const icon = { ok: 'fa-circle-check', err: 'fa-circle-exclamation', INFO: 'fa-circle-info', WARNING: 'fa-triangle-exclamation', CRITICAL: 'fa-fire' }[kind] || 'fa-circle-info';
    const cls = ['ok', 'err'].includes(kind) ? kind : `sev-${kind}`;
    const el = document.createElement('div');
    el.className = `toast ${cls}`;
    el.innerHTML = `<span class="ic"><i class="fa-solid ${icon}"></i></span>
        <div><b>${esc(title)}</b>${message ? `<span class="muted">${esc(message)}</span>` : ''}</div>
        <button class="icon-btn sm bare" aria-label="Dismiss"><i class="fa-solid fa-xmark"></i></button>`;
    const close = () => {
        el.classList.add('leaving');
        setTimeout(() => el.remove(), 200);
    };
    el.querySelector('button').addEventListener('click', close);
    toastBox().appendChild(el);
    while (toastBox().children.length > 4) toastBox().firstChild.remove();
    setTimeout(close, ttl);
}

// ---------------------------------------------------------------- confirm
export function confirmDialog(title, text, okLabel = 'Confirm') {
    const modal = document.getElementById('confirmModal');
    document.getElementById('confirmTitle').textContent = title;
    document.getElementById('confirmText').textContent = text;
    const ok = document.getElementById('confirmOk');
    const cancel = document.getElementById('confirmCancel');
    ok.textContent = okLabel;
    modal.hidden = false;
    ok.focus();
    return new Promise((resolve) => {
        const done = (v) => {
            modal.hidden = true;
            ok.removeEventListener('click', yes);
            cancel.removeEventListener('click', no);
            modal.removeEventListener('click', backdrop);
            document.removeEventListener('keydown', key);
            resolve(v);
        };
        const yes = () => done(true);
        const no = () => done(false);
        const backdrop = (e) => { if (e.target === modal) done(false); };
        const key = (e) => { if (e.key === 'Escape') done(false); };
        ok.addEventListener('click', yes);
        cancel.addEventListener('click', no);
        modal.addEventListener('click', backdrop);
        document.addEventListener('keydown', key);
    });
}

/** Runs an async action with a spinner on the button and a toast on failure. */
export async function busy(button, action) {
    const original = button ? button.innerHTML : null;
    if (button) {
        button.disabled = true;
        button.innerHTML = '<i class="fa-solid fa-spinner"></i>';
    }
    try {
        return await action();
    } catch (e) {
        toast('Something went wrong', e.message, 'err', 6000);
        throw e;
    } finally {
        if (button) {
            button.disabled = false;
            button.innerHTML = original;
        }
    }
}
