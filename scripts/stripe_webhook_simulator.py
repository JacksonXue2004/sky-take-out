#!/usr/bin/env python3
"""Send simulated Stripe webhook deliveries to a running Sky Take-Out backend.

Each scenario is a situation a real webhook endpoint meets: a valid event, Stripe delivering the
same event again, a body changed in transit, a captured request replayed later, and a correctly
signed event whose amount does not match the order. The script signs events exactly as Stripe
does (HMAC-SHA256 over "<timestamp>.<body>"), so it needs the same webhook secret as the backend.

Example, against the Docker setup (the customer proxy forwards /notify/*):

    python scripts/stripe_webhook_simulator.py --url http://localhost:3000/notify/stripe \
        --secret "$STRIPE_WEBHOOK_SECRET" --order <order number> --amount 25.00

The order must be waiting for payment. Only the Python standard library is used.
"""
import argparse
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal


def checkout_completed(event_id, order_number, amount_cents, payment_intent):
    return json.dumps({
        "id": event_id,
        "object": "event",
        "type": "checkout.session.completed",
        "created": int(time.time()),
        "data": {"object": {
            "id": "cs_test_" + uuid.uuid4().hex,
            "object": "checkout.session",
            "amount_total": amount_cents,
            "currency": "usd",
            "payment_status": "paid",
            "payment_intent": payment_intent,
            "metadata": {"order_number": order_number},
        }},
    }, separators=(",", ":"))


def signature(secret, body, timestamp):
    digest = hmac.new(secret.encode(), f"{timestamp}.{body}".encode(), hashlib.sha256).hexdigest()
    return f"t={timestamp},v1={digest}"


def deliver(url, body, signature_header):
    request = urllib.request.Request(url, data=body.encode(), method="POST", headers={
        "Content-Type": "application/json",
        "Stripe-Signature": signature_header,
    })
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", required=True, help="webhook endpoint, ending in /notify/stripe")
    parser.add_argument("--secret", required=True, help="the backend's STRIPE_WEBHOOK_SECRET")
    parser.add_argument("--order", required=True, help="number of an order that is waiting for payment")
    parser.add_argument("--amount", required=True, help="the order total in dollars, for example 25.00")
    args = parser.parse_args()

    cents = int((Decimal(args.amount) * 100).to_integral_value())
    payment_intent = "pi_simulated_" + uuid.uuid4().hex[:16]
    valid = checkout_completed("evt_simulated_" + uuid.uuid4().hex, args.order, cents, payment_intent)
    wrong_amount = checkout_completed("evt_simulated_" + uuid.uuid4().hex, args.order, cents + 1, payment_intent)
    now = int(time.time())

    # The ones that must not pay the order go first, while it is still waiting for payment.
    scenarios = [
        ("tampered body", valid.replace(f'"amount_total":{cents}', '"amount_total":1'),
         signature(args.secret, valid, now), 400, "invalid signature"),
        ("replayed 10 minutes later", valid, signature(args.secret, valid, now - 600), 400, "invalid signature"),
        ("signed, wrong amount", wrong_amount, signature(args.secret, wrong_amount, now), 200, "rejected"),
        ("valid payment", valid, signature(args.secret, valid, now), 200, "processed"),
        ("same event again", valid, signature(args.secret, valid, now), 200, "duplicate"),
        ("and a third time", valid, signature(args.secret, valid, now), 200, "duplicate"),
    ]

    as_expected = 0
    for name, body, header, expected_status, expected_text in scenarios:
        status, response = deliver(args.url, body, header)
        ok = status == expected_status and expected_text in response
        as_expected += ok
        print(f"{'ok  ' if ok else 'FAIL'} {name:<26} {status} {response}")
    print(f"{as_expected}/{len(scenarios)} deliveries handled as expected")
    return 0 if as_expected == len(scenarios) else 1


if __name__ == "__main__":
    sys.exit(main())
