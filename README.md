# EcoRoute: Smart & Sustainable Waste Collection

Fixed collection schedules send trucks to half-empty bins while busy sites overflow. EcoRoute watches every bin's fill sensor, forecasts when each one will be full, and plans fleet routes through only the bins that need emptying. It then shows the distance, diesel and CO₂ saved compared with a fixed schedule.

The demo runs a simulated district of **24 bins in west Hyderabad** (Gachibowli, HITEC City, Kondapur and around) with **3 trucks** on an accelerated clock, so a day of operations plays out in minutes.

## What's inside

| Area | Highlights |
|---|---|
| **Live operations dashboard** | Overview KPIs, district map, "next to overflow" list, fill by zone, activity feed. Updates push in over Server-Sent Events, so there's no polling. |
| **Live map** | Bins sized by fill and coloured by status, planned routes (dashed), active routes (solid), trucks driving their tours in real time, and status filters. |
| **Bins** | Searchable, sortable and filterable table; detail drawer with 72 h history and a 24 h forecast chart; add/edit with a map location picker; mark emptied; CSV import. |
| **Routes & fleet** | Multi-truck, capacity-aware planning; dispatch / complete / cancel; load vs capacity; 2-opt savings per route; fleet status and route history. |
| **Sustainability** | Fixed schedule vs data-driven for distance, diesel and CO₂; average fill at pickup; bins collected per day; lifetime totals; methodology. |
| **Alerts** | Raised automatically when a bin turns urgent or reaches overflow risk, and on route events. Acknowledge one or all. Open alerts resolve automatically when a bin is emptied. |
| **Simulation controls** | Pause or resume, run at 1× / 60× / 360× / 1800×, **Autopilot** (plans and dispatches on its own), reset demo data. |
| **Polish** | Light & dark themes, responsive down to phone width, keyboard shortcuts (`/` to search, `Esc` to close). |

## How it works

1. **Sensors (simulated).** Each bin has a site profile: residential, market, commercial, park or transit. Its fill rate varies by hour of day and weekday. A reading is stored every simulated hour, with 7 days of history on start-up.
2. **Forecast.** A least-squares trend line over the last 10 readings, restarted after a collection, gives the fill rate and the time until 100%. R² (penalised for fewer than 5 points) is the confidence score.
3. **Priority.**
   * **Overflow risk:** full, or past 100% on trend.
   * **Urgent:** ≥ 90%, or full within 8 h.
   * **Soon:** full within 24 h.
4. **Routing** (`RoutingEngine`, pure and unit tested):
   1. Keep the most critical bins that fit in the free fleet's combined payload.
   2. Split them between trucks with a **capacity-aware sweep** around the depot.
   3. Order each tour with **nearest-neighbour**, then improve it with **2-opt**.
   4. Measure distances with **Haversine**.
5. **Execution.** A dispatched truck advances along its legs at 25 km/h, with 3 min of service per stop. Each bin is emptied as the truck reaches it, and the route closes when the truck is back at the depot.
6. **Sustainability.** Fuel = km ÷ truck km/L. CO₂ = fuel × 2.68 kg/L (diesel). Trees equivalent = annual CO₂ ÷ 21 kg per tree per year.

## Run it

JDK 17 and Maven are bundled in this folder.

```powershell
cd "S:\Projects\1M1B project\smart-waste"
.\run.bat spring-boot:run
```

Open **http://localhost:8080**. Other useful pages:

