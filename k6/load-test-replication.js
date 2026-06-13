import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// Custom Metrics
const slaveRoutedReqs  = new Counter('slave_routed_requests');
const masterRoutedReqs = new Counter('master_routed_requests');
const lagErrorRate     = new Rate('replication_lag_errors');
const readResponseTime = new Trend('read_response_time_ms', true);
const writeResponseTime = new Trend('write_response_time_ms', true);

export const options = {
  scenarios: {
    // Slave 조회 부하 (readOnly → Slave 라우팅 확인)
    read_heavy: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      exec: 'readScenario',
    },
    // Master 쓰기 부하
    write_load: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      exec: 'writeScenario',
      startTime: '5s',
    },
    // Replication Lag 재현 — @ForceMaster 효과 검증
    lag_test: {
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 10,
      exec: 'lagScenario',
      startTime: '35s',
    },
  },
  thresholds: {
    http_req_failed:        ['rate<0.01'],
    read_response_time_ms:  ['p(95)<500'],
    write_response_time_ms: ['p(95)<1000'],
    replication_lag_errors: ['rate<0.05'],
  },
};

// k6 run -e TOKEN=xxx k6/load-test-replication.js
const TOKEN   = __ENV.TOKEN || '';
const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

export function readScenario() {
  const start = Date.now();

  const rankRes = http.get(`${BASE_URL}/api/stylists/ranking?page=0&size=10`, { headers });
  check(rankRes, { '랭킹 조회 200': r => r.status === 200 });
  slaveRoutedReqs.add(1);

  const searchRes = http.get(`${BASE_URL}/api/stylists?page=0&size=10`, { headers });
  check(searchRes, { '미용사 검색 200': r => r.status === 200 });
  slaveRoutedReqs.add(1);

  readResponseTime.add(Date.now() - start);
  sleep(0.5);
}

export function writeScenario() {
  const start = Date.now();
  const body = JSON.stringify({
    stylistProfileId: 1,
    menuId: 1,
    reservedAt: new Date(Date.now() + 86400000).toISOString().slice(0, 16),
  });

  const res = http.post(`${BASE_URL}/api/reservations`, body, { headers });
  check(res, { '예약 생성 200/201': r => r.status === 200 || r.status === 201 });
  masterRoutedReqs.add(1);
  writeResponseTime.add(Date.now() - start);
  sleep(1);
}

export function lagScenario() {
  // 예약 생성 직후 즉시 단건 조회
  // @ForceMaster 없으면 Slave Lag으로 404 간헐 발생, 있으면 항상 성공
  const body = JSON.stringify({
    stylistProfileId: 1,
    menuId: 1,
    reservedAt: new Date(Date.now() + 86400000).toISOString().slice(0, 16),
  });

  const createRes = http.post(`${BASE_URL}/api/reservations`, body, { headers });
  if (createRes.status !== 200 && createRes.status !== 201) {
    lagErrorRate.add(true);
    return;
  }

  const reservationId = JSON.parse(createRes.body)?.id;
  if (!reservationId) { lagErrorRate.add(true); return; }

  // 즉시 조회 — @ForceMaster 적용 시 항상 Master에서 읽으므로 Lag 없음
  const getRes = http.get(`${BASE_URL}/api/reservations/${reservationId}`, { headers });
  lagErrorRate.add(getRes.status === 404);

  check(getRes, { '생성 직후 조회 성공(@ForceMaster 효과)': r => r.status === 200 });
  sleep(0.1);
}

export function handleSummary(data) {
  const p95Read   = data.metrics.read_response_time_ms?.values?.['p(95)'] ?? 0;
  const p95Write  = data.metrics.write_response_time_ms?.values?.['p(95)'] ?? 0;
  const lagRate   = (data.metrics.replication_lag_errors?.values?.rate ?? 0) * 100;
  const failRate  = (data.metrics.http_req_failed?.values?.rate ?? 0) * 100;
  const rps       = data.metrics.http_reqs?.values?.rate ?? 0;
  const totalReqs = data.metrics.http_reqs?.values?.count ?? 0;
  const duration  = ((data.state?.testRunDurationMs ?? 0) / 1000).toFixed(1);

  const lines = [
    '════════════════════════════════════════════════════════',
    ' DB REPLICATION LOAD TEST RESULTS',
    '════════════════════════════════════════════════════════',
    '',
    '─── TOTAL RESULTS ──────────────────────────────────────',
    `  Total Requests  : ${totalReqs}`,
    `  RPS             : ${rps.toFixed(1)} req/s`,
    `  Fail Rate       : ${failRate.toFixed(2)}%  ${failRate < 1 ? '✅' : '❌'}`,
    '',
    '─── CUSTOM METRICS ─────────────────────────────────────',
    `  Slave Routed    : ${data.metrics.slave_routed_requests?.values?.count ?? 0} reqs`,
    `  Master Routed   : ${data.metrics.master_routed_requests?.values?.count ?? 0} reqs`,
    `  Lag Error Rate  : ${lagRate.toFixed(2)}%  ${lagRate < 5 ? '✅' : '❌ @ForceMaster 미적용 의심'}`,
    '',
    '─── HTTP ────────────────────────────────────────────────',
    `  Read  p(95)     : ${p95Read.toFixed(0)}ms  ${p95Read < 500 ? '✅' : '❌'}`,
    `  Write p(95)     : ${p95Write.toFixed(0)}ms  ${p95Write < 1000 ? '✅' : '❌'}`,
    '',
    '─── EXECUTION ───────────────────────────────────────────',
    `  Duration        : ${duration}s`,
    `  VUs Peak        : ${data.metrics.vus_max?.values?.max ?? 0}`,
    '',
    '─── NETWORK ─────────────────────────────────────────────',
    `  Data Sent       : ${((data.metrics.data_sent?.values?.count ?? 0) / 1024).toFixed(1)} KB`,
    `  Data Received   : ${((data.metrics.data_received?.values?.count ?? 0) / 1024).toFixed(1)} KB`,
    '════════════════════════════════════════════════════════',
  ];

  return { stdout: lines.join('\n') + '\n' };
}
