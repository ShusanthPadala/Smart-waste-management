# Smart & Sustainable Waste Collection Management System

## Project Overview
Traditional residential waste collection relies on fixed, static schedules, leading to unnecessary trips for empty bins, overflowing bins in high-usage areas, and inefficient fuel consumption. The Smart Waste Management System solves this by ingesting real-time bin sensor data, predicting when bins will overflow using linear regression, and dynamically generating optimized collection routes for only the bins that need it, dramatically reducing CO₂ emissions and operational costs.

## Architecture
The system operates as a data-driven pipeline:
1. **Data**: Simulates continuous IoT sensor readings for fill levels across city bins.
2. **Prediction**: Analyzes historical readings using linear regression to forecast future fill levels and overflow times.
3. **Priority**: Classifies bins into `NORMAL`, `SOON`, `URGENT`, and `OVERFLOW_RISK` based on thresholds.
4. **Route Optimization**: Uses a Nearest-Neighbor algorithm to calculate the shortest path connecting all `URGENT` bins.
5. **Dashboard**: Provides a unified, dynamic web interface for operators to monitor live status, generate routes, and track sustainability KPIs.

## AI & Algorithm Explanation
*   **Trend-Based Linear Regression**: Instead of just looking at current fill levels, the system calculates the "fill rate" (slope) using the last 10 sensor readings. It automatically detects if a bin was recently emptied to avoid skewed data, and solves the linear equation `y = mx + c` to determine exactly how many hours remain until 100% capacity.
*   **Confidence Scoring (R²)**: The system computes the R-squared statistical measure to determine how well the sensor readings fit a straight line. If the data is highly erratic, confidence drops.
*   **Nearest-Neighbor Routing**: When a route is generated, the algorithm starts at the depot and iteratively finds the closest unvisited urgent bin until all are collected. 
*   **Haversine Distance**: All routing distances are calculated using the Haversine formula, which accurately computes the shortest distance over the earth's surface given latitude and longitude coordinates.
*   **Sustainability Calculations**: The system compares the optimized route (only visiting urgent bins) against a theoretical fixed schedule (visiting all bins) to prove the reduction in driving distance, fuel consumption, and CO₂ emissions.

## How to Run
This project requires JDK 17 and Maven. A local JDK 17 and Maven distribution are included in the workspace.

1. Navigate to the project directory:
   ```bash
   cd "s:\Projects\1M1B project\smart-waste"
   ```
2. Set `JAVA_HOME` to the pinned JDK 17 directory and run via Maven:
   ```powershell
   $env:JAVA_HOME="S:\Projects\1M1B project\smart-waste\jdk17\jdk-17.0.20.1+1"
   .\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
   ```
3. Open the dashboard in your browser: `http://localhost:8080`

To run the unit tests:
```powershell
$env:JAVA_HOME="S:\Projects\1M1B project\smart-waste\jdk17\jdk-17.0.20.1+1"
.\maven\apache-maven-3.9.6\bin\mvn.cmd test
```

## API Documentation

### 1. `GET /api/bins`
Returns a list of all bins and their current status.
**Response:**
```json
[
  { "id": 1, "binCode": "B001", "locationName": "Green Park Residency", "latitude": 40.71, "longitude": -74.00, "capacity": 100.0, "currentFillPercentage": 68.2, "status": "NORMAL" }
]
```

### 2. `POST /api/bins`
Creates a new bin.
**Request:**
```json
{ "binCode": "B005", "locationName": "New Office", "latitude": 40.75, "longitude": -73.98, "capacity": 100.0, "currentFillPercentage": 0.0 }
```

### 3. `PUT /api/bins/{id}`
Updates an existing bin (including manual fill percentage overrides).

### 4. `DELETE /api/bins/{id}`
Deletes a bin and its associated sensor history.

### 5. `GET /api/bins/{id}/history`
Fetches historical sensor readings for a specific bin.

### 6. `GET /api/predictions`
Calculates and returns live predictions for all bins.
**Response:**
```json
[
  { "binId": 4, "currentFillPercentage": 95.0, "predictedFillPercentage": 100.0, "estimatedHoursToFull": 5.2, "predictedOverflowTime": "2026-09-14T19:17:15", "confidence": 0.99, "recommendedStatus": "URGENT" }
]
```