* API docs (Swagger UI): http://localhost:8080/swagger-ui.html
* Health check: http://localhost:8080/actuator/health
* H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:smartwaste`, user `sa`, password `password`)

Run the tests (33 tests: unit tests for prediction, routing and sustainability, plus end-to-end API tests):

```powershell
.\run.bat test
```

**Try this:** click **Plan routes**, then **Dispatch all**, open **Live map**, and set the clock to **360×**. Or switch on **Autopilot** and watch the Sustainability page fill up.

## API

All JSON. Errors come back as `{status, error, message, fieldErrors?}`.

| Method & path | Purpose |
|---|---|
| `GET /api/dashboard/summary` | Headline numbers: status counts, fleet, open alerts, lifetime totals |
| `GET /api/bins` | All bins with their latest prediction |
| `GET /api/bins/{id}` | One bin |
| `POST /api/bins` | Create a bin (validated, unique code) |
| `PUT /api/bins/{id}` | Update a bin |
| `DELETE /api/bins/{id}` | Delete a bin and its readings |
| `GET /api/bins/urgent` | Bins that need pickup |
| `GET /api/bins/{id}/history` | Last 50 readings |
| `GET /api/bins/{id}/forecast` | 72 h history + 24 h projection |
| `POST /api/bins/{id}/collect` | Mark a bin emptied |
| `GET /api/predictions` | Re-run predictions for every bin |
| `POST /api/routes/plan?includeSoon=false` | Plan fleet routes (replaces undispatched plans). `POST /api/routes/optimize` is an alias. |
| `GET /api/routes/active` | Planned and in-progress routes |
| `GET /api/routes` | Last 30 routes |
| `POST /api/routes/{id}/dispatch` | Send the truck out |
| `POST /api/routes/{id}/complete` | Finish now |
| `POST /api/routes/{id}/cancel` | Cancel the route |
| `GET /api/fleet` | Trucks with live position and active route |
| `GET /api/metrics/comparison` | Fixed schedule vs optimised, for the current state. `GET /api/sustainability/compare` is an alias. |
| `GET /api/metrics/lifetime` | Totals over completed routes |
| `GET /api/metrics/daily?days=7` | Daily totals |
| `GET /api/metrics/zones` | Average fill and demand per zone |
| `GET /api/alerts?open=true` | Alerts |
| `POST /api/alerts/{id}/ack` | Acknowledge one alert |
| `POST /api/alerts/ack-all` | Acknowledge all alerts |
| `GET /api/simulation` | Clock, speed, paused, autopilot, depot |
| `POST /api/simulation` | Change state with `{speed, paused, autopilot}` |
| `POST /api/simulation/reset` | Rebuild the demo district (`POST /api/bins/reset-demo` is an alias) |
| `POST /api/simulation/import` | Multipart CSV of `timestamp,bin,fillPercentage`; `bin` is a code or an id (see `sample-sensor-data.csv`) |
| `GET /api/stream` | Server-Sent Events: `tick` after each simulation step, `alert` when one is raised |

## Project layout

```
src/main/java/com/example/smartwaste/
  algorithm/   PredictionService, RoutingEngine, RouteOptimizationService, SustainabilityService
  service/     BinService, RouteService (lifecycle + truck movement), AlertService, DashboardService, LiveUpdateService (SSE)
  simulation/  SimulationService (demo district, live tick, autopilot, CSV import), WorldLock
  controller/  REST endpoints + the dashboard page
  entity/ dto/ repository/ config/ web/ (error handling)
src/main/resources/
  templates/index.html          single-page dashboard shell
  static/css/app.css            design system (light/dark tokens, components)
  static/js/{app,api,ui,maps}.js  ES modules, no build step (Leaflet + Chart.js from CDN)
```

## Configuration (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `app.depot.latitude` / `longitude` | 17.4401 / 78.3489 | Where routes start and end |
| `app.threshold.urgent` | 90 | Fill % that is urgent on its own |
| `app.threshold.urgent-hours` / `soon-hours` | 8 / 24 | Time to full for urgent / soon |
| `app.sustainability.*` | 4.5 km/L, 2.68 kg/L, 25 km/h, 3 min/stop | Emissions and timing model |
| `smartwaste.simulation.speed` | 60 | Simulated seconds per real second |
| `smartwaste.simulation.tick-ms` | 2000 | How often the world advances |
| `smartwaste.simulation.autopilot` | false | Start with autopilot on |

## Limitations & next steps

* **Simulated sensors.** Replace `SimulationService` with an MQTT ingest from real ultrasonic sensors.
* **Straight-line distances.** Haversine ignores the road network. OSRM or a distance-matrix API would give road distances and traffic-aware times.
* **Heuristic routing.** Sweep, nearest-neighbour and 2-opt are fast and good, but not optimal. OR-Tools CVRP with time windows is the natural upgrade.
* **Linear forecast.** Daily seasonality is visible in the data, so a seasonal model (Holt-Winters, gradient boosting) would forecast better.
* **In-memory H2.** The demo resets on restart. Point the datasource at Postgres for persistence.
* **No authentication.** Add Spring Security before exposing it beyond a demo.
