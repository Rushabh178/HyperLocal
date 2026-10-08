# HyperLocal API map

Base URL: `http://localhost:8080/api/v1`

**Status:** ✅ = working now · 🚧 = stub that returns `501 NOT_IMPLEMENTED` (none left). Rarer cases that are not handled yet are marked `TODO(edge-case)` in the code.

## Running locally
Needs Java 25+, PostgreSQL with PostGIS, and (for tests) Docker.
1. `cp .env.example .env` and fill it in; `.env` is git-ignored and loaded automatically, real environment variables win.
2. `./mvnw spring-boot:run`. Hibernate creates/updates the tables, then `db/indexes.sql` adds the GiST and partial unique indexes.
3. `./mvnw test` runs the integration tests against a throwaway `postgis/postgis` container.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_PASSWORD` | yes | – | PostgreSQL password |
| `JWT_SECRET` | yes | – | Base64 of ≥ 32 random bytes (`openssl rand -base64 48`) |
| `DB_URL` / `DB_USERNAME` | no | `jdbc:postgresql://localhost:5432/hyper_local` / `postgres` | |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | no | – | first ops admin, created on startup |
| `PAYMENT_WEBHOOK_SECRET` | prod | dev value | HMAC key for payment callbacks |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | with `razorpay` | – | Razorpay gateway |
| `OSRM_BASE_URL` | with `osrm` | `http://localhost:5000` | OSRM routing server |
| `STORAGE_DIR` / `PUBLIC_BASE_URL` | no | `./uploads` / `http://localhost:8080` | local file storage for KYC uploads |

Profiles (`SPRING_PROFILES_ACTIVE`): none = fake payment gateway, haversine routing, local file storage; `razorpay` = Razorpay gateway; `osrm` = OSRM routing.

Concurrent edits of the same order or payment return `409 CONCURRENT_UPDATE`; just retry.

All errors share one JSON shape:

```json
{ "status": 403, "code": "PHONE_NOT_VERIFIED", "message": "...", "fieldErrors": { "phone": "..." }, "path": "...", "timestamp": "..." }
```

Switch on `code` in the apps, not on `message`.

---

## How sign-in works

There are three separate account types. Each has its own table, its own URL space and its own JWT role, so a customer token can never call a delivery-partner endpoint (it gets `403`).

| App | Auth endpoints (public) | Protected endpoints | JWT role |
|---|---|---|---|
| Customer | `/customer/auth/**` | `/customer/**`, `/payments/**` | `CUSTOMER` |
| Delivery partner | `/agent/auth/**` | `/agent/**` | `DELIVERY_AGENT` |
| Kirana / restaurant | `/merchant/auth/**` | `/merchant/**` | `MERCHANT` |
| Ops console | `/admin/auth/**` | `/admin/**` | `ADMIN` |

- **Access token:** a JWT (HS256) valid for 15 minutes. Send it as `Authorization: Bearer <token>`.
- **Refresh token:** an opaque token valid for 30 days. It is rotated on every `/refresh`. Re-using an old refresh token is treated as theft, so every session of that user is revoked.
- **OTP:** 6 digits, valid for 5 minutes, max 5 wrong attempts, 30 s before a resend. OTPs are scoped by role, so a customer OTP can't verify a partner account.
  - In development (`app.otp.provider=log`) the OTP is printed in the server log as `[DEV OTP] ...`.
- **Phone numbers:** `9876543210`, `+91 98765 43210` and `919876543210` are all accepted and stored as `+919876543210`. The same number can hold a customer account *and* a partner account.

### Customer sign-up flow
```
POST /customer/auth/signup          {fullName, phone, email?, password}    → 201 {userId, nextStep:"VERIFY_PHONE", otpExpiresAt}
POST /customer/auth/verify-phone    {phone, otp}                           → {accessToken, refreshToken, expiresIn, role, user}
```

