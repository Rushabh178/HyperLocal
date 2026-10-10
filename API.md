# HyperLocal API map

Base URL: `http://localhost:8080/api/v1`

**Status:** ✅ = working now · 🚧 = stub that returns `501 NOT_IMPLEMENTED` (none left). Rarer cases that are not handled yet are marked `TODO(edge-case)` in the code.

## Running locally
Needs Java 25+, PostgreSQL with PostGIS, and (for tests) Docker.
1. `cp .env.example .env` and fill it in; `.env` is git-ignored and loaded automatically, real environment variables win.
2. `./mvnw spring-boot:run`. For local development uncomment `spring.profiles.active=dev` in your `.env` or set `SPRING_PROFILES_ACTIVE=dev` (`dev` is never on by default; see Profiles below). Hibernate creates/updates the tables, then `db/indexes.sql` adds the GiST and partial unique indexes.
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
| `FIREBASE_CREDENTIALS` | with `fcm` | application-default credentials | path to the Firebase service-account JSON |
| `STORAGE_DIR` / `PUBLIC_BASE_URL` | no | `./uploads` / `http://localhost:8080` | local file storage for KYC uploads |

Profiles (`SPRING_PROFILES_ACTIVE`): there is no default. `dev` must be set explicitly for local development: fake payment gateway that honours `X-Fake-Payment`, logged pushes. Without `dev` the fakes do not exist, `X-Fake-Payment` is ignored, and startup fails unless a real gateway and push sender are configured (e.g. `prod,razorpay,fcm`). `razorpay` = Razorpay gateway; `fcm` = real push notifications; `osrm` = OSRM routing (otherwise haversine).

### Conventions
- **Money** is rupees as a JSON number with exactly two decimals, always computed by the server: `"deliveryFee": 25.00`. Show it as is; the apps never calculate money. (Ratings are plain numbers like `4.5`.)
- **Lists** are always `{ "items": [ … ], "page": 0, "size": 20, "totalItems": 57 }`, pages are 0-based (`?page=0&size=20`). Short lists that are not paged (offers, broadcasts, addresses) use the same shape as a single page.
- Concurrent edits of the same order or payment return `409 CONCURRENT_UPDATE`; just retry.

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

### Push notifications (all three apps)
After every sign-in, and whenever the push SDK hands out a new token, register the phone:
```
POST /api/v1/customer/devices      (or /api/v1/agent/devices, /api/v1/merchant/devices)
Authorization: Bearer <accessToken>
{ "token": "dQw4w9WgXcQ:APA91b…", "platform": "ANDROID" }        // ANDROID | IOS | WEB
→ 204
```
It is an **upsert on the token**: if another account registered the same token before (same phone, different login), the token moves to the account that registered last.
Unregister by logging out with the token; there is no separate DELETE:
```
POST /api/v1/customer/auth/logout   (same for agent / merchant)
{ "refreshToken": "kq3…", "deviceToken": "dQw4w9WgXcQ:APA91b…" }      // deviceToken optional
→ 204
```
Every push carries the WebSocket envelope as FCM **data** (all values are strings): `type`, `sentAt`, and `data` (the JSON of the WebSocket `data`).

| Type | Who | Shown as |
|---|---|---|
| `OFFER` | rider | **data-only, high priority**, TTL ends at the offer's `expiresAt` (a stale offer is never delivered late) |
| `BROADCAST_OFFER` | Kirana | **data-only, high priority**, TTL ends at `expiresAt` |
| `STOP_ADDED` | rider | notification "+1 stop added" / the injection message |
| `ORDER_STATUS` | customer | notification "Order update" / e.g. "Your order is on the way" |
| `GROCERIES_UNFULFILLED` | customer | notification "Groceries unavailable" / "We couldn't get your groceries. ₹60.00 is back in your wallet; your food is on its way." (the `ORDER_STATUS` sent with it is WebSocket-only) |
| `NEW_ORDER`, `ORDER_CANCELLED`, `BROADCAST_TAKEN`, `RIDER_ARRIVING`, `ORDER_PICKED_UP` | merchant | notification with a title and body |

Other types are WebSocket-only. Example FCM data of a rider offer:
```json
{ "type": "OFFER", "sentAt": "2026-10-08T13:20:00Z",
  "data": "{\"offerId\":\"a3e0…\",\"orderIds\":[\"6f1c…\"],\"estimatedEarnings\":40.00,\"expiresAt\":\"2026-10-08T13:20:30Z\", …}" }
```
In the `dev` profile pushes are only logged (`[DEV PUSH] …`); the `fcm` profile sends them through Firebase (`FIREBASE_CREDENTIALS` = path to the service-account JSON, or Google application-default credentials).

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
| POST | `/logout` | `refreshToken, deviceToken?` (removes the push token too) | ✅ |
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
| GET | `/` | `{items, page, size, totalItems}`, default address first | ✅ |
| POST | `/` | see body below; the first address becomes the default; max 10 | ✅ |
| GET | `/{id}` | | ✅ |
| PUT | `/{id}` | same body as POST | ✅ |
| PUT | `/{id}/default` | | ✅ |
| DELETE | `/{id}` | soft delete; the next address is promoted to default | ✅ |
| POST | `/serviceability` | `{lat,lng}` → `{serviceable, serviceRadiusMeters, nearbyRestaurants, nearbyKiranaStores, message, restaurantRadiusMeters, kiranaRadiusMeters}`; counts approved restaurants within 6 km and Kiranas within 2 km (`serviceRadiusMeters` = the wider one, 6000) | ✅ |

