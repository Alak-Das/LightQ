LightQ Performance Report (Sprint 1)
=====================================

Scope
- Objective: maximize message read/write throughput and minimize latency.
- Changes in this sprint:
  1) Redis I/O and serialization
     - Switched Redis value serialization from JSON to Smile (binary) with Jackson Afterburner.
     - Added a custom RedisSerializer to avoid deprecated APIs and minimize overhead.
     - Enabled Lettuce connection pooling (commons-pool2) with configurable bounds.
     - Continued to use pipelining for cache writes; added vectorized LPUSHALL for batch cache loads.
  2) Batching
     - Added batch push API and service path to reduce per-message round trips and DB index checks.
  3) Hot-path logging
     - Downgraded several hot-path INFO logs to DEBUG to reduce allocation and log I/O.
  4) Tooling
     - Microbenchmarks (JMH) for serialization formats.
     - Load tests (k6) to simulate mixed push/pop/view workloads.
     - Properties for tuning pool sizes and timeouts.

Repository Changes Summary
- Config
  - RedisConfig: Lettuce pooling + Smile serializer with Afterburner.
  - LightQProperties: new pool settings (redisPoolMaxTotal/MaxIdle/MinIdle).
  - application.properties: default pool settings.
  - SecurityConfig: authorized new batch push endpoint.
- Services/Controller
  - CacheService: addMessages(List<Message>) with single pipeline and LPUSHALL per group.
  - PushMessageService: pushBatch + async batch persistence.
  - MessageController: /queue/batch/push endpoint.
- Tooling
  - perf/k6/lightq-load.js: k6 load script.
  - src/test/java/com/al/lightq/bench/SerializationBench.java: JMH bench.
  - pom.xml: jackson-module-afterburner, jackson-dataformat-smile, JMH deps, commons-pool2.

How to Run
1) Start stack via Docker (recommended)
   - docker compose up -d
   - Default credentials in application.properties:
     - user/password -> role USER
     - admin/adminpassword -> role ADMIN

2) Run locally without Docker (alternative)
   - MongoDB and Redis must be reachable (see docker-compose.yml or environment)
   - mvn spring-boot:run

3) Verify health
   - curl http://127.0.0.1:8080/actuator/health

Load Testing (k6)
- Script: perf/k6/lightq-load.js
- Default uses Basic auth (user/password) and consumerGroup=perf-group.
- Example (steady mixed workload):
  K6_BASE_URL=http://127.0.0.1:8080 \
  K6_USER=user K6_PASS=password \
  K6_GROUP=perf-group \
  K6_START_RPS=100 K6_TARGET_RPS=400 K6_HOLD=5m \
  k6 run perf/k6/lightq-load.js

- Customize operation mix:
  K6_RATIO_PUSH=0.4 K6_RATIO_BATCH_PUSH=0.4 K6_RATIO_POP=0.15 K6_RATIO_VIEW=0.05 ...

- Batch size/content size:
  K6_BATCH_SIZE=100 K6_CONTENT_SIZE=512 ...

- View limit:
  K6_VIEW_LIMIT=50

Profiling
- Java Flight Recorder (JFR):
  JAVA_TOOL_OPTIONS="-XX:StartFlightRecording=filename=lightq.jfr,dumponexit=true,settings=profile" \
  mvn spring-boot:run
  # Run k6 for 3-5 minutes and then inspect lightq.jfr in JMC

- async-profiler (if installed on host):
  # Find Java PID (jps -l)
  ./profiler.sh -d 60 -e cpu -f /tmp/cpu.svg <pid>
  ./profiler.sh -d 60 -e alloc -f /tmp/alloc.svg <pid>

Microbenchmarks (JMH)
- Bench class: src/test/java/com/al/lightq/bench/SerializationBench.java
- Run from IDE: run main() in SerializationBench
- Or via Maven to compile annotations:
  mvn -q -DskipTests=false -Dtest=com.al.lightq.bench.SerializationBenchTestPlaceholder test
  # Then run the main() to view results

Expected Results (Guidance)
- Serialization (Message payload ~0.5KB)
  - Smile+Afterburner vs JSON: expect ~1.2x–2.0x throughput gain and lower garbage for (de)serialization.
- Redis
  - Pooling prevents head-of-line blocking under concurrency (avoid single-connection contention).
  - Pipelined LPUSHALL reduces round-trips for cache writes; expect reduced P95 for batch push flows.
- Batch push
  - For batch sizes >= 20, end-to-end push throughput should improve significantly (fewer per-message operations).

Performance Targets (Initial)
- Push latency (P95): < 200 ms
- Pop latency (P95): < 200 ms
- View latency (P95): < 250 ms
- Error rate: < 1%

Configuration Tuning
- application.properties
  lightq.redis-command-timeout-seconds=5
  lightq.redis-shutdown-timeout-seconds=2
  lightq.redis-pool-max-total=64
  lightq.redis-pool-max-idle=32
  lightq.redis-pool-min-idle=8
  # increase pool sizes under high parallelism and ensure Redis host capacity.

- Threading (AsyncConfig via LightQProperties)
  lightq.core-pool-size=5
  lightq.max-pool-size=10
  lightq.queue-capacity=25
  # Tune according to CPU and Mongo/Redis latencies; check JFR to avoid excessive context switching.

Before/After Methodology
- Baseline:
  - Checkout the main branch or a pre-optimization commit.
  - Use k6 to execute a steady mixed workload for 3–5 minutes.
  - Record p50/p95/p99 latency, operation rates, and error rates.
  - Record JFR snapshot and note top CPU/alloc hotspots (classes, methods).

- After:
  - Checkout the optimization branch (this sprint) and repeat the exact workload/steps.
  - Record the same metrics and JFR.
  - Ensure environment is unchanged between runs (hardware, JVM flags, Redis/Mongo host, network).

Reporting Template (fill with your measurements)
- Environment:
  - Host: CPU model, cores, RAM
  - Java: java -version
  - Redis: version, host location
  - MongoDB: version, host location

- Workload:
  - k6 configs: START_RPS, TARGET_RPS, HOLD, ratios, batch size, content size

- k6 Results (example)
  | Operation | p50 (ms) | p95 (ms) | p99 (ms) | RPS | Error% |
  |-----------|----------|----------|----------|-----|--------|
  | push      |          |          |          |     |        |
  | batchPush |          |          |          |     |        |
  | pop       |          |          |          |     |        |
  | view      |          |          |          |     |        |

- Throughput totals:
  - Total req/s: 
  - CPU%/GC activity observations:

- Profiling (JFR/async-profiler):
  - Top 5 hotspots (baseline):
  - Top 5 hotspots (after):
  - Notable reductions in (de)serialization CPU, Redis I/O wait, logging overhead:

Risk/Trade-offs
- Binary Smile payloads are internal to Redis cache only; REST API still uses JSON/text as before.
- Increased Redis connection pool sizes raise connection counts; tune according to Redis capacity.
- Batch endpoints reduce per-message latency but may change client behavior; clients should opt-in.

Next Steps (Potential Sprint 2)
- Add batch pop API (vectorized read path + reservation) with Redis/Mongo coordination.
- Introduce Redis streams for reservation patterns if needed.
- Consider Netty-based HTTP or reactive stack for further I/O scale, after profiling confirms benefit.
- Add CI job to run a smoke load (mini-k6) for perf regression detection.