### Delivery-partner sign-up flow
```
POST /agent/auth/signup             {fullName, phone, email?, password, vehicleType, vehicleNumber, city}
POST /agent/auth/verify-phone       {phone, otp}            → tokens; user.verificationStatus = PENDING_DOCUMENTS
PUT  /agent/me/documents/{type}     {documentNumber, fileUrl}   (repeat for each type listed in user.missingDocuments)
                                    → verificationStatus becomes UNDER_REVIEW when all are uploaded
(admin approves)                    → APPROVED
PUT  /agent/me/duty                 {online:true}           → AVAILABLE (403 AGENT_NOT_APPROVED before approval)
```
Required documents: `AADHAAR, PAN, PROFILE_PHOTO`, plus `DRIVING_LICENSE, VEHICLE_RC` for motorised vehicles.

Ops approves partners and stores through the admin endpoints below.

---

## Customer app

### Auth: `/customer/auth` (public)
| Method | Path | Body | Status |
|---|---|---|---|
| POST | `/signup` | `fullName, phone, email?, password` | ✅ |
| POST | `/verify-phone` | `phone, otp` | ✅ |
| POST | `/verify-phone/resend` | `phone` | ✅ |
| POST | `/login` | `identifier` (phone or email), `password` | ✅ |
| POST | `/otp/send` | `phone` (password-less login) | ✅ |
| POST | `/otp/login` | `phone, otp` | ✅ |
| POST | `/refresh` | `refreshToken` | ✅ |
| POST | `/logout` | `refreshToken` | ✅ |
| POST | `/password/forgot` | `phone` | ✅ |
| POST | `/password/reset` | `phone, otp, newPassword` | ✅ |

### Profile: `/customer/me`
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/` | profile | ✅ |
| PATCH | `/` | `fullName?, email?` | ✅ |
| PUT | `/password` | `currentPassword, newPassword`; signs out other devices, returns new tokens | ✅ |
| DELETE | `/` | soft delete, frees the phone number | ✅ |

### Addresses: `/customer/addresses`
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/` | default address first | ✅ |
| POST | `/` | see body below; the first address becomes the default; max 10 | ✅ |
| GET | `/{id}` | | ✅ |
| PUT | `/{id}` | same body as POST | ✅ |
| PUT | `/{id}/default` | | ✅ |
| DELETE | `/{id}` | soft delete; the next address is promoted to default | ✅ |
| POST | `/serviceability` | `{lat,lng}` → `{serviceable, serviceRadiusMeters, nearbyRestaurants, nearbyKiranaStores, message}`; counts approved stores within 2 km | ✅ |

Address body: `label` (HOME/WORK/OTHER), `customLabel?`, `recipientName?`, `recipientPhone?`, `houseNumber`, `buildingName?`, `street?`, `landmark?`, `area?`, `city`, `state`, `pincode`, `location {lat,lng}`, `deliveryInstructions?`, `makeDefault?`. The server stores a PostGIS point plus its H3 cell.

### Catalog: `/catalog` (public GET)
Pass the selected address's pin as `lat`/`lng`. Everything is limited to approved stores within 2 km (`app.ops.service-radius-meters`).
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/restaurants?lat&lng&cuisine&page&size` | open restaurants first, then nearest; closed ones have `open:false`; `etaMinutes` = default prep (15) + travel time; `rating` is null until rated | ✅ |
| GET | `/restaurants/{id}/menu` | grouped by category; sold-out dishes have `available:false` | ✅ |
| GET | `/groceries?lat&lng&q&category&page&size` | "virtual warehouse": products listed by **open** Kiranas within 2 km, one row per product (name + pack size) at the cheapest price; empty when no Kirana is open nearby | ✅ |
| GET | `/search?lat&lng&q` | `{restaurants (≤10, name or cuisine), dishes (≤20), groceries (≤20)}` | ✅ |

### Unified cart: `/customer/cart`
One persistent cart per customer: food from **one** restaurant plus any groceries. Prices are always read from the live catalogue.
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/` | `{restaurantId, foodItems, groceryItems, deliveryAddressId, bill, nudge}` (empty cart if none) | ✅ |
| POST | `/items` `{itemType FOOD/GROCERY, itemId, restaurantId?, quantity}` | `restaurantId` required for FOOD; food from another restaurant **replaces** the food section; same item again adds to the quantity (max 20); `409 STORE_CLOSED` if the store is closed | ✅ |
| PATCH | `/items/{cartItemId}` `{quantity}` | 0 removes the line | ✅ |
| DELETE | `/items/{cartItemId}` | returns the cart | ✅ |
| DELETE | `/` | 204 | ✅ |
| PUT | `/address` `{addressId}` | must be yours, within 2 km of the restaurant and of an open Kirana if there are groceries (`400 ADDRESS_NOT_SERVICEABLE`) | ✅ |
| GET | `/nudges` | same object as `cart.nudge`; `message:null` and no meals when it doesn't apply | ✅ |