Address body: `label` (HOME/WORK/OTHER), `customLabel?`, `recipientName?`, `recipientPhone?`, `houseNumber`, `buildingName?`, `street?`, `landmark?`, `area?`, `city`, `state`, `pincode`, `location {lat,lng}`, `deliveryInstructions?`, `makeDefault?`. The server stores a PostGIS point plus its H3 cell.

### Delivery range and fee
Distances are measured from the customer's **selected delivery address** to the store (`app.delivery.*`):
- **Visibility** uses the straight line (H3 cells first, then PostGIS): restaurants within **6 km** (`restaurant-radius-km`), Kiranas within **2 km** (`kirana-radius-km`). Groceries (catalogue, broadcast targets) always come from Kiranas within 2 km of the customer.
- **The fee** uses the **road distance** (`RoutingClient`; by default straight line × 1.3) of the order's farthest pickup, which is the restaurant (Kiranas are always inside the free zone). It is shown as `distanceKm` (one decimal) next to `deliveryFee`.

| Road distance | Delivery fee |
|---|---|
| up to 2 km (`free-delivery-km`) | free; but a grocery-only cart under ₹150 pays ₹25 unless it adds a meal (the meal nudge) |
| 2–3 km · 3–4 km · 4–5 km · 5–6 km (`fee-slabs`) | ₹15 · ₹25 · ₹35 · ₹45, whatever the cart holds (no waiver) |
| over 6 km by road (store still within 6 km straight line) | ₹45, the last slab (`TODO(edge-case)`) |

So the restaurant cards and the menu can say "Free delivery" (`deliveryFee: 0.00`) or "₹25 delivery". The cart and checkout recompute the fee for the chosen address; checkout stores the distance with the order.

### Catalog: `/catalog` (public GET)
Pass the selected address's pin as `lat`/`lng`. Restaurants are limited to approved stores within 6 km, groceries to Kiranas within 2 km (see above).
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/restaurants?lat&lng&cuisine&page&size` | open restaurants first, then nearest; closed ones have `open:false`; `etaMinutes` = default prep (15) + travel time; `rating` is null until rated; `distanceMeters` (straight line), `distanceKm` (road) and `deliveryFee` (example below) | ✅ |
| GET | `/restaurants/{id}/menu?lat&lng` | grouped by category; sold-out dishes have `available:false`. Header: `isOpen` (same as `open`), `rating` (null until rated), `ratingCount`, and with the customer's pin `distanceKm` (road), `etaMinutes` and `deliveryFee` (all null without `lat`/`lng`). Example below | ✅ |
| GET | `/groceries/categories?lat&lng&page&size` | categories of that same catalogue with distinct product counts, e.g. `{"items":[{"category":"Bakery","itemCount":1},{"category":"Dairy","itemCount":2}],"page":0,"size":50,"totalItems":2}` | ✅ |
| GET | `/groceries?lat&lng&q&category&page&size` | "virtual warehouse": products listed by **open** Kiranas within 2 km, one row per product (name + pack size) at the cheapest price; empty when no Kirana is open nearby | ✅ |
| GET | `/search?lat&lng&q` | `{restaurants (≤10, name or cuisine), dishes (≤20), groceries (≤20)}`; each dish carries what's needed to add it: `{itemId, restaurantId, restaurantName, name, description, price, veg, available, imageUrl}` (`id` = `itemId`) | ✅ |

Restaurant card:
```json
{ "id": "0c5e…", "name": "Spice Hub", "cuisines": ["North Indian", "Biryani"], "rating": 4.3, "distanceMeters": 2400,
  "etaMinutes": 24, "open": true, "imageUrl": null, "distanceKm": 3.1, "deliveryFee": 25.00 }
```
Menu:
```json
GET /api/v1/catalog/restaurants/0c5e…/menu?lat=18.5204&lng=73.8567
{ "restaurantId": "0c5e…", "restaurantName": "Spice Hub", "open": true, "isOpen": true,
  "distanceKm": 3.1, "etaMinutes": 24, "rating": 4.3, "ratingCount": 18, "deliveryFee": 25.00,
  "categories": [ { "name": "Biryani", "items": [ { "id": "9b3c…", "name": "Veg Biryani", "description": "…", "price": 180.00,
                                                    "veg": true, "available": true, "imageUrl": null } ] } ] }
```
Dish search result:
```json
"dishes": [ { "id": "9b3c…", "itemId": "9b3c…", "restaurantId": "0c5e…", "restaurantName": "Spice Hub", "name": "Veg Biryani",
              "description": "…", "price": 180.00, "veg": true, "available": true, "imageUrl": null } ]
