# Dispatch Load Balancer

Spring Boot backend that assigns delivery orders to a fleet of vehicles. It respects vehicle capacity and order priority, uses Haversine distances, and returns a dispatch plan with a delivery sequence per vehicle. Backend only; there is no UI.

## Problem statement

Given a set of delivery orders (location, weight, priority) and a set of vehicles (start location, capacity), decide which vehicle carries which order so that:

- no vehicle is loaded beyond its capacity,
- HIGH priority orders are placed before MEDIUM, and MEDIUM before LOW,
- the travel distance is kept low.

Orders that cannot be placed are reported separately, never dropped.

## Features

- Bulk order and vehicle intake with validation and update-or-insert by business ID
- Priority-aware greedy allocation and nearest-neighbour route ordering
- Haversine distances (km, two decimals)
- Consistent JSON errors via a single `@RestControllerAdvice`
- Swagger UI (Springdoc)

## Technology stack

Java 21, Spring Boot 3.3.5 (Web, Data JPA, Validation), Hibernate, PostgreSQL, Maven, Springdoc OpenAPI 2.6.0, JUnit 5, Mockito, H2 (tests only).

## Architecture

```
controller/   DispatchController            REST endpoints, request validation (@Valid)
service/      DispatchService               transactions, upsert, loads data for the plan
              DispatchOptimizationService   allocation algorithm (no database access)
              DistanceCalculationService    distance and route-length helpers
              VehicleAllocation             working state of one vehicle during allocation
repository/   OrderRepository, VehicleRepository
model/        DeliveryOrder, Vehicle        JPA entities
dto/          request / response records
exception/    GlobalExceptionHandler, ResourceNotFoundException, InvalidDispatchRequestException
util/         HaversineCalculator
```

The optimiser takes entities in and returns the response DTO, so it can be tested without a database.

## Database design

Hibernate creates and updates the schema (`spring.jpa.hibernate.ddl-auto=update`). There are no migration scripts.

**delivery_orders**: `id` (PK, sequence), `order_id` (unique, not null), `latitude`, `longitude` (double precision), `address` (text), `package_weight` (numeric 12,2), `priority` (HIGH/MEDIUM/LOW, stored as string), `created_at`.

**vehicles**: `id` (PK, sequence), `vehicle_id` (unique, not null), `capacity` (numeric 12,2), `current_latitude`, `current_longitude`, `current_address`, `created_at`.

The unique constraints on the business IDs also provide the lookup indexes. IDs use sequences with an allocation size of 50 so that Hibernate can batch inserts (JDBC batch size is 50).

**Upsert behaviour:** a batch is checked for duplicate IDs, existing rows are loaded with one `IN` query, matching rows are updated, the rest are inserted, and everything is saved with one `saveAll` in one transaction. Plan generation reads the stored data, so the same plan can be regenerated at any time.

## Dispatch algorithm

Implemented in `DispatchOptimizationService`.

1. Sort orders by priority (HIGH, MEDIUM, LOW), then by `orderId` ascending. Vehicles are sorted by `vehicleId`.
2. For each order, consider only vehicles whose remaining capacity is at least the order weight.
3. For each eligible vehicle, compute the incremental distance: the Haversine distance from the vehicle's last assigned stop (or its start point, if it has none yet) to the order.
4. Assign the order to the vehicle with the smallest incremental distance. Ties go to the lower vehicle ID.
5. If no vehicle is eligible, the order goes to `unassignedOrders` with a reason.
6. After all orders are placed, each vehicle's orders are re-sequenced by nearest neighbour starting from the vehicle's start point, and the route distance is the sum of the legs. There is no return-to-depot leg.

Complexity is roughly O(orders × vehicles) for allocation plus O(k²) per vehicle for route ordering, where k is the number of orders on that vehicle.

**Limitations of the heuristic:** this does not guarantee a globally minimal total distance. A high-priority order is always placed first even if that makes the overall route longer, and the incremental distance is measured from the last assigned stop, not from the best insertion point in the final route.

## Haversine formula

```
a = sin²(Δlat / 2) + cos(lat1) · cos(lat2) · sin²(Δlon / 2)
c = 2 · atan2(√a, √(1 − a))
distance = 6371 · c      (kilometres)
```

Inputs are converted from degrees to radians first. See `util/HaversineCalculator`.

## API

Swagger documents every endpoint with example payloads. Postman requests are in `postman/`.

| Method | Path | Success | Description |
|---|---|---|---|
| POST | `/api/dispatch/orders` | 201 | Accept orders (update-or-insert by `orderId`) |
| POST | `/api/dispatch/vehicles` | 201 | Accept vehicles (update-or-insert by `vehicleId`) |
| GET | `/api/dispatch/plan` | 200 | Generate the plan from stored data |

### POST /api/dispatch/orders

```json
{
  "orders": [
    { "orderId": "ORD001", "latitude": 28.6139, "longitude": 77.2090,
      "address": "Connaught Place, Delhi, India", "packageWeight": 15, "priority": "HIGH" }
  ]
}
```
Response: `{ "message": "Delivery orders accepted.", "status": "success" }`