Bill (`app.ops.*`): `deliveryFee` ₹25, **waived** when the cart has food or groceries reach ₹150 · `convenienceFee` ₹10 flat · `taxes` 5% GST on food only · `discount` 0 for now.
Nudge: a grocery-only cart under ₹150 gets `{groceryTotal, threshold, message, suggestedMeals}` — the 5 priciest available dishes from open restaurants near the cart's address (or the default address).

### Orders: `/customer/orders`
| Method | Path | Notes | Status |
|---|---|---|---|
| POST | `/checkout` `{addressId, paymentMethod UPI/WALLET, upiVpa?}` + optional `Idempotency-Key` header | → 201 `{orderId, status, payableAmount, payment}`. Re-validates and re-prices the cart, freezes it into an order, takes the payment, clears the cart. Same key again returns the first order. Errors: `400 CART_EMPTY`, `400 ADDRESS_NOT_SERVICEABLE`, `409 ITEM_UNAVAILABLE`, `409 STORE_CLOSED`, `409 INSUFFICIENT_WALLET_BALANCE` | ✅ |
| GET | `/?status&page&size` | newest first | ✅ |
| GET | `/{id}` | items, bill, address, rider, `deliveryOtp` (4 digits, shown while the order is active), `stops` | ✅ |
| GET | `/{id}/timeline` | unified Kirana → Restaurant → Home line | ✅ |
| GET | `/{id}/tracking` | `{orderId, status, agentLocation, etaMinutes, stops, updatedAt}` snapshot; live updates over WebSocket | ✅ |
| POST | `/{id}/cancel` `{reason}` | only before pickup (`409 ORDER_NOT_CANCELLABLE` / `ORDER_ALREADY_PICKED_UP`); releases the UPI hold or refunds a wallet payment to the wallet | ✅ |
| POST | `/{id}/rating` `{deliveryRating?, restaurantRating?, kiranaRating?, comment?}` | completed orders only, once (`409 ALREADY_RATED`); updates the stores' average rating | ✅ |

**Stops** (order details, tracking, `ORDER_STATUS` and `STOPS_UPDATED`): the rider's real stops once someone has the order; before that the planned stops with `stopId: null`.
`status` is `PENDING`, `ARRIVED`, `PICKED_UP` (Kirana / restaurant), `DELIVERED` (customer) or `SKIPPED`.
```json
"stops": [
  { "stopId": "e2d4…", "type": "KIRANA",     "name": "Sharma Kirana", "status": "PICKED_UP", "completedAt": "2026-10-08T13:31:02Z" },
  { "stopId": "9a01…", "type": "RESTAURANT", "name": "Spice Hub",     "status": "ARRIVED",   "completedAt": null },
  { "stopId": "77bc…", "type": "CUSTOMER",   "name": "Home",          "status": "PENDING",   "completedAt": null }
]
```
**Bill**: `{foodSubtotal, grocerySubtotal, deliveryFee, convenienceFee, taxes, discount, total, refunds, netTotal}`. `refunds` is what has already gone back to the customer (missing items, unavailable groceries, cancellation) and `netTotal = total - refunds`; in the cart `refunds` is `0.00` and `netTotal = total`.

**Order status** (state machine; illegal moves return `409 ILLEGAL_STATE_TRANSITION`):
`PENDING → CONFIRMED → (BATCHED →) PICKING → OUT_FOR_DELIVERY → COMPLETED`, and `CANCELLED` from anything before pickup.
With the fake gateway (default) and wallet payments the order is `CONFIRMED` right away; with a real gateway it stays `PENDING` until the payment webhook confirms the hold.
Each part of the order also has its own status (`foodStatus` / `groceryStatus` internally): `AWAITING_PAYMENT → NEW → ACCEPTED ⇄ DELAYED → READY → PICKED_UP` for food, `AWAITING_PAYMENT → BROADCASTING → READY → PICKED_UP` (or `UNFULFILLED`) for groceries.

