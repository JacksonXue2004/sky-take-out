// k6 load test for the checkout path. README.md ("Load test") explains how to run it.
//
// Three scenarios run at the same time:
//   checkout          CUSTOMERS customers, each adding to the cart, placing an order and paying, nonstop
//   retried_checkout  one customer sending the same order 10 times at once with one Idempotency-Key
//   merchant_race     one merchant clicking Accept and Reject 4 times each, at once, on a paid order
// The last two must stay correct while the first one loads the system: one order per key, and
// exactly one winning merchant action per order. Their counters must end at 0.
//
// Options, passed with -e:
//   CUSTOMERS, DURATION       load size and length (20, 60s)
//   USER_API, ADMIN_API       where to send requests
//   LEGACY=1                  also send amount and packAmount, which backends before commit 78f3ffe required;
//                             used to measure the previous version for the before/after comparison
//   SKIP_RACE=1               leave out merchant_race (the previous version called Stripe inside rejections)
import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

// Defaults fit docker compose: the customer proxy forwards /user/*, the merchant proxy maps /api/* to /admin/*.
const USER_API = __ENV.USER_API || 'http://host.docker.internal:3000';
const ADMIN_API = __ENV.ADMIN_API || 'http://host.docker.internal:8081/api';
const CUSTOMERS = Number(__ENV.CUSTOMERS || 20);
const DURATION = __ENV.DURATION || '60s';
const LEGACY = __ENV.LEGACY === '1';
const RACE = __ENV.SKIP_RACE !== '1';

const checkoutsCompleted = new Counter('checkouts_completed');
const ordersRefused = new Counter('checkout_orders_refused');
const paymentsFailed = new Counter('checkout_payments_failed');
const retryRounds = new Counter('retry_rounds');
const retryExtraOrders = new Counter('retry_extra_orders');
const raceViolations = new Counter('race_rounds_without_exactly_one_winner');
const raceRounds = new Counter('race_rounds');

const scenarios = {
  checkout: { executor: 'constant-vus', vus: CUSTOMERS, duration: DURATION, exec: 'checkout' },
  retried_checkout: { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'retriedCheckout' },
};
const thresholds = {
  // Listing each request type here also makes k6 print its own latency line.
  'http_req_duration{name:cart_add}': ['p(95)<1500'],
  'http_req_duration{name:submit}': ['p(95)<1500'],
  'http_req_duration{name:pay}': ['p(95)<1500'],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  retry_extra_orders: ['count==0'],
};
if (RACE) {
  scenarios.merchant_race = { executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'merchantRace' };
  thresholds['http_req_duration{name:merchant_action}'] = ['p(95)<1500'];
  thresholds.race_rounds_without_exactly_one_winner = ['count==0'];
}

export const options = { setupTimeout: '120s', scenarios, thresholds };

function params(name, token, extraHeaders) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, extraHeaders || {});
  if (token) headers.authentication = token;
  return { headers, tags: { name } };
}

function post(url, body, p) {
  return http.post(url, JSON.stringify(body), p);
}

function put(url, body, p) {
  return http.put(url, JSON.stringify(body), p);
}

function ok(response) {
  return response.status === 200 && response.json('code') === 1;
}

function newKey() {
  return crypto.sha256(`${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}-${Math.random()}`, 'hex').slice(0, 32);
}

function newCustomer(i) {
  const email = `load-${Date.now()}-${i}@example.test`;
  post(`${USER_API}/user/user/register`, { email, password: 'load-test-password' }, params('setup'));
  const token = post(`${USER_API}/user/user/login`, { email, password: 'load-test-password' }, params('setup')).json('data.token');
  post(`${USER_API}/user/addressBook`, {
    consignee: 'Load Test', phone: '4085550100', sex: '1', detail: '1 Main St',
    provinceName: 'CA', cityName: 'San Jose', districtName: 'Downtown',
  }, params('setup', token));
  const addressId = http.get(`${USER_API}/user/addressBook/list`, params('setup', token)).json('data.0.id');
  return { token, addressId };
}

