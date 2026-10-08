# HyperLocal API map

Base URL: `http://localhost:8080/api/v1`

**Status:** ✅ = working now · 🚧 = stub that returns `501 NOT_IMPLEMENTED` until its logic is written (the method in the service class has a TODO describing it).

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
| Ops console | – | `/admin/**` | `ADMIN` |

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

> Until the admin endpoints are built, approve a partner in dev with:
> `UPDATE delivery_agents SET verification_status='APPROVED' WHERE phone='+91XXXXXXXXXX';`

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
| GET | `/{id}` | items, bill, address, rider, `deliveryOtp` (4 digits, shown while the order is active) | ✅ |
| GET | `/{id}/timeline` | unified Kirana → Restaurant → Home line | ✅ |
| GET | `/{id}/tracking` | `{orderId, status, agentLocation, etaMinutes, updatedAt}` snapshot; live updates over WebSocket | ✅ |
| POST | `/{id}/cancel` `{reason}` | only before pickup (`409 ORDER_NOT_CANCELLABLE` / `ORDER_ALREADY_PICKED_UP`); releases the UPI hold or refunds a wallet payment to the wallet | ✅ |
| POST | `/{id}/rating` `{deliveryRating?, restaurantRating?, kiranaRating?, comment?}` | completed orders only, once (`409 ALREADY_RATED`); updates the stores' average rating | ✅ |

**Order status** (state machine; illegal moves return `409 ILLEGAL_STATE_TRANSITION`):
`PENDING → CONFIRMED → (BATCHED →) PICKING → OUT_FOR_DELIVERY → COMPLETED`, and `CANCELLED` from anything before pickup.
With the fake gateway (default) and wallet payments the order is `CONFIRMED` right away; with a real gateway it stays `PENDING` until the payment webhook confirms the hold.
Each part of the order also has its own status (`foodStatus` / `groceryStatus` internally): `AWAITING_PAYMENT → NEW → ACCEPTED ⇄ DELAYED → READY → PICKED_UP` for food, `AWAITING_PAYMENT → BROADCASTING → READY → PICKED_UP` (or `UNFULFILLED`) for groceries.

Deleting the account (`DELETE /customer/me`) is refused with `409 ORDER_IN_PROGRESS` while an order is active.

### Payments & wallet
| Method | Path | Notes | Status |
|---|---|---|---|
| POST | `/payments/orders/{orderId}/upi-hold` | retry a failed hold | 🚧 |
| GET | `/payments/orders/{orderId}` | `{paymentId, orderId, amount, status, upiIntentUrl, expiresAt}` | ✅ |
| POST | `/payments/webhook` (public, gateway signature) | | 🚧 |
| GET | `/customer/wallet` | `{balance, currency}`; balance = sum of the append-only ledger | ✅ |
| GET | `/customer/wallet/transactions?page&size` | newest first | ✅ |

Payment status: `HOLD_PENDING → HELD → CAPTURED → PARTIALLY_REFUNDED → REFUNDED`, `HOLD_PENDING/HELD → RELEASED`, `HOLD_PENDING → FAILED`.

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
| POST | `/documents/{type}/upload-url` | pre-signed upload URL | 🚧 |
| PUT | `/documents/{type}` | `documentNumber?, fileUrl` | ✅ |
| PUT | `/duty` | `{online}`: APPROVED partners only; can't go offline mid-delivery | ✅ |
| POST | `/location` | `{location{lat,lng}, headingDegrees?, speedKmph?}`; updates PostGIS point + H3 cell | ✅ |

### Trips: `/agent`
| Method | Path | Status |
|---|---|---|
| GET | `/offers` | 🚧 |
| POST | `/offers/{id}/accept` · `/offers/{id}/reject` | 🚧 |
| GET | `/route` (locked multi-stop route) | 🚧 |
| POST | `/route/stops/{stopId}/arrived` | 🚧 |
| POST | `/route/stops/{stopId}/kirana-qr` `{qrPayload}` | 🚧 |
| POST | `/route/stops/{stopId}/items/{itemId}/deficient` | 🚧 |
| POST | `/route/stops/{stopId}/picked-up` | 🚧 |
| POST | `/orders/{orderId}/deliver` `{deliveryOtp}` | 🚧 |
| POST | `/route/injections/{id}/ack` | 🚧 |
| GET | `/deliveries` · `/earnings?period=TODAY` · `/earnings/payouts` | 🚧 |

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
| GET | `/broadcasts` | | 🚧 |
| POST | `/broadcasts/{id}/claim` ("Items in Hand") | | 🚧 |
| GET | `/orders?status` | | 🚧 |
| POST | `/orders/{id}/accept` `{prepMinutes}` · `/delay` · `/ready` | | 🚧 |
| GET | `/items?page&size` | paginated (`{items, page, size, totalItems}`), by category then name | ✅ |
| POST | `/items` | see body below → 201 | ✅ |
| PUT | `/items/{id}` | same body as POST | ✅ |
| PATCH | `/items/{id}/availability` | `{available}` | ✅ |

Item body: `name`, `description?`, `price` (> 0), `category`, `veg?`, `zone` (HOT/COLD/AMBIENT), `imageUrl?`, `available`, and for groceries `brand?`, `packSize?`, `mrp?`.
The item's `itemType` follows the store: restaurants create `FOOD`, Kiranas create `GROCERY`. Grocery listings form the "virtual warehouse": customers see products listed by any Kirana within 2 km, and whichever nearby Kirana claims the order fulfils it.

## Admin: `/admin` (ROLE_ADMIN)
`GET /agents?status=UNDER_REVIEW` · `POST /agents/{id}/approve` · `POST /agents/{id}/reject` · `POST /agents/{id}/documents/{type}/reject` · `POST /merchants/{id}/approve`: all 🚧, and there is no admin sign-in yet.

---

## Real-time (STOMP over WebSocket)
- Endpoint: `ws://localhost:8080/ws`. The CONNECT frame must carry the header `Authorization: Bearer <accessToken>`.
- Rider → server: `SEND /app/agent/location` with the same body as `POST /agent/me/location` ✅
- Server → customer: subscribe to `/user/queue/tracking`
- Server → rider: subscribe to `/user/queue/route-updates` (offers, the "+1 stop" injection flash) and `/user/queue/errors`
- Only `/user/...` subscriptions are allowed.

## Internal engines (no HTTP endpoints, all 🚧)
| Class | Blueprint section |
|---|---|
| `dispatch/DispatchOrchestrator` | parallel dispatch, kitchen-delay handling |
| `dispatch/KiranaBroadcastService` | 5 closest shops within 500 m, 60 s claim timeout, H3 ring expansion, dark-store fallback |
| `batching/CoLinearBatchingEngine` | 500 m corridor, vector intersection, Δt ≤ 4 min |
| `routing/OsrmRoutingClient` | road-network routes and travel-time matrix |
| `spatial/SpatialIndexService` | H3 cells ✅ (resolution 9) |

The blueprint constants (2 km radius, 500 m corridor, 4 min, 60 s, ₹150, ₹15) live under `app.ops.*` in `application.properties`.