Validation: all fields required, latitude in [-90, 90], longitude in [-180, 180], `packageWeight` > 0, priority one of HIGH / MEDIUM / LOW, no duplicate `orderId` inside one request.

### POST /api/dispatch/vehicles

```json
{
  "vehicles": [
    { "vehicleId": "VEH001", "capacity": 100, "currentLatitude": 28.7041,
      "currentLongitude": 77.1025, "currentAddress": "Karol Bagh, Delhi, India" }
  ]
}
```
Response: `{ "message": "Vehicle details accepted.", "status": "success" }`

Validation: capacity > 0, valid coordinates, non-blank IDs and address, no duplicate `vehicleId` inside one request.

### GET /api/dispatch/plan

Returns 404 if no orders or no vehicles are stored. Otherwise:

```json
{
  "dispatchPlan": [
    {
      "vehicleId": "VEH001",
      "startLatitude": 28.7041,
      "startLongitude": 77.1025,
      "startAddress": "Karol Bagh, Delhi, India",
      "totalLoad": 15.0,
      "totalDistance": 12.45,
      "assignedOrders": [
        { "sequence": 1, "orderId": "ORD001", "latitude": 28.6139, "longitude": 77.2090,
          "address": "Connaught Place, Delhi, India", "packageWeight": 15.0, "priority": "HIGH" }
      ]
    }
  ],
  "unassignedOrders": [
    { "orderId": "ORD006", "address": "Connaught Place, Delhi, India", "packageWeight": 40.0,
      "priority": "LOW", "reason": "Insufficient remaining capacity on all vehicles." }
  ],
  "totalAssignedOrders": 1,
  "totalUnassignedOrders": 1,
  "totalTravelDistance": 12.45
}
```

Numbers above are illustrative. `assignedOrders` is in delivery order. Vehicles with no orders still appear, with load 0 and distance 0.

## Error handling

All errors share one shape:

```json
{ "timestamp": "2026-11-18T10:00:00", "status": 400, "error": "Bad Request", "message": "orders[0].packageWeight: Package weight must be greater than zero." }
```

| Status | When |
|---|---|
| 400 | Validation failures, malformed JSON, invalid priority, duplicate IDs in one request |
| 404 | Plan requested with no orders or no vehicles stored |
| 409 | Database integrity conflict (for example two concurrent requests inserting the same ID) |
| 503 | Database unreachable |
| 500 | Anything unexpected. The body is generic; details are only logged |

## Running tests

```bash
mvn test
```

- `HaversineCalculatorTest`, `DistanceCalculationServiceTest`: plain unit tests
- `DispatchOptimizationServiceTest`: priority order, capacity, vehicle selection, tie-breaking, unassigned handling, route order and distances
- `DispatchServiceTest`: Mockito tests for upsert, duplicate rejection and plan loading
- `DispatchControllerTest`: `@WebMvcTest` for status codes, validation and error bodies
- `OrderRepositoryTest`, `VehicleRepositoryTest`: `@DataJpaTest` on in-memory H2, covering persistence, update-in-place and unique constraints

Repository tests run on H2, not PostgreSQL.

## Local setup

Prerequisites: JDK 21, Maven 3.9+, PostgreSQL.

```bash
createdb dispatch_db        # or create it with your preferred tool

export DB_URL=jdbc:postgresql://localhost:5432/dispatch_db
export DB_USERNAME=postgres
export DB_PASSWORD=your-password

mvn spring-boot:run
```

Or build a jar: `mvn clean package` then `java -jar target/dispatch-load-balancer-1.0.0.jar`.

### Environment variables

See `.env.example`. The application does not read `.env` files itself; export the variables or set them in your hosting dashboard.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/dispatch_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | none | Database password |
| `PORT` | `8080` | HTTP port |

## Swagger

`http://localhost:8080/swagger-ui/index.html` (OpenAPI JSON at `/v3/api-docs`). On a deployed instance use the same path on its base URL.

## Deployment

A `Dockerfile` (multi-stage Maven build, JRE 21 runtime) is included, so any host that builds Docker images works. Create a managed PostgreSQL instance, then set `DB_URL` (JDBC format, `jdbc:postgresql://host:port/db`), `DB_USERNAME`, `DB_PASSWORD` on the service. Most platforms inject `PORT`, which the app honours. Some providers give a `postgres://` URL; convert it to the JDBC form.

After deploying, check `/swagger-ui/index.html`, then submit the sample files from `postman/` and call `GET /api/dispatch/plan`.

## Assumptions and limitations

- Orders and vehicles accumulate across requests; there is no delete or reset endpoint. The plan always uses everything stored.
- Re-submitting an existing ID replaces its data. Duplicate IDs inside a single request are rejected.
- Priority sorting is strict: a HIGH order can take capacity that would have let several smaller LOW orders fit.
- Vehicles are assumed to start the day empty and do not return to the depot.
- Haversine gives straight-line distance, not road distance.
- The schema is managed by Hibernate `update`, not a migration tool.
- There is no authentication.