export function setup() {
  const categories = http.get(`${USER_API}/user/category/list?type=1`, params('setup')).json('data');
  let dish = null;
  for (const category of categories) {
    const dishes = http.get(`${USER_API}/user/dish/list?categoryId=${category.id}`, params('setup')).json('data');
    if (dishes && dishes.length) {
      dish = dishes[0];
      break;
    }
  }
  const customers = [];
  for (let i = 0; i < CUSTOMERS + (RACE ? 2 : 1); i++) customers.push(newCustomer(i));
  const adminToken = post(`${ADMIN_API}/employee/login`,
    { username: 'admin', password: __ENV.ADMIN_PASSWORD || '123456' }, params('setup')).json('data.token');
  return { dishId: dish.id, price: dish.price, customers, adminToken };
}

// Every order holds one item. The current backend ignores amount and packAmount and prices the cart itself.
function orderBody(data, customer) {
  const body = { addressBookId: customer.addressId, payMethod: 1, deliveryStatus: 1, tablewareStatus: 1, tablewareNumber: 1 };
  if (LEGACY) {
    body.packAmount = 0;
    body.amount = data.price;
  }
  return body;
}

function placeOrder(data, customer, key) {
  post(`${USER_API}/user/shoppingCart/add`, { dishId: data.dishId }, params('cart_add', customer.token));
  return post(`${USER_API}/user/order/submit`, orderBody(data, customer),
    params('submit', customer.token, { 'Idempotency-Key': key }));
}

function pay(customer, orderNumber) {
  return put(`${USER_API}/user/order/payment/simulate`, { orderNumber, payMethod: 1 }, params('pay', customer.token));
}

// Every VU, whichever scenario it runs, gets its own customer and so its own cart. VU ids run from 1
// to the total number of VUs across all scenarios, but not in scenario order, so they index the list directly.
function myCustomer(data) {
  return data.customers[exec.vu.idInTest - 1];
}

export function checkout(data) {
  const customer = myCustomer(data);
  const submitted = placeOrder(data, customer, newKey());
  if (!check(submitted, { 'order placed': ok })) {
    ordersRefused.add(1);
    return;
  }
  if (check(pay(customer, submitted.json('data.orderNumber')), { 'order paid': ok })) {
    checkoutsCompleted.add(1);
  } else {
    paymentsFailed.add(1);
  }
}

export function retriedCheckout(data) {
  const customer = myCustomer(data);
  http.post(`${USER_API}/user/shoppingCart/add`, JSON.stringify({ dishId: data.dishId }), params('cart_add', customer.token));
  const request = {
    method: 'POST',
    url: `${USER_API}/user/order/submit`,
    body: JSON.stringify(orderBody(data, customer)),
    params: params('submit', customer.token, { 'Idempotency-Key': newKey() }),
  };
  const responses = http.batch(Array(10).fill(request));
  const placed = responses.filter(ok);
  const ids = new Set(placed.map((r) => r.json('data.id')));
  retryRounds.add(1);
  // One customer action should give one order, however many times the request was sent.
  retryExtraOrders.add(Math.max(ids.size - 1, 0));
  check(null, { 'ten retries, one order': () => placed.length === responses.length && ids.size === 1 });
  if (placed.length) pay(customer, placed[0].json('data.orderNumber'));
}

export function merchantRace(data) {
  const customer = myCustomer(data);
  const submitted = placeOrder(data, customer, newKey());
  if (!ok(submitted) || !ok(pay(customer, submitted.json('data.orderNumber')))) return;
  const orderId = submitted.json('data.id');
  const adminParams = { headers: { 'Content-Type': 'application/json', token: data.adminToken }, tags: { name: 'merchant_action' } };
  const actions = [];
  for (let i = 0; i < 4; i++) {
    actions.push({ method: 'PUT', url: `${ADMIN_API}/order/confirm`, body: JSON.stringify({ id: orderId }), params: adminParams });
    actions.push({ method: 'PUT', url: `${ADMIN_API}/order/rejection`,
      body: JSON.stringify({ id: orderId, rejectionReason: 'Load test' }), params: adminParams });
  }
  const winners = http.batch(actions).filter(ok).length;
  raceRounds.add(1);
  if (!check(null, { 'exactly one merchant action wins': () => winners === 1 })) raceViolations.add(1);
}
