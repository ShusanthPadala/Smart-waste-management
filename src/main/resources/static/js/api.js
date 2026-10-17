// Thin fetch wrapper: JSON in/out, server error messages surfaced as Error.message.

export class ApiError extends Error {
    constructor(message, status, body) {
        super(message);
        this.status = status;
        this.body = body;
    }
}

async function request(method, url, body) {
    const opts = { method, headers: {} };
    if (body instanceof FormData) {
        opts.body = body;
    } else if (body !== undefined) {
        opts.headers['Content-Type'] = 'application/json';
        opts.body = JSON.stringify(body);
    }
    let res;
    try {
        res = await fetch(url, opts);
    } catch (e) {
        throw new ApiError('Cannot reach the server. Is it running?', 0);
    }
    const text = await res.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch { data = text; }
    }
    if (!res.ok) {
        const msg = (data && data.message) || `Request failed (${res.status})`;
        throw new ApiError(msg, res.status, data);
    }
    return data;
}

export const api = {
    summary: () => request('GET', '/api/dashboard/summary'),
    bins: () => request('GET', '/api/bins'),
    bin: (id) => request('GET', `/api/bins/${id}`),
    forecast: (id) => request('GET', `/api/bins/${id}/forecast`),
    createBin: (b) => request('POST', '/api/bins', b),
    updateBin: (id, b) => request('PUT', `/api/bins/${id}`, b),
    deleteBin: (id) => request('DELETE', `/api/bins/${id}`),
    collectBin: (id) => request('POST', `/api/bins/${id}/collect`),

    activeRoutes: () => request('GET', '/api/routes/active'),
    recentRoutes: () => request('GET', '/api/routes'),
    planRoutes: (includeSoon) => request('POST', `/api/routes/plan?includeSoon=${includeSoon ? 'true' : 'false'}`),
    dispatchRoute: (id) => request('POST', `/api/routes/${id}/dispatch`),
    completeRoute: (id) => request('POST', `/api/routes/${id}/complete`),
    cancelRoute: (id) => request('POST', `/api/routes/${id}/cancel`),

    fleet: () => request('GET', '/api/fleet'),
    alerts: (open) => request('GET', `/api/alerts?open=${open ? 'true' : 'false'}`),
    ackAlert: (id) => request('POST', `/api/alerts/${id}/ack`),
    ackAll: () => request('POST', '/api/alerts/ack-all'),

    comparison: () => request('GET', '/api/metrics/comparison'),
    daily: (days = 7) => request('GET', `/api/metrics/daily?days=${days}`),
    zones: () => request('GET', '/api/metrics/zones'),

    settings: () => request('GET', '/api/settings'),
    updateDistrict: (d) => request('PUT', '/api/settings/district', d),
    loadDemoCity: (city) => request('POST', '/api/settings/demo-city', { city }),
    clearDistrict: () => request('POST', '/api/settings/clear'),

    simulation: () => request('GET', '/api/simulation'),
    updateSimulation: (patch) => request('POST', '/api/simulation', patch),
    resetDemo: () => request('POST', '/api/simulation/reset'),
    importCsv: (file) => {
        const fd = new FormData();
        fd.append('file', file);
        return request('POST', '/api/simulation/import', fd);
    },
};
