# TransitHub — Backend API

REST API for the TransitHub intercity bus ticketing platform. Handles authentication, routes, schedules, bookings, tickets, and payments.

Built with **Java** and **Spring Boot**, deployed on **Railway**.

## Features

- 🔐 JWT authentication (register / login)
- 🗺️ Routes and schedules (Ghana intercity, seeded with sample data)
- 🎫 Bookings with seat selection and QR codes
- ✅ Conductor endpoints to verify a QR and mark a trip complete
- 📧 Email receipts (Spring Mail)
- 💳 Payment initiation/confirmation (Paystack / Mobile Money)

## Tech stack

- **Java + Spring Boot**
- **Spring Security + JWT**
- **Spring Data JPA** (PostgreSQL)
- **Docker** (for Railway deployment)

## Key endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Log in, returns JWT |
| GET | `/api/routes` | List routes |
| GET | `/api/schedules/search` | Find schedules for a route + date |
| POST | `/api/bookings` | Create a booking (auth) |
| GET | `/api/bookings/my` | Current user's bookings (auth) |
| POST | `/api/bookings/{id}/cancel` | Cancel a booking |
| POST | `/api/bookings/verify-qr` | Conductor: look up a booking by QR |
| POST | `/api/bookings/{id}/complete` | Conductor: mark trip complete |
| POST | `/api/payments/initiate` | Start a payment |

## Running locally

```bash
cd backend
./mvnw spring-boot:run
```

Configuration (datasource URL, JWT secret, etc.) is supplied via environment variables — see `application.properties`.

## Deployment

Containerised with the included `Dockerfile` and deployed on Railway. Pushes to `main` trigger a redeploy.

---

Part of the **TransitHub** project: [mobile app](https://github.com/transithubgroup74/transithub-app) · this backend · [admin dashboard](https://github.com/transithubgroup74/transithub-admin-dashboard).
