import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { b64encode } from 'k6/encoding';

// Configuration via environment variables with sensible defaults
const BASE_URL = __ENV.K6_BASE_URL || 'http://127.0.0.1:8080';
const USER = __ENV.K6_USER || 'user';
const PASS = __ENV.K6_PASS || 'password';
const GROUP = __ENV.K6_GROUP || 'perf-group';

// Mix ratios for operations (should sum to 1.0). Override via env if needed.
const R_PUSH = Number(__ENV.K6_RATIO_PUSH || 0.45);        // single push
const R_BATCH_PUSH = Number(__ENV.K6_RATIO_BATCH_PUSH || 0.25); // batch push
const R_POP = Number(__ENV.K6_RATIO_POP || 0.20);          // pop
const R_VIEW = Number(__ENV.K6_RATIO_VIEW || 0.10);        // view

const BATCH_SIZE = Number(__ENV.K6_BATCH_SIZE || 20);          // number of messages per batch push
const CONTENT_SIZE = Number(__ENV.K6_CONTENT_SIZE || 256);     // chars per message
const VIEW_LIMIT = Number(__ENV.K6_VIEW_LIMIT || 50);          // messageCount header for view

const AUTH = 'Basic ' + b64encode(`${USER}:${PASS}`);
const commonHeaders = {
  Authorization: AUTH,
  consumerGroup: GROUP,
};

// Custom metrics
const pushLatency = new Trend('push_latency', true);
const batchPushLatency = new Trend('batch_push_latency', true);
const popLatency = new Trend('pop_latency', true);
const viewLatency = new Trend('view_latency', true);
const pushes = new Counter('push_count');
const batchPushes = new Counter('batch_push_count');
const pops = new Counter('pop_count');
const views = new Counter('view_count');

// k6 options
export const options = {
  // Arrival rate model provides more stable throughput signal than VU model
  scenarios: {
    steady_mixed: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.K6_START_RPS || 50), // initial requests per second
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.K6_PRE_VUS || 50),
      maxVUs: Number(__ENV.K6_MAX_VUS || 500),
      stages: [
        { target: Number(__ENV.K6_TARGET_RPS || 200), duration: __ENV.K6_RAMP || '1m' }, // ramp to target RPS
        { target: Number(__ENV.K6_TARGET_RPS || 200), duration: __ENV.K6_HOLD || '3m' }, // hold
        { target: Number(__ENV.K6_END_RPS || 0), duration: __ENV.K6_RAMP_DOWN || '30s' }, // ramp down
      ],
    },
  },
  thresholds: {
    // Global thresholds (can be tuned)
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300', 'p(99)<800'],

    // Operation-specific latency SLOs
    push_latency: ['p(95)<200', 'p(99)<500'],
    batch_push_latency: ['p(95)<400', 'p(99)<800'],
    pop_latency: ['p(95)<200', 'p(99)<500'],
    view_latency: ['p(95)<250', 'p(99)<600'],
  },
};

// Utilities
function contentOfSize(n) {
  // Simple deterministic payload of n characters
  return 'x'.repeat(n);
}

function randomMessages(count, size) {
  const arr = new Array(count);
  for (let i = 0; i < count; i++) {
    arr[i] = contentOfSize(size);
  }
  return arr;
}

// Operations
function pushOne() {
  const body = contentOfSize(CONTENT_SIZE);
  const headers = { ...commonHeaders, 'Content-Type': 'text/plain' };
  const url = `${BASE_URL}/queue/push`;
  const res = http.post(url, body, { headers });
  pushLatency.add(res.timings.duration);
  pushes.add(1);
  check(res, {
    'push status is 200': (r) => r.status === 200,
  });
}

function batchPush() {
  const body = JSON.stringify(randomMessages(BATCH_SIZE, CONTENT_SIZE));
  const headers = { ...commonHeaders, 'Content-Type': 'application/json' };
  const url = `${BASE_URL}/queue/batch/push`;
  const res = http.post(url, body, { headers });
  batchPushLatency.add(res.timings.duration);
  batchPushes.add(1);
  check(res, {
    'batch push status is 200': (r) => r.status === 200,
  });
}

function popOne() {
  const url = `${BASE_URL}/queue/pop`;
  const res = http.get(url, { headers: commonHeaders });
  popLatency.add(res.timings.duration);
  pops.add(1);
  check(res, {
    // Accept 200 (message present) or 404 (empty queue)
    'pop status ok or not found': (r) => r.status === 200 || r.status === 404,
  });
}

function viewSome() {
  const headers = { ...commonHeaders, messageCount: String(VIEW_LIMIT), consumed: 'no' };
  const url = `${BASE_URL}/queue/view`;
  const res = http.get(url, { headers });
  viewLatency.add(res.timings.duration);
  views.add(1);
  check(res, {
    'view status is 200': (r) => r.status === 200,
  });
}

// Weighted mixed workload per iteration
export default function () {
  const x = Math.random();
  if (x < R_PUSH) {
    pushOne();
  } else if (x < R_PUSH + R_BATCH_PUSH) {
    batchPush();
  } else if (x < R_PUSH + R_BATCH_PUSH + R_POP) {
    popOne();
  } else {
    viewSome();
  }
  // tiny think-time to avoid synchrony bursts
  sleep(Number(__ENV.K6_SLEEP || 0.01));
}

/**
Usage:

1) Start the stack (in repo root):
   docker compose up -d
   # or run the app locally with: mvn spring-boot:run

2) Export credentials (match application.properties or your env):
   export K6_BASE_URL=http://127.0.0.1:8080
   export K6_USER=user
   export K6_PASS=password
   export K6_GROUP=perf-group

3) Run load (requires k6):
   k6 run perf/k6/lightq-load.js

   # Customize RPS and mixes:
   K6_START_RPS=100 K6_TARGET_RPS=500 K6_HOLD=5m K6_RATIO_PUSH=0.4 K6_RATIO_BATCH_PUSH=0.4 K6_RATIO_POP=0.15 K6_RATIO_VIEW=0.05 k6 run perf/k6/lightq-load.js

4) Collect results:
   - Observe thresholds and p(50)/p(95)/p(99) in k6 output
   - For profiling, run JVM with JFR:
     JAVA_TOOL_OPTIONS="&#45;XX:StartFlightRecording=filename=lightq.jfr,dumponexit=true,settings=profile"
   - Or use async-profiler (if installed):
     ./profiler.sh -d 60 -e cpu -f /tmp/cpu.svg <pid>

Record baseline and post-optimization metrics in PERFORMANCE.md.
*/