Deleting the account (`DELETE /customer/me`) is refused with `409 ORDER_IN_PROGRESS` while an order is active.

### Payments & wallet
| Method | Path | Notes | Status |
|---|---|---|---|
| POST | `/payments/orders/{orderId}/upi-hold` | retry a `FAILED` UPI hold of a `PENDING` order; confirms the order if the new hold succeeds. `409 HOLD_NOT_RETRYABLE` / `ORDER_NOT_AWAITING_PAYMENT` | ✅ |
| GET | `/payments/orders/{orderId}` | `{paymentId, orderId, amount, status, upiIntentUrl, expiresAt}` | ✅ |
| POST | `/payments/webhook` (public) | gateway callback, authenticated **only** by its HMAC-SHA256 signature header (`X-Signature`, or `X-Razorpay-Signature`), checked before the body is read: `401 INVALID_SIGNATURE`. Idempotent: a redelivered event id is acknowledged and ignored. A confirmed hold confirms the order (dispatch starts); a failed hold marks the payment `FAILED` | ✅ |
| GET | `/customer/wallet` | `{balance, currency}`; balance = sum of the append-only ledger | ✅ |
| GET | `/customer/wallet/transactions?page&size` | newest first | ✅ |

Payment status: `HOLD_PENDING → HELD → CAPTURED → PARTIALLY_REFUNDED → REFUNDED`, `HOLD_PENDING/HELD → RELEASED`, `HOLD_PENDING → FAILED → HOLD_PENDING` (retry).
Money flow: the hold is captured at the first pickup (Kirana QR / restaurant hand-over), which also records what each store is owed. Missing items and unavailable groceries are refunded to the **wallet**; cancelling releases an uncaptured hold or refunds the rest to the wallet.

