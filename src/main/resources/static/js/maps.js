// Leaflet map of the district: bins, depot, planned/active routes and moving trucks.
import { esc, fmt, STATUS, cssVar } from './ui.js';

// Standard OSM tiles; dark mode is a CSS filter on the tile pane (see .map-dark in app.css).
const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
/** Visakhapatnam; only used until the server tells us where the depot is. */
const DEFAULT_CENTER = [17.7345, 83.2985];
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

export function isDark() {
    const t = document.documentElement.dataset.theme;
    if (t) return t === 'dark';
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export class DistrictMap {
    constructor(el, { onBinClick, onMapClick, zoomControl = true, scrollWheelZoom = true } = {}) {
        this.el = el;
        this.onBinClick = onBinClick;
        this.map = L.map(el, { zoomControl, scrollWheelZoom, attributionControl: true }).setView(DEFAULT_CENTER, 13);
        this.tiles = null;
        this.binMarkers = new Map();
        this.truckMarkers = new Map();
        this.routeLayer = L.layerGroup().addTo(this.map);
        this.depotMarker = null;
        this.fitted = false;
        this.applyTheme();
        if (onMapClick) this.map.on('click', (e) => onMapClick(e.latlng.lat, e.latlng.lng));
    }

    applyTheme() {
        if (!this.tiles) this.tiles = L.tileLayer(TILE_URL, { attribution: ATTRIBUTION, maxZoom: 19 }).addTo(this.map);
        this.el.classList.toggle('map-dark', isDark());
    }

    invalidate() {
        setTimeout(() => this.map.invalidateSize(), 50);
    }

    focus(lat, lon) {
        this.map.flyTo([lat, lon], 16, { duration: 0.6 });
    }

    /**
     * @param bins     BinView[]
     * @param routes   active CollectionRouteDTO[]
     * @param fleet    VehicleView[]
     * @param depot    {lat, lon}
     * @param statuses Set of statuses to show (null = all)
     * @param showRoutes whether to draw routes and trucks
     */
    update({ bins = [], routes = [], fleet = [], depot, statuses = null, showRoutes = true }) {
        this.drawBins(bins, statuses, routes);
        this.drawDepot(depot);
        this.drawRoutes(showRoutes ? routes : [], fleet, depot);
        this.drawTrucks(showRoutes ? fleet : [], fleet);
        if (!this.fitted && (bins.length || depot)) {
            const pts = bins.map((b) => [b.latitude, b.longitude]);
            if (depot) pts.push([depot.lat, depot.lon]);
            if (pts.length > 1) this.map.fitBounds(L.latLngBounds(pts).pad(0.08));
            else this.map.setView(pts[0], 14);
            this.fitted = true;
        }
    }

    /** Re-frame the map on the next update, e.g. after switching to another city. */
    refit() {
        this.fitted = false;
    }

    drawBins(bins, statuses, routes) {
        const seen = new Set();
        const pending = new Set();
        routes.forEach((r) => r.stops.forEach((s) => { if (!s.collected && s.binId) pending.add(s.binId); }));

        for (const b of bins) {
            if (statuses && !statuses.has(b.status)) continue;
            seen.add(b.id);
            const size = Math.round(12 + (Math.min(100, b.currentFillPercentage) / 100) * 12);
            const key = `${b.status}|${size}|${pending.has(b.id)}`;
            let m = this.binMarkers.get(b.id);
            const icon = () => L.divIcon({
                className: 'pin-wrap',
                html: `<div class="pin c-${b.status} ${b.status}" style="--size:${size}px"></div>`,
                iconSize: [size + 10, size + 10],
            });
            if (!m) {
                m = L.marker([b.latitude, b.longitude], { icon: icon(), riseOnHover: true, keyboard: true, title: b.binCode });
                m.on('click', () => this.onBinClick && this.onBinClick(m._binId));
                m.addTo(this.map);
                this.binMarkers.set(b.id, m);
            } else {
                m.setLatLng([b.latitude, b.longitude]);
                if (m._key !== key) m.setIcon(icon());
            }
            m._binId = b.id;
            m._key = key;
            m.setZIndexOffset(STATUS[b.status].rank * 100);
            m.unbindTooltip().bindTooltip(
                `<b>${esc(b.binCode)}</b> · ${esc(b.locationName)}<br>${fmt.pct(b.currentFillPercentage)} full · ${STATUS[b.status].label}` +
                (b.estimatedHoursToFull != null ? ` · full in ${fmt.hours(b.estimatedHoursToFull)}` : '') +
                (pending.has(b.id) ? '<br><i class="fa-solid fa-truck"></i> scheduled for pickup' : ''),
                { direction: 'top', offset: [0, -size / 2 - 4] });
        }
        for (const [id, m] of this.binMarkers) {
            if (!seen.has(id)) {
                m.remove();
                this.binMarkers.delete(id);
            }
        }
    }

    drawDepot(depot) {
        if (!depot) return;
        if (this.depotMarker) {
            this.depotMarker.setLatLng([depot.lat, depot.lon]);
        } else {
            this.depotMarker = L.marker([depot.lat, depot.lon], {
                icon: L.divIcon({ className: 'pin-wrap', html: '<div class="depot"><i class="fa-solid fa-warehouse"></i></div>', iconSize: [30, 30] }),
                zIndexOffset: 1000,
                title: 'Depot',
            }).bindTooltip('<b>Depot</b><br>All routes start and end here', { direction: 'top', offset: [0, -16] }).addTo(this.map);
        }
    }

    drawRoutes(routes, fleet, depot) {
        this.routeLayer.clearLayers();
        if (!depot) return;
        for (const r of routes) {
            const color = cssVar(`--v${vehicleSlot(fleet, r.vehicleId)}`);
            const pts = [[depot.lat, depot.lon], ...r.stops.map((s) => [s.latitude, s.longitude]), [depot.lat, depot.lon]];
            const active = r.status === 'IN_PROGRESS';
            L.polyline(pts, { color: cssVar('--surface'), weight: active ? 8 : 6, opacity: 0.9, interactive: false }).addTo(this.routeLayer);
            L.polyline(pts, {
                color, weight: active ? 4 : 3, opacity: 0.95, dashArray: active ? null : '6 7', lineJoin: 'round',
            }).bindTooltip(`<b>${esc(r.vehicleNumber || 'Route')}</b> · route #${r.id}<br>${r.stops.length} stops · ${fmt.km(r.totalDistanceKm)}`, { sticky: true })
                .addTo(this.routeLayer);
            r.stops.forEach((s, i) => {
                L.marker([s.latitude, s.longitude], {
                    icon: L.divIcon({
                        className: 'pin-wrap',
                        html: `<div class="stop-num ${s.collected ? 'done' : ''}" style="--c:${color}">${i + 1}</div>`,
                        iconSize: [18, 18], iconAnchor: [-4, 20],
                    }),
                    interactive: false,
                    zIndexOffset: 900,
                }).addTo(this.routeLayer);
            });
        }
    }

    drawTrucks(visibleFleet, fleet) {
        const seen = new Set();
        for (const v of visibleFleet) {
            if (v.status !== 'ON_ROUTE' || v.latitude == null) continue;
            seen.add(v.id);
            let m = this.truckMarkers.get(v.id);
            if (!m) {
                m = L.marker([v.latitude, v.longitude], {
                    icon: L.divIcon({
                        className: 'pin-wrap truck-marker',
                        html: `<div class="truck" style="--c:var(--v${vehicleSlot(fleet, v.id)})"><i class="fa-solid fa-truck"></i>${esc(v.vehicleNumber)}</div>`,
                        iconSize: [84, 26],
                    }),
                    zIndexOffset: 2000,
                    title: v.vehicleNumber,
                }).addTo(this.map);
                this.truckMarkers.set(v.id, m);
            } else {
                m.setLatLng([v.latitude, v.longitude]);
            }
        }
        for (const [id, m] of this.truckMarkers) {
            if (!seen.has(id)) {
                m.remove();
                this.truckMarkers.delete(id);
            }
        }
    }

    /** Small picker map for the bin form. */
    static picker(el, onPick) {
        const map = L.map(el, { zoomControl: true, attributionControl: false }).setView(DEFAULT_CENTER, 13);
        L.tileLayer(TILE_URL, { maxZoom: 19 }).addTo(map);
        el.classList.toggle('map-dark', isDark());
        let marker = null;
        const set = (lat, lon, pan = false) => {
            if (!marker) {
                marker = L.marker([lat, lon], {
                    icon: L.divIcon({ className: 'pin-wrap', html: '<div class="pin c-NORMAL" style="--size:18px;--c:var(--brand)"></div>', iconSize: [28, 28] }),
                }).addTo(map);
            } else {
                marker.setLatLng([lat, lon]);
            }
            if (pan) map.setView([lat, lon], 15);
        };
        map.on('click', (e) => {
            set(e.latlng.lat, e.latlng.lng);
            onPick(e.latlng.lat, e.latlng.lng);
        });
        return {
            map,
            set,
            clear: () => { if (marker) { marker.remove(); marker = null; } },
        };
    }
}

/** 1-based colour slot for a vehicle by its position in the fleet list. */
export function vehicleSlot(fleet, vehicleId) {
    const idx = fleet.findIndex((v) => v.id === vehicleId);
    return Math.min(Math.max(idx, 0), 2) + 1;
}
