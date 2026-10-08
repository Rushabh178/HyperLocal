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
| POST | `/serviceability` | `{lat,lng}`: inside the 2 km radius? | 🚧 |

Address body: `label` (HOME/WORK/OTHER), `customLabel?`, `recipientName?`, `recipientPhone?`, `houseNumber`, `buildingName?`, `street?`, `landmark?`, `area?`, `city`, `state`, `pincode`, `location {lat,lng}`, `deliveryInstructions?`, `makeDefault?`. The server stores a PostGIS point plus its H3 cell.

### Catalog: `/catalog` (public GET)
| Method | Path | Status |
|---|---|---|
| GET | `/restaurants?lat&lng&cuisine&page&size` | 🚧 |
| GET | `/restaurants/{id}/menu` | 🚧 |
| GET | `/groceries?lat&lng&q&category&page&size` | 🚧 |
| GET | `/search?lat&lng&q` | 🚧 |

### Unified cart: `/customer/cart`
| Method | Path | Status |
|---|---|---|
| GET | `/` | 🚧 |
| POST | `/items` `{itemType FOOD/GROCERY, itemId, restaurantId?, quantity}` | 🚧 |
| PATCH | `/items/{cartItemId}` `{quantity}` | 🚧 |
| DELETE | `/items/{cartItemId}` | 🚧 |
| DELETE | `/` | 🚧 |
| PUT | `/address` `{addressId}` | 🚧 |
| GET | `/nudges` (grocery-only cart under ₹150 → suggested meals) | 🚧 |

### Orders: `/customer/orders`
| Method | Path | Status |
|---|---|---|
| POST | `/checkout` `{addressId, paymentMethod, upiVpa?}` + `Idempotency-Key` header | 🚧 |
| GET | `/?status&page&size` | 🚧 |
| GET | `/{id}` | 🚧 |
| GET | `/{id}/timeline` (unified Kirana → Restaurant → Home line) | 🚧 |
| GET | `/{id}/tracking` (snapshot; live updates over WebSocket) | 🚧 |
| POST | `/{id}/cancel` `{reason}` | 🚧 |
| POST | `/{id}/rating` | 🚧 |

### Payments & wallet
| Method | Path | Status |
|---|---|---|
| POST | `/payments/orders/{orderId}/upi-hold` | 🚧 |
| GET | `/payments/orders/{orderId}` | 🚧 |
| POST | `/payments/webhook` (public, gateway signature) | 🚧 |
| GET | `/customer/wallet` | 🚧 |
| GET | `/customer/wallet/transactions` | 🚧 |

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

| Method | Path | Status |
|---|---|---|
| POST | `/merchant/auth/signup` · `verify-phone` · `login` · `otp/send` · `otp/login` · `refresh` · `logout` | 🚧 |
| GET | `/merchant/me` · PUT `/merchant/me/store-status` | 🚧 |
| GET | `/merchant/broadcasts` | 🚧 |
| POST | `/merchant/broadcasts/{id}/claim` ("Items in Hand") | 🚧 |
| GET | `/merchant/store-qr` | 🚧 |
| GET | `/merchant/orders?status` | 🚧 |
| POST | `/merchant/orders/{id}/accept` `{prepMinutes}` · `/delay` · `/ready` | 🚧 |
| GET/POST | `/merchant/items` · PUT `/merchant/items/{id}` · PATCH `/merchant/items/{id}/availability` | 🚧 |

To build merchant auth: add a `Merchant` entity that implements `AuthAccount`, then make `MerchantAuthService` extend `AccountAuthService` the same way `CustomerAuthService` does.

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
