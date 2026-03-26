# Trade Engine Local Run

This backend is now local-first.

All runtime configuration lives in `src/main/resources/application.properties`. There is no `.env` bootstrap step and no Spring profile switching for normal local development.

## Prerequisites

* Java 21
* Network access to the Redis instance configured in `src/main/resources/application.properties`
* Valid Kite credentials in `src/main/resources/application.properties` if you need login or broker-backed flows

## Install and configure

1. Clone the repository.
2. Open `trade-engine/src/main/resources/application.properties`.
3. Review these sections before first run:
   * `spring.data.redis`
   * `kite`
   * `app.cors.allowed-origins`
   * `server.port`
   * `internal.port`
4. If you are using instrument search or other Redis-backed instrument features, preload Redis with the loader described in [`/Users/shagirulhassan/Desktop/algotrading/rw-charting/instruments-loader/readme.MD`](/Users/shagirulhassan/Desktop/algotrading/rw-charting/instruments-loader/readme.MD).

## Run the backend

Build the jar:

```bash
cd trade-engine
./mvnw -q -DskipTests package
```

Start the application from the packaged jar:

```bash
java -jar target/trade-engine-0.0.1-SNAPSHOT.jar
```

You can also run it directly from Maven during development:

```bash
./mvnw spring-boot:run
```

## Verify startup

After the app starts:

```bash
curl http://localhost:8086/
curl http://localhost:8086/api/session
curl http://localhost:8086/actuator/health
```

Expected local ports:

* HTTP API: `8086`
* Internal API: `8087`

## Common local flow

1. Start or verify the Redis instance configured in `application.properties`.
2. Run the instruments loader if you need instrument search data.
3. Start the backend.
4. Open the frontend and call the backend on `http://localhost:8086`.

## Notes

* If startup fails, check `src/main/resources/application.properties` first. It is the only supported local config source.
* If Redis is unreachable, Redis-backed endpoints will fail even if the Spring app itself starts.
* If Kite credentials are missing or invalid, auth-related flows will fail even if health endpoints still respond.