```
→ `POST /customer/cart/items {"itemType":"FOOD","itemId":"9b3c…","restaurantId":"0c5e…","quantity":1}`

### Unified cart: `/customer/cart`
One persistent cart per customer: food from **one** restaurant plus any groceries. Prices are always read from the live catalogue.
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/` | `{restaurantId, foodItems, groceryItems, deliveryAddressId, bill, nudge, deliveryDistanceKm, freeDeliveryKm}` (empty cart if none). The bill is priced for the cart's address, else the default address; `deliveryDistanceKm` is the road km the fee used (null with no food or no address) | ✅ |
| POST | `/items` `{itemType FOOD/GROCERY, itemId, restaurantId?, quantity}` | `restaurantId` required for FOOD; food from another restaurant **replaces** the food section; same item again adds to the quantity (max 20); `409 STORE_CLOSED` if the store is closed | ✅ |
| PATCH | `/items/{cartItemId}` `{quantity}` | 0 removes the line | ✅ |
| DELETE | `/items/{cartItemId}` | returns the cart | ✅ |
| DELETE | `/` | 204 | ✅ |
| PUT | `/address` `{addressId}` | must be yours, within 6 km of the restaurant and 2 km of an open Kirana if there are groceries (`400 ADDRESS_NOT_SERVICEABLE`; the message says which part can't come, e.g. "Food from Spice Hub can't be delivered to this address: it's more than 6 km away. Your groceries can." or "Groceries can't be delivered to this address: no grocery store is open within 2 km."). Returns the cart repriced for that address | ✅ |
| GET | `/nudges` | same object as `cart.nudge`; `message:null` and no meals when it doesn't apply | ✅ |

Bill: `deliveryFee` by distance (see "Delivery range and fee": free up to 2 km by road, where a grocery-only cart under ₹150 pays ₹25; slabs beyond) · `convenienceFee` ₹10 flat · `taxes` 5% GST on food only · `discount` 0 for now.
Nudge: a grocery-only cart under ₹150 gets `{groceryTotal, threshold, message, suggestedMeals}` — the 5 priciest available dishes from open restaurants **within 2 km** (`meal-nudge-radius-km`) of the cart's address (or the default address), so adding one keeps delivery free. Each meal carries what's needed to add it:
```json
"nudge": { "groceryTotal": 30.00, "threshold": 150.00, "message": "Add a meal from a restaurant nearby and get free delivery",
           "suggestedMeals": [ { "itemId": "9b3c…", "restaurantId": "0c5e…", "restaurantName": "Spice Hub", "name": "Veg Thali",
                                 "description": null, "price": 150.00, "veg": true, "imageUrl": null } ] }
```
→ `POST /customer/cart/items {"itemType":"FOOD","itemId":"9b3c…","restaurantId":"0c5e…","quantity":1}`

### Orders: `/customer/orders`
| Method | Path | Notes | Status |
|---|---|---|---|
| POST | `/checkout` `{addressId, paymentMethod UPI/WALLET}` + optional `Idempotency-Key` header | → 201 `{orderId, status, payableAmount, upiIntentUrl, payment}` (see "Paying" below). Re-validates and re-prices the cart, freezes it into an order, takes the payment, clears the cart. Same key again returns the first order. Errors: `400 CART_EMPTY`, `400 ADDRESS_NOT_SERVICEABLE`, `409 ITEM_UNAVAILABLE`, `409 STORE_CLOSED`, `409 INSUFFICIENT_WALLET_BALANCE` | ✅ |
| GET | `/?status&page&size` | newest first; `{orderId, status, hasFood, hasGroceries, total, rated, createdAt}` | ✅ |
| GET | `/{id}` | items, `bill` (after refunds), `missingItems`, address, `rider`, `deliveryOtp` (4 digits, shown while the order is active), `cancellable`, `stops`, `rated` + `rating` — example below | ✅ |
| GET | `/{id}/timeline` | unified Kirana → Restaurant → Home line | ✅ |
| GET | `/{id}/tracking` | `{orderId, status, agentLocation, etaMinutes, stops, updatedAt}` snapshot; live updates over WebSocket | ✅ |
| POST | `/{id}/cancel` `{reason}` | only before pickup (`409 ORDER_NOT_CANCELLABLE` / `ORDER_ALREADY_PICKED_UP`); releases the UPI hold or refunds a wallet payment to the wallet | ✅ |
| POST | `/{id}/rating` `{deliveryRating?, restaurantRating?, kiranaRating?, comment?}` | completed orders only, once (`409 ALREADY_RATED`); updates the stores' average rating | ✅ |

Order details:
```json
{ "orderId": "6f1c…", "status": "PICKING",
  "foodItems": [ { "cartItemId": "…", "itemType": "FOOD", "itemId": "…", "name": "Veg Thali", "unitPrice": 150.00, "quantity": 1, "lineTotal": 150.00, "zone": "HOT" } ],
  "groceryItems": [],
  "bill": { "foodSubtotal": 150.00, "grocerySubtotal": 0.00, "deliveryFee": 0.00, "convenienceFee": 10.00, "taxes": 7.50,
            "discount": 0.00, "total": 167.50, "refunds": 0.00, "netTotal": 167.50 },
  "missingItems": [],
  "deliveryAddress": { … },
  "rider": { "name": "Ravi", "phone": "+919812345678", "vehicleNumber": "MH12AB1234", "location": { "lat": 18.52, "lng": 73.85 } },
  "deliveryOtp": "4821", "cancellable": true,
  "stops": [ … ], "rated": false, "rating": null, "createdAt": "…" }
```
`rider` is null until someone accepts; the phone is the real number for now (masking comes later). `missingItems[]` is `{itemId, name, quantity, refundAmount}`.
`rated` says whether the customer has rated the order; once rated, `rating` is `{deliveryRating, restaurantRating, kiranaRating, comment, createdAt}` (stars 1-5, null for a part the order didn't have or wasn't rated).

**Stops** (order details, tracking, `ORDER_STATUS` and `STOPS_UPDATED`): the rider's real stops once someone has the order; before that the planned stops with `stopId: null`.
`status` is `PENDING`, `ARRIVED`, `PICKED_UP` (Kirana / restaurant), `DELIVERED` (customer) or `SKIPPED`.
Every stop has `lat`, `lng` and `address` (the store's, or the delivery address for `CUSTOMER`), so the map can show all of them. They are null only for a Kirana stop while no shop has claimed the groceries yet ("Finding a store nearby") and for the "Groceries unavailable" stop.
```json
"stops": [
  { "stopId": "e2d4…", "type": "KIRANA",     "name": "Sharma Kirana", "status": "PICKED_UP", "completedAt": "2026-10-08T13:31:02Z",
    "lat": 18.5219, "lng": 73.8552, "address": "Sharma Kirana, Shivajinagar, Pune" },
  { "stopId": "9a01…", "type": "RESTAURANT", "name": "Spice Hub",     "status": "ARRIVED",   "completedAt": null,
    "lat": 18.5248, "lng": 73.8524, "address": "Spice Hub, Shivajinagar, Pune" },
  { "stopId": "77bc…", "type": "CUSTOMER",   "name": "Home",          "status": "PENDING",   "completedAt": null,
    "lat": 18.5204, "lng": 73.8567, "address": "Flat 12, Shanti Apartments, FC Road, Shivajinagar, Pune, Maharashtra 411005" }
]
```
**Bill**: `{foodSubtotal, grocerySubtotal, deliveryFee, convenienceFee, taxes, discount, total, refunds, netTotal}`. `refunds` is what has already gone back to the customer (missing items, unavailable groceries, cancellation) and `netTotal = total - refunds`; in the cart `refunds` is `0.00` and `netTotal = total`.

**Paying** (UPI intent only; there is no UPI collect / `upiVpa`):
```
POST /api/v1/customer/orders/checkout
Idempotency-Key: 4c1d…
{ "addressId": "a71e…", "paymentMethod": "UPI" }
→ 201 { "orderId": "6f1c…", "status": "PENDING", "payableAmount": 167.50,
        "upiIntentUrl": "upi://pay?pa=hyperlocal@bank&pn=HyperLocal&am=167.50&cu=INR&tr=…",
        "payment": { "paymentId": "…", "orderId": "6f1c…", "amount": 167.50, "status": "HOLD_PENDING", "upiIntentUrl": "upi://…", "expiresAt": "…" } }
```
1. If `upiIntentUrl` is present, open it in the UPI app. It is **null** when there is nothing to approve: the order is already `CONFIRMED` (wallet payment, or the dev gateway approved it) or the hold failed.
2. Wait for `PAYMENT_CONFIRMED` or `PAYMENT_FAILED` on `/user/queue/tracking`; poll `GET /customer/orders/{id}` as a fallback.
3. After `PAYMENT_FAILED` the order stays `PENDING`; retry with `POST /payments/orders/{id}/upi-hold` (or cancel it).

**Order status** (state machine; illegal moves return `409 ILLEGAL_STATE_TRANSITION`):
`PENDING → CONFIRMED → (BATCHED →) PICKING → OUT_FOR_DELIVERY → COMPLETED`, and `CANCELLED` from anything before pickup.
With the fake gateway (`dev` profile) and wallet payments the order is `CONFIRMED` right away; with a real gateway it stays `PENDING` until the payment webhook confirms the hold.
Each part of the order also has its own status (`foodStatus` / `groceryStatus` internally): `AWAITING_PAYMENT → NEW → ACCEPTED ⇄ DELAYED → READY → PICKED_UP` for food, `AWAITING_PAYMENT → BROADCASTING → CLAIMED → READY → PICKED_UP` (or `UNFULFILLED` when no Kirana claims it, nothing is available, or the drop happens without them) for groceries.

**Food + groceries in one order (mixed orders):**
- **Dispatch waits for the groceries.** The restaurant gets its ticket and the Kiranas the broadcast as usual, but no rider is offered the order until the groceries are `CLAIMED`/`READY` (the route is then Kirana → Restaurant → Home) or `UNFULFILLED` (the food goes alone). Food-only and grocery-only orders dispatch as before.
- **Groceries that weren't picked up are never paid for.** When the grocery part becomes `UNFULFILLED` (the broadcast found nobody, nothing was available at the Kirana, or the drop completed without them), the customer gets the groceries' share back in the **wallet**: their subtotal, their GST (none, groceries are tax-inclusive) and any delivery fee the food alone wouldn't have paid. The convenience fee is per order and stays. If nothing else remains the order is cancelled instead (hold released or everything refunded).
- One refund per order part, whichever path fires first (wallet reference `refund:groceries:<orderId>`); later paths are no-ops.
- The customer sees it in `bill.refunds` / `bill.netTotal`, a `SKIPPED` Kirana stop named "Groceries unavailable", the `ORDER_STATUS` + `STOPS_UPDATED` messages and a `GROCERIES_UNFULFILLED` message (also a push):
```json
{ "type": "GROCERIES_UNFULFILLED", "sentAt": "…",
  "data": { "orderId": "6f1c…", "refundAmount": 60.00, "reason": "No store nearby had these items",
            "bill": { "foodSubtotal": 180.00, "grocerySubtotal": 60.00, "deliveryFee": 0.00, "convenienceFee": 10.00, "taxes": 9.00,
                      "discount": 0.00, "total": 259.00, "refunds": 60.00, "netTotal": 199.00 } } }
```

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
Money flow: the hold is captured at the first pickup (Kirana QR / restaurant hand-over), which also records what each store is owed (a refund before any pickup captures it first). Missing items and unavailable groceries are refunded to the **wallet**; cancelling releases an uncaptured hold or refunds the rest to the wallet.

Gateways (`PaymentGateway`): the fake gateway exists **only in the `dev` profile** (without `razorpay`). Its outcome is `payments.fake.default-outcome` (`SUCCESS`), and a checkout or retry request can choose it with the header `X-Fake-Payment: SUCCESS | PENDING | FAIL`. Without `dev` there is no fake gateway, the header is ignored, and the app refuses to start unless a real gateway is configured. `PENDING` waits for a webhook `{"eventId","reference","type":"HOLD_CONFIRMED"|"HOLD_FAILED"}` signed as hex HMAC-SHA256 of the body with `PAYMENT_WEBHOOK_SECRET`. A Razorpay skeleton runs with the `razorpay` profile (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`).

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
| GET | `/offers` | open offers `{items:[{offerId, orderIds, stops, totalDistanceMeters, estimatedEarnings, expiresAt}], …}` (also pushed as `OFFER`); `estimatedEarnings` includes the distance pay of the planned route | ✅ |
| POST | `/offers/{id}/accept` | first accept wins; `409 OFFER_NO_LONGER_AVAILABLE` / `409 RIDER_BUSY`. Rider → `ACTIVE`, order → `PICKING`; returns the locked route | ✅ |
| POST | `/offers/{id}/reject` `{reason?}` | 204; the order goes to the next rider straight away | ✅ |
| GET | `/route` | `{routeId, locked:true, stops[{stopId, sequence, type, name, address, location, orderIds, items, state, eta}], remainingDistanceMeters, estimatedCompletionAt}`; `404` when no active route | ✅ |
| POST | `/route/stops/{stopId}/arrived` | pickup step 1 (also used at the customer); tells the store `RIDER_ARRIVING` | ✅ |
| POST | `/route/stops/{stopId}/deficient` `{itemIds:[…], reason?}` | pickup step 2, **Kirana only, optional**: mark missing items (stop item ids). Only after Arrived (`409 NOT_ARRIVED`) and before the scan (`409 STOP_ALREADY_COMPLETED`) | ✅ |
| POST | `/route/stops/{stopId}/scan-qr` `{qrPayload}` | pickup step 3 at **every** store: the store's QR must match (`400 QR_WRONG_STORE` / `QR_INVALID` / `QR_EXPIRED` / `QR_REQUIRED`), needs Arrived (`409 NOT_ARRIVED`). Completes the stop. Kirana: the shop is settled for the available items only and the missing ones are refunded to the customer's wallet (nothing available → stop skipped, groceries `UNFULFILLED`: a grocery-only order is cancelled, a mixed one refunds the groceries' share). Restaurant: food picked up, restaurant settled | ✅ |
| POST | `/route/stops/{stopId}/kirana-qr` · `/route/stops/{stopId}/picked-up` | aliases of `scan-qr` (same `{qrPayload}` body) | ✅ |
| POST | `/route/stops/{stopId}/items/{itemId}/deficient` | alias of `deficient` for one item | ✅ |
| POST | `/orders/{orderId}/deliver` `{deliveryOtp}` | 4-digit code from the customer; `400 DELIVERY_OTP_INVALID`. Completes the order, books earnings, frees the rider when the route is empty. Groceries not picked up by then become `UNFULFILLED` and are refunded (see mixed orders) | ✅ |
| POST | `/route/injections/{id}/ack` | 204 | ✅ |
| GET | `/deliveries?page&size` | `{orderId, batched, stops, earnings, completedAt}` | ✅ |
| GET | `/earnings?period=TODAY/WEEK/MONTH&page&size` | Indian time; base ₹30 per order + ₹10 per extra pickup + batching incentives + `distancePay`, `tripCount`, today's `onlineMinutes` and paged `payouts` (example below) | ✅ |
| GET | `/earnings/payouts?page&size` | | ✅ |

Pickup sequence at every store: **Arrived → mark missing items (Kirana only, optional) → Scan QR**.
```
POST /api/v1/agent/route/stops/e2d4…/arrived
POST /api/v1/agent/route/stops/e2d4…/deficient   {"itemIds": ["41b8…"], "reason": "Out of stock"}
POST /api/v1/agent/route/stops/e2d4…/scan-qr     {"qrPayload": "HLQR1.0c5e….1791477000.Zm9v…"}
→ 200 the route, with stop e2d4… "state": "COMPLETED"
```
Earnings (the merchant version has the same style):
```json
GET /api/v1/agent/earnings?period=WEEK
{ "period": "WEEK", "from": "2026-10-05", "to": "2026-10-08", "tripCount": 23, "batchedInjections": 4,
  "basePay": 690.00, "multiStopBonus": 80.00, "incentives": 60.00, "distancePay": 112.50, "total": 942.50, "onlineMinutes": 312,
  "payouts": { "items": [ { "payoutId": "…", "amount": 1200.00, "status": "PAID", "utr": "UTR123", "paidAt": "…" } ], "page": 0, "size": 20, "totalItems": 1 } }
```
`onlineMinutes` is always for today (Indian time), whatever the period.
Distance pay: `app.rider.per-km-rate` (₹5) for every km of the order's route (its pickups, then the drop, by road) beyond `app.rider.distance-pay-after-km` (2 km), fractions included, booked at delivery as a `DISTANCE` earning (`agent_earnings.earning_type`) and included in `total`.

Stops must be worked in order (Linear Route Locking): acting on any stop but the current one returns `409 STOP_LOCKED`. One order's stops always run Kirana → Restaurant → Customer. A mixed order is only offered once its groceries are claimed (or given up), so the Kirana stop is there from the start; for an order dispatched before that rule, a late Kirana stop is still inserted before the restaurant.

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
| POST | `/refresh` · `/logout` | `refreshToken` · `refreshToken, deviceToken?` | ✅ |
| POST | `/password/forgot` · `/password/reset` | `phone` · `phone, otp, newPassword` | ✅ |

Sign-up body: `type` (KIRANA/RESTAURANT), `storeName`, `ownerName`, `phone`, `password`, `address`, `pincode`, `location {lat,lng}`, `fssaiNumber` (**required for RESTAURANT**, else `400 FSSAI_REQUIRED`), `gstin?`, `upiVpa?`, `cuisines?` (restaurants, up to 5), `imageUrl?`.
A new store is `verificationStatus: PENDING` until ops approves it, and cannot open before that (`403 MERCHANT_NOT_APPROVED`).

### Uploads, profile and documents
Uploads need a token, so they happen **after `verify-phone`**: sign up → verify phone → upload photo/documents → save them → wait for approval.
```
POST /api/v1/merchant/uploads
{ "purpose": "STORE_PHOTO", "contentType": "image/png" }       // DOCUMENT may also be application/pdf
→ 200 { "uploadUrl": "http://localhost:8080/api/v1/files/upload?key=public%2Fmerchants%2F0c5e…%2Fstore%2F9f2a….png&expires=…&contentType=image%2Fpng&sig=…",
        "fileKey": "public/merchants/0c5e…/store/9f2a….png",
        "fileUrl": "http://localhost:8080/api/v1/files/public/merchants/0c5e…/store/9f2a….png",
        "publicRead": true, "expiresAt": "…" }

PUT <uploadUrl>   (raw bytes, same Content-Type, no token, max 10 MB, within 15 min)  → 204
```
- `STORE_PHOTO` / `ITEM_PHOTO` live under `public/…` and anyone can `GET` them (customer catalogue). Save a store photo with `PATCH /merchant/me {"imageUrl": fileUrl}`, an item photo with `PUT /merchant/items/{id}` (`imageUrl`).
- `DOCUMENT` lives under `merchants/{id}/…` and only the store itself and admins can `GET` it. Save it with:
```
PUT /api/v1/merchant/me/documents/FSSAI_CERT
{ "documentNumber": "12345678901234", "fileUrl": "http://localhost:8080/api/v1/files/merchants/0c5e…/documents/77d1….pdf" }
→ 200 { "verificationStatus": "PENDING", "missingDocuments": ["SHOP_PHOTO", "OWNER_ID"],
        "documents": [ { "type": "FSSAI_CERT", "documentNumberMasked": "XXXXXXXXXX1234", "fileUrl": "…", "status": "UPLOADED", "remarks": null, "uploadedAt": "…" } ] }
```
Required documents: restaurants `FSSAI_CERT, SHOP_PHOTO, OWNER_ID`; Kiranas `SHOP_PHOTO, OWNER_ID`. `GST_CERT` and `BANK_PROOF` are optional.

### Store: `/merchant`
| Method | Path | Notes | Status |
|---|---|---|---|
| GET | `/me` | profile incl. `verificationStatus`, `rejectionReason`, `open`, `imageUrl`, `cuisines`, `openingHours`, `upiVpa` | ✅ |
| PATCH | `/me` | partial: `storeName?, ownerName?, imageUrl?, openingHours?, cuisines?` (restaurants), `upiVpa?` → profile | ✅ |
| POST | `/uploads` | `{purpose: DOCUMENT/STORE_PHOTO/ITEM_PHOTO, contentType}` → signed upload link (see "Uploads, profile and documents" above) | ✅ |
| GET | `/me/documents` | `{verificationStatus, missingDocuments, documents}` | ✅ |
| PUT | `/me/documents/{type}` | `{documentNumber?, fileUrl}`; types `FSSAI_CERT, GST_CERT, SHOP_PHOTO, OWNER_ID, BANK_PROOF`; `409 DOCUMENTS_LOCKED` once approved | ✅ |
| PUT | `/me/password` | `currentPassword, newPassword` | ✅ |
| PUT | `/me/store-status` | `{open}`; approved stores only | ✅ |
| GET | `/store-qr` | **every store** (Kirana and restaurant); signed payload `HLQR1.<merchantId>.<exp>.<sig>`, valid 15 min — show it as a QR and refresh before `expiresAt`. Response `{"qrPayload": "HLQR1.…", "expiresAt": "…"}` | ✅ |
| GET | `/broadcasts` | Kirana only. Open rounds this shop was pinged for: `{items:[{broadcastId, orderId, items[{name, packSize, quantity}], payoutAmount, distanceMeters, expiresAt}], …}` (a handful at most, one page) | ✅ |
| GET | `/earnings?period=TODAY/WEEK/MONTH&page&size` | same style as partner earnings: `{period, from, to, orderCount, total, payouts{items,page,size,totalItems}}`; `total` is what the store was settled for (one settlement per picked-up order) | ✅ |
| POST | `/broadcasts/{id}/claim` ("Items in Hand") | Kirana only. First tap wins (atomic): `{broadcastId, orderId, claimed, message}`; late or un-targeted taps get `claimed:false` | ✅ |
| GET | `/orders?status&page&size` | paginated. Restaurants see their kitchen tickets, Kiranas the orders they claimed: `{orderId, status, items, amount, prepDeadline, riderName, riderEta}` | ✅ |
| POST | `/orders/{id}/accept` `{prepMinutes 1-90}` | restaurant only; `NEW → ACCEPTED`, sets `prepDeadline` | ✅ |
| POST | `/orders/{id}/delay` `{additionalMinutes 1-60, reason?}` | restaurant only; `→ DELAYED`, pushes `prepDeadline` back and holds back the Kirana broadcast | ✅ |
| POST | `/orders/{id}/ready` | restaurant: food `ACCEPTED/DELAYED → READY`; Kirana: groceries `CLAIMED → READY` (its own claimed orders only, else `404`) | ✅ |
| GET | `/items?page&size` | paginated (`{items, page, size, totalItems}`), by category then name | ✅ |
| POST | `/items` | see body below → 201 | ✅ |
| PUT | `/items/{id}` | same body as POST | ✅ |
| PATCH | `/items/{id}/availability` | `{available}` | ✅ |


Merchant order `status`: `NEW, ACCEPTED, DELAYED, READY, PICKED_UP, CANCELLED` (`PREPARING` is accepted as a filter alias of `ACCEPTED`). A Kirana's claimed order shows as `ACCEPTED` until it calls `/ready`. Illegal moves return `409 ILLEGAL_STATE_TRANSITION`; a Kirana calling a restaurant-only endpoint gets `403 RESTAURANT_ONLY` (and vice versa `403 KIRANA_ONLY`).

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
| GET | `/merchants/{id}/documents` | the store's documents (same shape as `GET /merchant/me/documents`); open a file with `GET /files/{key}` using the admin token | ✅ |
| POST | `/merchants/{id}/approve` | → `APPROVED`, all documents `VERIFIED`; returns the store profile | ✅ |
| POST | `/merchants/{id}/reject` `{reason}` | → `REJECTED`, store closed | ✅ |

---

## Files (local storage)
| Method | Path | Notes | Status |
|---|---|---|---|
| PUT | `/files/upload?key&expires&contentType&sig` | public, authorised by the signed link only | ✅ |
| GET | `/files/{key}` | `public/…` (store and item photos): anyone, no token. Everything else: admins any file, partners only `agents/{id}/…`, stores only `merchants/{id}/…` | ✅ |

## Real-time (STOMP over WebSocket)
- Endpoint: `ws://localhost:8080/ws`. The CONNECT frame must carry the header `Authorization: Bearer <accessToken>`.
- Only `/user/...` subscriptions are allowed; each app subscribes to its own queue:

| App | Subscribe to | Types |
|---|---|---|
| Customer | `/user/queue/tracking` | `ORDER_STATUS`, `RIDER_LOCATION`, `STOPS_UPDATED`, `PAYMENT_CONFIRMED`, `PAYMENT_FAILED`, `GROCERIES_UNFULFILLED` |
| Rider | `/user/queue/route-updates` | `OFFER`, `OFFER_WITHDRAWN`, `STOP_ADDED`, `ROUTE_UPDATED` |
| Merchant | `/user/queue/merchant-events` | `NEW_ORDER`, `ORDER_CANCELLED`, `BROADCAST_OFFER`, `BROADCAST_TAKEN`, `RIDER_ARRIVING`, `ORDER_PICKED_UP` |

- Rider → server: `SEND /app/agent/location` with the same body as `POST /agent/me/location`; errors come back on `/user/queue/errors`.
- Scaling note: the broker is Spring's in-memory one, so a user only gets messages from the instance they are connected to. Before running more than one backend instance, switch to a relay broker (RabbitMQ STOMP) or Redis pub/sub (`TODO(scale)` in `WebSocketConfig`). Push notifications are not affected.

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
            "stops": [ { "stopId": "e2d4…", "type": "KIRANA", "name": "Sharma Kirana", "status": "ARRIVED", "completedAt": null,
                         "lat": 18.5219, "lng": 73.8552, "address": "Sharma Kirana, Shivajinagar, Pune" }, … ],
            "missingItems": [ { "itemId": "41b8…", "name": "Brown Bread", "quantity": 1, "refundAmount": 45.00 } ],
            "bill": { "foodSubtotal": 0.00, "grocerySubtotal": 165.00, "deliveryFee": 0.00, "convenienceFee": 10.00, "taxes": 0.00,
                      "discount": 0.00, "total": 175.00, "refunds": 45.00, "netTotal": 130.00 } } }
```

`PAYMENT_CONFIRMED` / `PAYMENT_FAILED`: the outcome of the payment after checkout (or a retry).
```json
{ "type": "PAYMENT_CONFIRMED", "sentAt": "…",
  "data": { "orderId": "6f1c…", "paymentId": "…", "paymentStatus": "HELD", "orderStatus": "CONFIRMED", "amount": 167.50, "message": "Payment received" } }
{ "type": "PAYMENT_FAILED", "sentAt": "…",
  "data": { "orderId": "6f1c…", "paymentId": "…", "paymentStatus": "FAILED", "orderStatus": "PENDING", "amount": 167.50,
            "message": "The payment didn't go through. Try again." } }
```

`GROCERIES_UNFULFILLED`: the groceries of a mixed order were given up and their share refunded; example under "Food + groceries in one order" above. Arrives together with `ORDER_STATUS` and `STOPS_UPDATED`.

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
| `dispatch/DispatchOrchestrator` | reacts to order events after commit (async): starts the broadcast (combined orders wait `kirana-broadcast-combined-delay`, 2 min), offers food-only orders to the nearest rider at once, orders with groceries once claimed (or, mixed, once the groceries are given up); inserts late Kirana stops; postpones unstarted broadcasts on kitchen delays; cleans up on cancel | ✅ |
| `dispatch/KiranaBroadcastService` | 5 nearest open Kiranas within 500 m, 60 s to claim, then the next ring (1 km), never farther than 2 km (`kirana-radius-km`) from the customer. Rounds start at the restaurant if it is within 2 km of the customer, else at the customer; after the last round the groceries are `UNFULFILLED` (grocery-only orders are cancelled, combined orders refund the groceries' share through `order/UnfulfilledGroceries` and dispatch the food) | ✅ |
| `dispatch/DispatchScheduler` | poller on every instance (`app.dispatch.poll-interval-ms`): opens/expands due broadcast rounds, expires offers, retries orders waiting for a rider (same rule as above: a mixed order only once its groceries are claimed or `UNFULFILLED`); each row in its own transaction with `FOR UPDATE SKIP LOCKED` | ✅ |
| `batching/CoLinearBatchingEngine` | for a claimed grocery-only order: ACTIVE riders near the Kirana (H3), whose remaining route passes within 500 m of both the Kirana and the customer; every insertion that keeps existing stops in order is simulated and the cheapest one adding ≤ 4 min wins. The order becomes `BATCHED`, the rider earns ₹15 and gets the "+1 stop" push. Otherwise normal dispatch. The maths is in the pure `CoLinearBatchingAlgorithm` | ✅ |
| `routing/RoutingClient` | `HaversineRoutingClient` (straight line × 1.3 at 20 km/h) by default; `OsrmRoutingClient` (`/route` + `/table`) with the `osrm` profile and `OSRM_BASE_URL` | ✅ |
| `spatial/SpatialIndexService` | H3 cells (resolution 9) and the disk covering a radius | ✅ |

The blueprint constants (500 m corridor, 4 min, 60 s, ₹150, ₹15) live under `app.ops.*` in `application.properties`; delivery range and pricing under `app.delivery.*`, rider distance pay under `app.rider.*`.