Gateways (`PaymentGateway`): the default is a fake that approves holds instantly. **Dev only:** a `upiVpa` ending in `@pending` keeps the hold `HOLD_PENDING` until you post a webhook `{"eventId","reference","type":"HOLD_CONFIRMED"|"HOLD_FAILED"}` signed as hex HMAC-SHA256 of the body with `PAYMENT_WEBHOOK_SECRET`. A Razorpay skeleton runs with the `razorpay` profile (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`).

---

## Delivery-partner app

### Auth: `/agent/auth` (public)
Same endpoints as the customer auth (`signup`, `verify-phone`, `verify-phone/resend`, `login`, `otp/send`, `otp/login`, `refresh`, `logout`, `password/forgot`, `password/reset`) — all ✅.
Sign-up also takes `vehicleType` (MOTORCYCLE/SCOOTER/EV_SCOOTER/BICYCLE), `vehicleNumber` (not needed for BICYCLE) and `city`.

### Profile, KYC, duty: `/agent/me`
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/` | includes `verificationStatus`, `missingDocuments`, `dutyStatus` | ✅ |
| PATCH | `/` | `fullName?, email?, city?` | ✅ |
| PUT | `/password` | | ✅ |
| GET | `/documents` | document numbers are masked | ✅ |
| POST | `/documents/{type}/upload-url` | `{contentType image/jpeg / image/png / application/pdf}` → `{uploadUrl, fileUrl, expiresAt}`. PUT the raw file to `uploadUrl` (no token, same `Content-Type`, max 10 MB, valid 15 min), then save `fileUrl` with PUT `/documents/{type}` | ✅ |
| PUT | `/documents/{type}` | `documentNumber?, fileUrl` | ✅ |
| PUT | `/duty` | `{online}`: APPROVED partners only; can't go offline mid-delivery | ✅ |
| POST | `/location` | `{location{lat,lng}, headingDegrees?, speedKmph?}`; updates PostGIS point + H3 cell | ✅ |

### Trips: `/agent`
Approved, online partners only. Offers reach the nearest free rider (H3 cells + distance around the first pickup); each offer is valid for `app.ops.rider-offer-timeout` (30 s), then goes to the next rider.
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/offers` | open offers: `[{offerId, orderIds, stops, totalDistanceMeters, estimatedEarnings, expiresAt}]` (also pushed on `/user/queue/route-updates`) | ✅ |
| POST | `/offers/{id}/accept` | first accept wins; `409 OFFER_NO_LONGER_AVAILABLE` / `409 RIDER_BUSY`. Rider → `ACTIVE`, order → `PICKING`; returns the locked route | ✅ |
| POST | `/offers/{id}/reject` `{reason?}` | 204; the order goes to the next rider straight away | ✅ |
| GET | `/route` | `{routeId, locked:true, stops[{stopId, sequence, type, name, address, location, orderIds, items, state, eta}], remainingDistanceMeters, estimatedCompletionAt}`; `404` when no active route | ✅ |
| POST | `/route/stops/{stopId}/arrived` | | ✅ |
| POST | `/route/stops/{stopId}/kirana-qr` `{qrPayload}` | Kirana stop: the shop's QR must match (`400 QR_WRONG_STORE` / `QR_INVALID` / `QR_EXPIRED`); captures the payment and records the Kirana's payout | ✅ |
| POST | `/route/stops/{stopId}/items/{itemId}/deficient` `{reason?}` | `itemId` = the stop item's id; refunds that line to the customer's wallet; if nothing is left the stop is skipped (and a grocery-only order is cancelled) | ✅ |
| POST | `/route/stops/{stopId}/picked-up` | restaurant stop; records the restaurant's payout | ✅ |
| POST | `/orders/{orderId}/deliver` `{deliveryOtp}` | 4-digit code from the customer; `400 DELIVERY_OTP_INVALID`. Completes the order, books earnings, frees the rider when the route is empty | ✅ |
| POST | `/route/injections/{id}/ack` | 204 | ✅ |
| GET | `/deliveries?page&size` | `{orderId, batched, stops, earnings, completedAt}` | ✅ |
| GET | `/earnings?period=TODAY/WEEK/MONTH` | Indian time; base ₹30 per order + ₹10 per extra pickup + batching incentives | ✅ |
| GET | `/earnings/payouts?page&size` | | ✅ |

Stops must be worked in order (Linear Route Locking): acting on any stop but the current one returns `409 STOP_LOCKED`. One order's stops always run Kirana → Restaurant → Customer; a Kirana that claims after the rider accepted is inserted before the restaurant.

---

## Merchant app (Kirana + restaurant)

### Auth: `/merchant/auth` (public)
Same flow as the other apps, but merchants sign in with their **phone number only** (no email login).
| Method | Path | Body | Status |
|---|---|---|---|
| POST | `/signup` | see below | ✅ |
| POST | `/verify-phone` · `/verify-phone/resend` | `phone, otp` · `phone` | ✅ |
| POST | `/login` | `identifier` (phone), `password` | ✅ |
| POST | `/otp/send` · `/otp/login` | `phone` · `phone, otp` | ✅ |
| POST | `/refresh` · `/logout` | `refreshToken` | ✅ |
| POST | `/password/forgot` · `/password/reset` | `phone` · `phone, otp, newPassword` | ✅ |

Sign-up body: `type` (KIRANA/RESTAURANT), `storeName`, `ownerName`, `phone`, `password`, `address`, `pincode`, `location {lat,lng}`, `fssaiNumber` (**required for RESTAURANT**, else `400 FSSAI_REQUIRED`), `gstin?`, `upiVpa?`, `cuisines?` (restaurants, up to 5), `imageUrl?`.
A new store is `verificationStatus: PENDING` until ops approves it, and cannot open before that (`403 MERCHANT_NOT_APPROVED`).

### Store: `/merchant`
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/me` | profile incl. `verificationStatus`, `rejectionReason`, `open` | ✅ |
| PUT | `/me/password` | `currentPassword, newPassword` | ✅ |
| PUT | `/me/store-status` | `{open}`; approved stores only | ✅ |
| GET | `/store-qr` | Kirana only; signed payload `HLQR1.<merchantId>.<exp>.<sig>`, valid 15 min — show it as a QR and refresh before `expiresAt` | ✅ |
| GET | `/broadcasts` | Kirana only. Open rounds this shop was pinged for: `[{broadcastId, orderId, items[{name, packSize, quantity}], payoutAmount, distanceMeters, expiresAt}]` (a handful at most, so not paged) | ✅ |
| POST | `/broadcasts/{id}/claim` ("Items in Hand") | Kirana only. First tap wins (atomic): `{broadcastId, orderId, claimed, message}`; late or un-targeted taps get `claimed:false` | ✅ |
| GET | `/orders?status&page&size` | paginated. Restaurants see their kitchen tickets, Kiranas the orders they claimed: `{orderId, status, items, amount, prepDeadline, riderName, riderEta}` | ✅ |
| POST | `/orders/{id}/accept` `{prepMinutes 1-90}` | restaurant only; `NEW → ACCEPTED`, sets `prepDeadline` | ✅ |
| POST | `/orders/{id}/delay` `{additionalMinutes 1-60, reason?}` | restaurant only; `→ DELAYED`, pushes `prepDeadline` back and holds back the Kirana broadcast | ✅ |
| POST | `/orders/{id}/ready` | restaurant only; `→ READY` | ✅ |

Merchant order `status`: `NEW, ACCEPTED, DELAYED, READY, PICKED_UP, CANCELLED` (`PREPARING` is accepted as a filter alias of `ACCEPTED`). Illegal moves return `409 ILLEGAL_STATE_TRANSITION`; a Kirana calling a restaurant-only endpoint gets `403 RESTAURANT_ONLY` (and vice versa `403 KIRANA_ONLY`).
| GET | `/items?page&size` | paginated (`{items, page, size, totalItems}`), by category then name | ✅ |
| POST | `/items` | see body below → 201 | ✅ |
| PUT | `/items/{id}` | same body as POST | ✅ |
| PATCH | `/items/{id}/availability` | `{available}` | ✅ |

Item body: `name`, `description?`, `price` (> 0), `category`, `veg?`, `zone` (HOT/COLD/AMBIENT), `imageUrl?`, `available`, and for groceries `brand?`, `packSize?`, `mrp?`.
The item's `itemType` follows the store: restaurants create `FOOD`, Kiranas create `GROCERY`. Grocery listings form the "virtual warehouse": customers see products listed by any Kirana within 2 km, and whichever nearby Kirana claims the order fulfils it.

## Admin: `/admin` (ROLE_ADMIN)

### Sign-in: `/admin/auth` (public)
There is no admin sign-up. The first admin is created on startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` (and optional `ADMIN_NAME`) if that email has no admin yet.
| Method | Path | Body | Status |
|---|---|---|---|
| POST | `/login` | `identifier` (email, or phone if set), `password` → tokens with `role: ADMIN` | ✅ |
| POST | `/refresh` · `/logout` | `refreshToken` | ✅ |

### Reviews
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/agents?status=UNDER_REVIEW&page&size` | partner profiles by KYC status | ✅ |
| POST | `/agents/{id}/approve` | under-review only (`409 NOT_UNDER_REVIEW`); all documents `VERIFIED`, `APPROVED`, thermal box assigned | ✅ |
| POST | `/agents/{id}/reject` `{reason}` | `REJECTED` with `rejectionReason` | ✅ |
| POST | `/agents/{id}/documents/{type}/reject` `{reason}` | document `REJECTED` with remarks; partner back to `PENDING_DOCUMENTS` to re-upload | ✅ |
| GET | `/merchants?status=PENDING&page&size` | store profiles by approval status | ✅ |
| POST | `/merchants/{id}/approve` | → `APPROVED`; returns the store profile | ✅ |
| POST | `/merchants/{id}/reject` `{reason}` | → `REJECTED`, store closed | ✅ |

---

## Files (local storage)
| Method | Path | Notes | Status |
|---|---|---|---|
| PUT | `/files/upload?key&expires&contentType&sig` | public, authorised by the signed link only | ✅ |
| GET | `/files/{key}` | admins: any file; partners: only their own `agents/{id}/...` | ✅ |

## Real-time (STOMP over WebSocket)
- Endpoint: `ws://localhost:8080/ws`. The CONNECT frame must carry the header `Authorization: Bearer <accessToken>`.
- Only `/user/...` subscriptions are allowed; each app subscribes to its own queue:

| App | Subscribe to | Types |
|---|---|---|
| Customer | `/user/queue/tracking` | `ORDER_STATUS`, `RIDER_LOCATION`, `STOPS_UPDATED`, `PAYMENT_CONFIRMED`, `PAYMENT_FAILED` |
| Rider | `/user/queue/route-updates` | `OFFER`, `OFFER_WITHDRAWN`, `STOP_ADDED`, `ROUTE_UPDATED` |
| Merchant | `/user/queue/merchant-events` | `NEW_ORDER`, `ORDER_CANCELLED`, `BROADCAST_OFFER`, `BROADCAST_TAKEN`, `RIDER_ARRIVING`, `ORDER_PICKED_UP` |

- Rider → server: `SEND /app/agent/location` with the same body as `POST /agent/me/location`; errors come back on `/user/queue/errors`.

### Envelope
Every message on every queue has the same shape; switch on `type`, read `data`:
```json
{ "type": "OFFER", "sentAt": "2026-10-08T13:20:00Z", "data": { ... } }
```

### Customer messages (`/user/queue/tracking`)
`ORDER_STATUS`: sent on every status change; `data` is the full tracking snapshot (same as `GET /customer/orders/{id}/tracking`).
```json
{ "type": "ORDER_STATUS", "sentAt": "2026-10-08T13:20:00Z",
  "data": { "orderId": "6f1c…", "status": "PICKING", "agentLocation": { "lat": 18.5204, "lng": 73.8567 },
            "etaMinutes": 14, "updatedAt": "2026-10-08T13:20:00Z" } }
```
`RIDER_LOCATION`: every time the rider carrying the order sends a location.
```json
{ "type": "RIDER_LOCATION", "sentAt": "…", "data": { "orderId": "6f1c…", "location": { "lat": 18.5211, "lng": 73.8570 }, "etaMinutes": 12, "updatedAt": "…" } }
```

`STOPS_UPDATED`: any stop of the order changed (rider assigned, arrived, picked up, skipped, missing items marked). Carries the stops, the missing items and the bill after refunds, so a refund shows up live.
```json
{ "type": "STOPS_UPDATED", "sentAt": "…",
  "data": { "orderId": "6f1c…", "status": "PICKING",
            "stops": [ { "stopId": "e2d4…", "type": "KIRANA", "name": "Sharma Kirana", "status": "ARRIVED", "completedAt": null }, … ],
            "missingItems": [ { "itemId": "41b8…", "name": "Brown Bread", "quantity": 1, "refundAmount": 45.00 } ],
            "bill": { "foodSubtotal": 0.00, "grocerySubtotal": 165.00, "deliveryFee": 0.00, "convenienceFee": 10.00, "taxes": 0.00,
                      "discount": 0.00, "total": 175.00, "refunds": 45.00, "netTotal": 130.00 } } }
```

### Rider messages (`/user/queue/route-updates`)
`OFFER`: `data` is the same object as an item of `GET /agent/offers`.
```json
{ "type": "OFFER", "sentAt": "…",
  "data": { "offerId": "a3e0…", "orderIds": ["6f1c…"], "stops": [ … ], "totalDistanceMeters": 2140,
            "estimatedEarnings": 40.00, "expiresAt": "2026-10-08T13:20:30Z" } }
```
`OFFER_WITHDRAWN`: remove the offer card. `reason` is `EXPIRED`, `TAKEN` (batched into another rider's route) or `CANCELLED` (order cancelled).
```json
{ "type": "OFFER_WITHDRAWN", "sentAt": "…", "data": { "offerId": "a3e0…", "orderId": "6f1c…", "reason": "EXPIRED" } }
```
`STOP_ADDED`: a batching injection; play the haptic flash.
```json
{ "type": "STOP_ADDED", "sentAt": "…",
  "data": { "injectionId": "c19d…", "message": "Route Optimized: +1 Stop added nearby (+₹15 earned)", "bonusAmount": 15.00,
            "updatedRoute": { "routeId": "…", "locked": true, "stops": [ … ], "remainingDistanceMeters": 1830, "estimatedCompletionAt": "…" } } }
```
`ROUTE_UPDATED`: the route changed without the rider acting (late Kirana stop, cancelled order); `data` is the same as `GET /agent/route` (empty `stops` when the route is finished).

### Merchant messages (`/user/queue/merchant-events`)
All of these come from domain events after they commit; nothing is sent from a controller.

`NEW_ORDER` (restaurant): only once the order is `CONFIRMED` (payment held/captured, food status `NEW`), never at checkout while `PENDING`. `data` is the order card of `GET /merchant/orders`.
```json
{ "type": "NEW_ORDER", "sentAt": "…",
  "data": { "orderId": "6f1c…", "status": "NEW", "items": [ { "name": "Veg Biryani", "packSize": null, "quantity": 1 } ],
            "amount": 180.00, "prepDeadline": null, "riderName": null, "riderEta": null } }
```
`ORDER_CANCELLED` (restaurant, and the Kirana if one claimed it):
```json
{ "type": "ORDER_CANCELLED", "sentAt": "…", "data": { "orderId": "6f1c…", "reason": "Changed my mind" } }
```
`BROADCAST_OFFER` (Kirana): same object as an item of `GET /merchant/broadcasts`; hide it at `expiresAt`.
```json
{ "type": "BROADCAST_OFFER", "sentAt": "…",
  "data": { "broadcastId": "b7a2…", "orderId": "6f1c…", "items": [ { "name": "Toned Milk", "packSize": "500 ml", "quantity": 2 } ],
            "payoutAmount": 60.00, "distanceMeters": 210, "expiresAt": "2026-10-08T13:21:00Z" } }
```
`BROADCAST_TAKEN` (every targeted Kirana except the winner): `reason` is `CLAIMED`, `EXPIRED` or `CANCELLED`.
```json
{ "type": "BROADCAST_TAKEN", "sentAt": "…", "data": { "broadcastId": "b7a2…", "orderId": "6f1c…", "reason": "CLAIMED" } }
```
`RIDER_ARRIVING`: the rider tapped **Arrived** at this store.
```json
{ "type": "RIDER_ARRIVING", "sentAt": "…",
  "data": { "orderId": "6f1c…", "stopId": "e2d4…", "riderName": "Ravi", "riderPhone": "+919812345678", "vehicleNumber": "MH12AB1234", "arrivedAt": "…" } }
```
`ORDER_PICKED_UP`: the rider's QR scan completed this store's stop; close the order card.
```json
{ "type": "ORDER_PICKED_UP", "sentAt": "…", "data": { "orderId": "6f1c…", "stopId": "e2d4…", "pickedUpAt": "…" } }
```

## Internal engines (no HTTP endpoints)
| Class | What it does | Status |
|---|---|---|
| `dispatch/DispatchOrchestrator` | reacts to order events after commit (async): starts the broadcast (combined orders wait `kirana-broadcast-combined-delay`, 2 min), offers food orders to the nearest rider at once, grocery-only orders once claimed; inserts late Kirana stops; postpones unstarted broadcasts on kitchen delays; cleans up on cancel | ✅ |
| `dispatch/KiranaBroadcastService` | 5 nearest open Kiranas within 500 m, 60 s to claim, then the next ring (1 km); after the last round the groceries are `UNFULFILLED` (grocery-only orders are cancelled, combined orders refund the groceries to the wallet) | ✅ |
| `dispatch/DispatchScheduler` | poller on every instance (`app.dispatch.poll-interval-ms`): opens/expands due broadcast rounds, expires offers, retries orders waiting for a rider; each row in its own transaction with `FOR UPDATE SKIP LOCKED` | ✅ |
| `batching/CoLinearBatchingEngine` | for a claimed grocery-only order: ACTIVE riders near the Kirana (H3), whose remaining route passes within 500 m of both the Kirana and the customer; every insertion that keeps existing stops in order is simulated and the cheapest one adding ≤ 4 min wins. The order becomes `BATCHED`, the rider earns ₹15 and gets the "+1 stop" push. Otherwise normal dispatch. The maths is in the pure `CoLinearBatchingAlgorithm` | ✅ |
| `routing/RoutingClient` | `HaversineRoutingClient` (straight line × 1.3 at 20 km/h) by default; `OsrmRoutingClient` (`/route` + `/table`) with the `osrm` profile and `OSRM_BASE_URL` | ✅ |
| `spatial/SpatialIndexService` | H3 cells (resolution 9) and the disk covering a radius | ✅ |

The blueprint constants (2 km radius, 500 m corridor, 4 min, 60 s, ₹150, ₹15) live under `app.ops.*` in `application.properties`.
