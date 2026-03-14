# Trade Engine Standalone Run

The Spring Boot jar is now configured to be **standalone-first**:

* default Redis host is `localhost`
* default Kite callback is `http://localhost:8086/kite/callback`
* Redis auth/TLS can be supplied via environment variables

## Redis env vars

The backend reads these settings through Spring configuration:

```bash
REDIS_HOST=<host>
REDIS_PORT=<port>
REDIS_DB=0
REDIS_USERNAME=default
REDIS_PASSWORD=<password>
REDIS_SSL_ENABLED=true
```

For a local non-TLS Redis instance, only `REDIS_HOST` and `REDIS_PORT` are usually needed.

## Run the jar

```bash
cd trade-engine
mvn -q -DskipTests package
java -jar target/trade-engine-0.0.1-SNAPSHOT.jar
```

## Notes

* Instrument-backed features still expect Redis to be preloaded. Run the loader in [`/Users/shagirulhassan/Desktop/algotrading/rw-charting/instruments-loader/readme.MD`](/Users/shagirulhassan/Desktop/algotrading/rw-charting/instruments-loader/readme.MD) against the same Redis instance before using instrument search and related flows.
* If you still want the old container defaults, start the app with `SPRING_PROFILES_ACTIVE=docker`.