### 7. `GET /api/routes/optimize`
Generates the optimal collection route for urgent bins.
**Response:**
```json
{
  "totalDistanceKm": 2.5,
  "estimatedTravelTimeMinutes": 5.0,
  "estimatedFuelLitres": 0.55,
  "estimatedCo2Kg": 1.49,
  "stops": [ { "sequenceOrder": 1, "binCode": "B004", "locationName": "Community Market" } ]
}
```

### 8. `GET /api/sustainability/compare`
Compares the optimized route to a fixed schedule route.

### 9. `POST /api/demo/reset`
Wipes the database and restores the default demo scenario.

## Database Schema Overview
*   **`Bin`**: Represents a physical waste bin. Contains `binCode`, `locationName`, coordinates, capacity, `currentFillPercentage`, and `status`.
*   **`SensorReading`**: Historical time-series data. Contains a Many-to-One reference to `Bin`, a `timestamp`, and `fillPercentage`.
*   **`CollectionRoute`**: Represents a generated route. Contains overall metrics (distance, time, fuel, CO2).
*   **`RouteStop`**: Contains a Many-to-One reference to `CollectionRoute`. Represents an ordered stop along the route. Includes `sequenceOrder`, `binCode`, and `locationName`.
*   **`Vehicle`**: (Reserved for future multi-vehicle routing).

## Simulation & Demo Data
Because this is a prototype without physical IoT sensors, a `SimulationService` generates realistic 7-day historical fill data on startup.
*   **Realistic Fill Rates**: Bins fill at varying speeds. For example, "Community Market" (Bin 4) fills much faster than residential areas.
*   **Guaranteed Demo Scenario**: To ensure the routing algorithm can be effectively demonstrated, Bin B004 is artificially forced to 95% fill capacity at startup, guaranteeing at least one `URGENT` bin.
*   **Reset Demo Data**: The dashboard includes a "Reset Demo Data" button. If you manually create, edit, or delete bins during testing and ruin the demo state, this button will purge the database and perfectly restore the original 4 bins and the guaranteed-urgent Bin 4 scenario.

## Sustainability Metrics
The `SustainabilityService` measures the environmental impact of the algorithm.
*   **Fuel Efficiency**: Assumed to be `0.22 Liters / Km` (standard heavy-duty diesel garbage truck).
*   **CO₂ Emission Factor**: Assumed to be `2.68 Kg CO₂ / Liter` of diesel burned.
*   **Formulas**: 
    *   `Fuel Consumed (L) = Distance (km) * 0.22`
    *   `CO₂ Emissions (kg) = Fuel Consumed (L) * 2.68`
*   The dashboard compares the *Optimized Route* against a *Fixed Schedule* (which would drive to all 4 bins regardless of fill level) to visualize the percentage saved.

## Limitations
*   **Simulated Data**: The system relies on software-generated data rather than physical IoT ultrasonic sensors.
*   **Heuristic Routing**: The Nearest-Neighbor algorithm is a greedy heuristic. While fast and effective for smaller datasets, it does not guarantee the globally mathematically optimal shortest path (like the exact TSP solution).
*   **Estimated Metrics**: Fuel and CO₂ metrics are based on static constants and do not account for variable factors like load weight, road grade, or idling time.
*   **Single Vehicle**: The current optimization logic assumes a single garbage truck and does not yet support multi-vehicle fleet distribution (CVRP).

## Future Enhancements
*   **Real IoT Sensor Integration**: Replace the simulation layer with an MQTT broker to ingest payloads from real physical ESP32/Arduino ultrasonic sensors.
*   **ML-Based Forecasting**: Replace the linear regression model with an LSTM Neural Network to detect complex, non-linear seasonal patterns in waste generation.
*   **Multi-Vehicle Fleet Routing (CVRP)**: Implement Google OR-Tools to distribute urgent bins across a fleet of multiple garbage trucks with specific carrying capacities.
*   **Traffic-Aware Routing**: Integrate the Google Maps Distance Matrix API to route trucks based on real-time traffic conditions instead of straight-line Haversine distance.
