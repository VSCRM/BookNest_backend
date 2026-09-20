# 📚 BookNest — Backend

The backend for the [BookNest](https://github.com/VSCRM/BookNest) online
bookstore: two independent services that together serve the catalog, cart,
checkout, order history, saved books, a sandboxed Ruby code playground, and
authentication for the React storefront. Both services ship as one Docker
image, and all persistent data lives in a managed **[Neon](https://neon.tech)**
PostgreSQL database — no database container has to be run or maintained.

[![Ruby](https://img.shields.io/badge/Ruby-3.2.11-CC342D?logo=ruby&logoColor=white)](https://www.ruby-lang.org/)
[![Rails](https://img.shields.io/badge/Rails-7.1-CC0000?logo=rubyonrails&logoColor=white)](https://rubyonrails.org/)
[![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Neon](https://img.shields.io/badge/Neon-Serverless_Postgres-00E599?logo=neon&logoColor=black)](https://neon.tech/)
[![SQLite](https://img.shields.io/badge/SQLite-1.4-003B57?logo=sqlite&logoColor=white)](https://www.sqlite.org/)
[![Apache Kafka](https://img.shields.io/badge/Kafka-KRaft-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

**Frontend repository:** [BookNest](https://github.com/VSCRM/BookNest) — the
React storefront that consumes this API.

**Live demo:** [vscrm.github.io/BookNest](https://vscrm.github.io/BookNest/) —
the deployed storefront running in real mode (`VITE_USE_MOCK=false`) against
this backend and its Neon database. It shows data whenever this backend is
running and reachable.

**Docker image:** [`ghcr.io/vscrm/booknest-backend`](https://github.com/VSCRM/BookNest_backend/pkgs/container/booknest-backend) —
the combined Rails + auth-service image, published on every release
(see [Releases & Docker Image](#-releases--docker-image)).

**API docs (once the stack is running):**
[Swagger UI — http://localhost:8080/api](http://localhost:8080/api) ·
[raw OpenAPI spec — http://localhost:8080/openapi.yaml](http://localhost:8080/openapi.yaml)

| Service            | Stack                     |  Port  | Responsibility                                                                 |
| ------------------ | ------------------------- | :----: | ------------------------------------------------------------------------------ |
| `BookNest/backend` | Ruby on Rails 7.1 (API)   | `8080` | Catalog, cart, orders, saved books, a sandboxed Ruby code-execution playground |
| `auth-service`     | Spring Boot 4.1 (Java 25) | `9000` | Registration, login, JWT issuing/refresh, Google OAuth2, password reset        |

The two services share a single **JWT secret**: `auth-service` signs tokens,
Rails only ever verifies them (it never issues its own), and real identity
lives exclusively in Neon PostgreSQL, owned by `auth-service`. `auth-service` also
publishes domain events (`UserRegisteredEvent`, `UserLoggedInEvent`,
`LoginFailedEvent`) to **Kafka** for any downstream consumer (analytics,
notifications, fraud detection, …) that wants to react to them later.

---

## Table of Contents

- [📚 BookNest — Backend](#-booknest--backend)
  - [Table of Contents](#table-of-contents)
  - [📌 What This Is](#-what-this-is)
  - [📌 Architecture](#-architecture)
  - [🐳 Why One Combined Container](#-why-one-combined-container)
  - [🚀 Tech Stack](#-tech-stack)
    - [`BookNest/backend` (Rails)](#booknestbackend-rails)
    - [`auth-service` (Spring Boot)](#auth-service-spring-boot)
    - [Infrastructure](#infrastructure)
  - [📡 API Endpoints](#-api-endpoints)
    - [Rails — `BookNest/backend` (`http://localhost:8080`)](#rails--booknestbackend-httplocalhost8080)
    - [Spring Boot — `auth-service` (`http://localhost:9000`)](#spring-boot--auth-service-httplocalhost9000)
  - [🔐 Authentication Flow](#-authentication-flow)
  - [🧪 The Ruby Playground](#-the-ruby-playground)
  - [🗄️ Database \& Neon Setup](#️-database--neon-setup)
    - [1. Create the Neon project and databases](#1-create-the-neon-project-and-databases)
    - [2. Point the backend at Neon](#2-point-the-backend-at-neon)
    - [3. Tables are created for you](#3-tables-are-created-for-you)
    - [4. Check that it worked](#4-check-that-it-worked)
    - [Good to know](#good-to-know)
  - [🐳 Getting Started — Docker (recommended)](#-getting-started--docker-recommended)
  - [🛠️ Getting Started — Running Services Individually](#️-getting-started--running-services-individually)
    - [Rails (`BookNest/backend`)](#rails-booknestbackend)
    - [auth-service (Spring Boot)](#auth-service-spring-boot-1)
  - [⚙️ Environment Variables](#️-environment-variables)
  - [🧪 Testing](#-testing)
    - [auth-service](#auth-service)
    - [Rails (`BookNest/backend`)](#rails-booknestbackend-1)
  - [🏗️ Project Structure](#️-project-structure)
  - [☁️ Deployment](#️-deployment)
  - [📦 Releases \& Docker Image](#-releases--docker-image)
    - [Automatic image publishing — `.github/workflows/docker-publish.yml`](#automatic-image-publishing--githubworkflowsdocker-publishyml)
    - [Offline release assets — `pack-release.sh`](#offline-release-assets--pack-releasesh)
    - [Cutting a release](#cutting-a-release)
    - [Running the published image](#running-the-published-image)
  - [📄 License](#-license)

---

## 📌 What This Is

BookNest's backend is split into two purpose-built services rather than one
monolith:

- **`BookNest/backend`** (Rails) owns the storefront's business data — books,
  carts, orders, saved books — and never issues credentials itself. It
  trusts a `booknest_jwt` cookie signed by `auth-service`, using it to
  find-or-create a thin **shadow `User` row** keyed by email — just enough of
  a local record to attach a cart or an order to. A legacy Rails
  session/`bcrypt` login path still exists for a handful of
  pre-existing/seeded accounts (e.g. the admin), but it is not the primary
  path for shoppers. It also hosts a small **sandboxed Ruby code-execution
  API** used by a separate learning-playground frontend.
- **`auth-service`** (Spring Boot) owns real identity: registration, password
  hashing (bcrypt), login, refresh, Google OAuth2, and password-reset codes.
  It is the only service with write access to the `users` table backing
  authentication, and the only service that talks to Kafka.
- **Kafka** decouples "something happened to a user" from any consumer that
  might care later — `auth-service` fires and forgets with a fast
  (`max.block.ms: 2000`) publish so authentication never blocks on a broker
  being unavailable.

---

## 📌 Architecture

BookNest's storefront is a static React single-page app, so the browser talks
to the backend directly and there is no server-side rendering layer in
between. Requests fan out to two services depending on their path: anything
under `/api/v1/*` (books, cart, orders, saved books, the Ruby playground) goes
to the **Rails API** on port `8080`, while everything under `/api/auth/*`
(registration, login, token refresh, profile, password reset, and the Google
OAuth2 redirect) goes to the **Spring Boot auth-service** on port `9000`.

The two services never call each other. Trust between them is established
purely through cryptography: `auth-service` signs a JWT with the shared
`JWT_SECRET` and hands it to the browser as an HTTP-only cookie, and Rails
verifies that same signature on every request it receives. Rails reads the
user's email, name, and role from the token and keeps a thin local "shadow"
user row so that carts and orders have something to attach to.

Persistence is handled entirely by **Neon**, a managed serverless PostgreSQL
service. A single Neon project hosts two separate databases: `booknest_auth`,
owned by `auth-service` and holding real user credentials and profiles, and
`booknest_shop`, owned by Rails and holding the catalog, carts, orders, saved
books, and the shadow users. They are deliberately kept apart because both
contain a `users` table with unrelated columns. Both services connect to Neon
over TLS, so the only thing the machine running the backend needs is outbound
internet access — there is no local Postgres container in this setup. (For
offline development Rails can still fall back to a file-based SQLite database;
see [Database & Neon Setup](#️-database--neon-setup).)

Finally, `auth-service` publishes user lifecycle events (registration,
successful login, failed login) to **Apache Kafka** running in KRaft mode. The
publish is fire-and-forget with a two-second cap, so authentication keeps
working even when no broker is reachable. Kafka is the only supporting
container the stack still runs alongside the combined backend container.

---

## 🐳 Why One Combined Container

`combined/` builds and runs **both** services inside a single Docker
container instead of two, for one specific reason: when Rails and
`auth-service` ran as separate containers, they could end up on two
different system clocks (a common symptom of Docker Desktop VM clock drift,
especially right after the host machine sleeps/resumes). Since Rails
verifies a JWT's `exp` claim against _its own_ clock, a few seconds of skew
was enough for perfectly valid, freshly-issued tokens to be rejected as
"expired" — a confusing, intermittent 401-everywhere bug.

Putting both processes in one container means they share one kernel clock,
which makes that specific class of skew impossible. On top of that, the
container's entrypoint (`combined/entrypoint.sh`) syncs its own clock against
real-world time on startup via HTTP (`htpdate`, so it works even where
outbound NTP/UDP-123 is blocked), correcting absolute drift as well.

```
combined/
├── Dockerfile      # Multi-stage: builds the auth-service jar (Maven), then
│                     assembles a runtime image with Ruby (Rails) + a JRE
│                     (auth-service jar), running both processes.
└── entrypoint.sh   # Syncs the clock, runs `rails db:prepare`, then starts
                      auth-service and Rails as two background processes in
                      the same container, forwarding signals to both and
                      bringing the whole container down if either one dies.
```

On every start the entrypoint also runs `bin/rails db:prepare` against the
configured database, which is what creates and updates the shop tables in Neon
(see [Database & Neon Setup](#️-database--neon-setup)).

This does **not** replace running each service directly for development —
see [Running Services Individually](#-getting-started--running-services-individually)
below — it's specifically what the root `docker-compose.yml` builds for a
one-command stack or a simple single-host deployment.

---

## 🚀 Tech Stack

### `BookNest/backend` (Rails)

| Technology                                                                                                                                                | Version | Purpose                                                                |
| --------------------------------------------------------------------------------------------------------------------------------------------------------- | :-----: | ---------------------------------------------------------------------- |
| [Ruby](https://www.ruby-lang.org/)                                                                                                                        | 3.2.11  | Language runtime                                                       |
| [Rails](https://rubyonrails.org/)                                                                                                                         | ~> 7.1  | Web framework, routing, ActiveRecord ORM                               |
| [SQLite](https://www.sqlite.org/) (`sqlite3` gem)                                                                                                         | ~> 1.4  | Development/default database (file-based, zero setup)                  |
| [pg](https://github.com/ged/ruby-pg)                                                                                                                      | ~> 1.5  | PostgreSQL adapter — connects to Neon when `RAILS_ENV=production`      |
| [Puma](https://github.com/puma/puma)                                                                                                                      | >= 5.0  | Application server                                                     |
| [jwt](https://github.com/jwt/ruby-jwt)                                                                                                                    | ~> 2.9  | Verifies HS256 JWTs issued by `auth-service`                           |
| [bcrypt](https://github.com/codahale/bcrypt-ruby)                                                                                                         | ~> 3.1  | Password hashing for the legacy/seeded local-session accounts          |
| [rack-cors](https://github.com/cyu/rack-cors)                                                                                                             |    —    | CORS for the React storefront's cross-origin requests                  |
| [rubocop](https://rubocop.org/)                                                                                                                           |    —    | Static analysis — also powers the "Lint" action of the Ruby playground |
| [jbuilder](https://github.com/rails/jbuilder)                                                                                                             |    —    | JSON view templates (available; most JSON responses are built inline)  |
| [importmap-rails](https://github.com/rails/importmap-rails), [turbo-rails](https://turbo.hotwired.dev/), [stimulus-rails](https://stimulus.hotwired.dev/) |    —    | Powers the hidden, server-rendered `/welcome` easter-egg page          |
| [bootsnap](https://github.com/Shopify/bootsnap)                                                                                                           |    —    | Boot-time caching                                                      |

### `auth-service` (Spring Boot)

| Technology                                                                                  | Version | Purpose                                                                       |
| ------------------------------------------------------------------------------------------- | :-----: | ----------------------------------------------------------------------------- |
| [Java](https://openjdk.org/)                                                                |   25    | Language runtime                                                              |
| [Spring Boot](https://spring.io/projects/spring-boot)                                       |  4.1.1  | Application framework                                                         |
| [Spring Data JPA](https://spring.io/projects/spring-data-jpa)                               |    —    | ORM over PostgreSQL                                                           |
| [Spring Security](https://spring.io/projects/spring-security) + OAuth2 Client               |    —    | Authentication, Google OAuth2 login, JWT filter chain, CORS                   |
| [Spring Kafka](https://spring.io/projects/spring-kafka)                                     |    —    | Publishes user lifecycle events                                               |
| [Spring Validation](https://docs.spring.io/spring-framework/reference/core/validation.html) |    —    | Request-body validation (`@Valid` DTOs)                                       |
| [jjwt](https://github.com/jwtk/jjwt) (api/impl/jackson)                                     | 0.13.0  | Signs/verifies JWT access & refresh tokens                                    |
| [PostgreSQL JDBC driver](https://jdbc.postgresql.org/)                                      |    —    | Connects `auth-service` to Neon over SSL; Hibernate creates the `users` table |
| [Lombok](https://projectlombok.org/)                                                        |    —    | Boilerplate reduction (constructors, getters)                                 |
| [Log4j2](https://logging.apache.org/log4j/2.x/)                                             |    —    | Logging, configured via `log4j2.xml`                                          |
| [Maven](https://maven.apache.org/)                                                          |    —    | Build tool                                                                    |
| [JaCoCo](https://www.jacoco.org/jacoco/)                                                    | 0.8.13  | Enforces a 90% line-coverage floor on `mvn verify`                            |

### Infrastructure

| Technology                                                                             | Purpose                                                                                    |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| [Docker](https://www.docker.com/) / [Docker Compose](https://docs.docker.com/compose/) | Runs the combined backend container and Kafka (plus an optional local Postgres)            |
| [Apache Kafka](https://kafka.apache.org/) (KRaft mode)                                 | Event bus for auth lifecycle events — no ZooKeeper required                                |
| [Neon](https://neon.tech/) (serverless PostgreSQL)                                     | Primary database — hosts both `booknest_auth` and `booknest_shop`; free tier, SSL required |
| [PostgreSQL 16](https://www.postgresql.org/) (`postgres:16-alpine`)                    | _Optional_ local stand-in for Neon (fully offline development)                             |

---

## 📡 API Endpoints

### Rails — `BookNest/backend` (`http://localhost:8080`)

| Method   | Endpoint                                 |     Auth      | Description                                                                              |
| -------- | ---------------------------------------- | :-----------: | ---------------------------------------------------------------------------------------- |
| `GET`    | `/api/v1/books`                          |       —       | Catalog list — supports `?q=`, `?genre=`, `?sort=asc\|desc`, `?locale=en\|uk`            |
| `GET`    | `/api/v1/books/:id`                      |       —       | Single book detail                                                                       |
| `GET`    | `/api/v1/admin/books`                    |   🛡️ Admin    | Full (non-localized) catalog for the admin table                                         |
| `POST`   | `/api/v1/admin/books`                    |   🛡️ Admin    | Create a book (`multipart/form-data` for the optional cover image)                       |
| `PATCH`  | `/api/v1/admin/books/:id`                |   🛡️ Admin    | Update a book                                                                            |
| `DELETE` | `/api/v1/admin/books/:id`                |   🛡️ Admin    | Delete a book (blocked with `book_has_orders` if it appears in existing orders)          |
| `GET`    | `/api/v1/cart`                           | Guest or user | Current cart (guest carts are session-based)                                             |
| `POST`   | `/api/v1/cart/items`                     | Guest or user | Add an item to the cart                                                                  |
| `PATCH`  | `/api/v1/cart/items/:book_id`            | Guest or user | Update an item's quantity                                                                |
| `DELETE` | `/api/v1/cart/items/:book_id`            | Guest or user | Remove an item from the cart                                                             |
| `GET`    | `/api/v1/orders`                         |  ✅ Required  | Current user's order history                                                             |
| `GET`    | `/api/v1/orders/:id`                     |  ✅ Required  | Single order detail (scoped to the caller — never another user's order)                  |
| `POST`   | `/api/v1/orders`                         |  ✅ Required  | Checkout — converts the current cart into an order                                       |
| `GET`    | `/api/v1/users/:username/saved`          |  ✅ Required  | Saved books ("wishlist") for the authenticated user                                      |
| `POST`   | `/api/v1/users/:username/saved`          |  ✅ Required  | Save a book                                                                              |
| `DELETE` | `/api/v1/users/:username/saved/:book_id` |  ✅ Required  | Remove a saved book                                                                      |
| `POST`   | `/api/v1/playground/execute`             |       —       | Sandboxed execution of a Ruby snippet (see [The Ruby Playground](#-the-ruby-playground)) |
| `POST`   | `/api/v1/playground/lint`                |       —       | RuboCop lint of a Ruby snippet                                                           |
| `GET`    | `/welcome`                               |       —       | A hidden, animated easter-egg page — not linked from any menu                            |
| `GET`    | `/api`                                   |       —       | Redirects to the Swagger/OpenAPI UI (`/docs/index.html`)                                 |
| `GET`    | `/up`                                    |       —       | Rails health check                                                                       |

> `username` in the saved-books routes is the user's email; the caller's own
> identity (from the JWT) is what's actually used server-side — the URL
> parameter is never trusted for authorization, closing off what would
> otherwise be an IDOR (insecure direct object reference). The same applies
> to order lookups: `show` and checkout are scoped to `current_user`, not to
> a raw record ID.
>
> **`/` is intentionally undefined** and returns Rails' normal 404 — the
> catalog UI that used to live there has been fully replaced by this
> separate React storefront. The legacy `/rubyback` prefix mentioned in
> older documentation has likewise been **removed entirely**; only the
> hidden `/welcome` page and the JSON API remain.

### Spring Boot — `auth-service` (`http://localhost:9000`)

| Method | Endpoint                       |      Auth      | Description                                                       |
| ------ | ------------------------------ | :------------: | ----------------------------------------------------------------- |
| `GET`  | `/api/auth/health`             |       —        | Liveness check                                                    |
| `POST` | `/api/auth/register`           |       —        | Create an account (email, password, name)                         |
| `POST` | `/api/auth/login`              |       —        | Email/password login — issues access + refresh token cookies      |
| `POST` | `/api/auth/refresh`            | Refresh cookie | Rotates the access token                                          |
| `GET`  | `/api/auth/me`                 |  ✅ Required   | Current authenticated user                                        |
| `PUT`  | `/api/auth/me`                 |  ✅ Required   | Update profile (name, optional password change)                   |
| `POST` | `/api/auth/forgot-password`    |       —        | Emails/returns a 6-digit reset code                               |
| `POST` | `/api/auth/reset-password`     |       —        | Redeems the code and sets a new password                          |
| `POST` | `/api/auth/logout`             |       —        | Clears the JWT cookies (works even with an expired/missing token) |
| `GET`  | `/oauth2/authorization/google` |       —        | Starts the Google OAuth2 login redirect                           |

---

## 🔐 Authentication Flow

1. The frontend calls `auth-service` (`/api/auth/register` or `/login`, or
   the Google OAuth2 redirect). On success, `auth-service` sets an HTTP-only
   `booknest_jwt` access-token cookie (short-lived, `JWT_ACCESS_MINUTES`,
   path `/`) and a `booknest_refresh` cookie (`JWT_REFRESH_DAYS`, path
   `/api/auth/refresh`), both signed with the shared `JWT_SECRET`.
2. Every subsequent request to **either** backend carries that cookie.
   `auth-service` verifies it directly; Rails verifies the exact same
   signature via `JwtAuthenticator` and extracts the `email`, `name`, and
   `role` claims — it never contacts `auth-service` over the network to do
   so.
3. On first sight of a valid token, Rails **creates a local shadow `User`
   row** if one doesn't exist yet (keyed by email, with a random, unusable
   bcrypt password hash so the column is never left `nil`), just so
   carts/orders have a foreign key to attach to, and keeps `name`/`role` in
   sync with the token's claims on every request.
4. A **guest** who adds items to their cart before logging in gets an
   anonymous, session-keyed cart; on login, that cart is transparently merged
   into the newly-identified user's cart (quantities summed for books present
   in both), so nothing is lost.
5. `auth-service` publishes `UserRegisteredEvent` / `UserLoggedInEvent` /
   `LoginFailedEvent` to Kafka on the corresponding actions — fire-and-forget,
   with a 2-second max block time so a down/unreachable broker can never make
   authentication itself fail or hang.
6. Spring Security is fully **stateless** — there is no `HttpSession` on the
   auth-service side; every request is re-authenticated from the JWT cookie
   by `JwtAuthenticationFilter`. CSRF protection is disabled there
   deliberately: with no session-backed CSRF token to protect, and cookie
   auth that can't be forged into a state-changing request without also
   controlling a page on an allowed CORS origin, classic CSRF doesn't apply
   the way it does to session-cookie auth.

---

## 🧪 The Ruby Playground

`Api::V1::PlaygroundController` is a small, self-contained "run this Ruby
snippet" service used by a separate learning/labs frontend (not this
storefront) to teach strings/arithmetic, arrays, and classes/OOP. It is
public on purpose — no login required — but every submission is executed
with real limits so a bad or malicious snippet can't hurt the server:

- Runs in its **own process/process-group** (`Process.spawn(..., pgroup:
true)`), not in the Rails process itself.
- **Wall-clock timeout** of 5 seconds, enforced from the parent process via
  `Timeout.timeout`; on timeout the whole process group is killed
  (`Process.kill(-9, pid)`), so a snippet that forks can't escape it.
- **CPU-time** (3s) and **memory** (256 MB) `rlimit`s enforced by the OS
  itself on the spawned process.
- **A blank environment** — every inherited env var is explicitly nil'd out
  before spawning (only `PATH`/`HOME` are re-added), so a submitted
  `puts ENV.to_h` can never leak `JWT_SECRET`, database credentials, or
  anything else this process holds.
- **Size caps** — 20 KB of source, 4 KB of stdin, 100 KB of captured output
  (stdout/stderr are truncated beyond that).
- A companion `POST /api/v1/playground/lint` endpoint runs the same snippet
  through RuboCop (`--format json --force-default-config`) and returns
  structured offenses (line, column, severity, message, cop name).

This is **process-level sandboxing, not a full container/VM jail** — it's
appropriate for a teaching tool with this backend's threat model, but a
public, high-traffic, multi-tenant deployment of this feature should run it
behind an actual container or micro-VM sandbox instead.

---

## 🗄️ Database & Neon Setup

BookNest keeps all of its data in **[Neon](https://neon.tech)** — serverless
PostgreSQL with a free tier. It replaces the Postgres container that the
Docker Compose file can optionally start, so you do not need to run, back up,
or expose a database yourself.

One Neon project holds two independent databases, one per service:

| Database        | Owner          | Env variable    | Contents                                                              |
| --------------- | -------------- | --------------- | --------------------------------------------------------------------- |
| `booknest_auth` | `auth-service` | `DB_NAME`       | Real user accounts: credentials, provider, role, password-reset codes |
| `booknest_shop` | Rails          | `RAILS_DB_NAME` | Catalog, carts, orders, saved books, and the local shadow `users`     |

The two must never point at the same database: both have a `users` table with
completely different columns, and Hibernate would try to bolt its columns onto
Rails' table.

### 1. Create the Neon project and databases

1. Sign up at [neon.tech](https://neon.tech) and create a **new project**,
   choosing the region closest to wherever the backend runs.
2. In the Neon Console open **Databases → New Database** and create
   `booknest_auth` and `booknest_shop` (or run
   `CREATE DATABASE booknest_shop;` in the SQL Editor). Neon's default
   `neondb` database can stay unused.
3. Open **Connection details** and note the **host**, **role** (user) and
   **password**. Prefer the _direct_ host (without `-pooler`): schema changes
   and long-lived JDBC connections work best without a connection pooler in
   between.

You only create the empty databases yourself. The tables inside them are
created automatically, as described below.

### 2. Point the backend at Neon

Set these variables in the root `.env` (the complete file is shown in
[Getting Started — Docker](#-getting-started--docker-recommended)):

```env
DB_HOST=ep-your-endpoint.eu-central-1.aws.neon.tech
DB_PORT=5432
DB_USERNAME=neondb_owner
DB_PASSWORD=your-neon-password
DB_SSLMODE=require
DB_NAME=booknest_auth
RAILS_DB_NAME=booknest_shop
RAILS_ENV=production
```

`RAILS_ENV=production` is what switches Rails from SQLite to PostgreSQL, and
`DB_SSLMODE=require` is mandatory — Neon rejects unencrypted connections.

### 3. Tables are created for you

**`booknest_auth` — created by Hibernate.** On its first start `auth-service`
connects to Neon and Hibernate (`ddl-auto: update`) creates the `users` table:
`id`, `email` (unique), `name`, `password_hash`, `provider` (`LOCAL` or
`GOOGLE`), `role`, `reset_code`, `reset_code_expires_at`, and `created_at`.
`AdminSeeder` then adds the admin account(s) from `ADMIN_EMAIL` /
`ADMIN_PASSWORD` (or `ADMIN_ACCOUNTS`) if they do not exist yet.

**`booknest_shop` — created by Rails migrations.** The combined container's
entrypoint runs `bin/rails db:prepare` on every start. On an empty database it
loads `db/schema.rb` and then runs `db/seeds.rb`; on a database that already
has tables it only applies migrations that are still pending, so restarting
is always safe. This creates:

| Table         | Purpose                                                                                 |
| ------------- | --------------------------------------------------------------------------------------- |
| `books`       | Catalog — Ukrainian and English title/author/genre/description, price, stock, cover URL |
| `users`       | Thin shadow copy of each authenticated user (email, name, role), created on first login |
| `carts`       | One cart per user, or per anonymous session token for guests                            |
| `cart_items`  | Books and quantities inside a cart (unique per cart and book)                           |
| `orders`      | Placed orders — unique order number and status                                          |
| `order_items` | Books in an order, with the unit price frozen at checkout                               |
| `saved_books` | Per-user wishlist (unique per user and book)                                            |

Rails also keeps its own bookkeeping tables, `schema_migrations` and
`ar_internal_metadata`. The seed step fills `books` with a sample catalog and
creates the admin and guest accounts from your `.env`.

**Running it manually.** You can create or update the shop tables without
starting the container — export the same variables from your `.env` first,
because Rails does not read `.env` files by itself:

```bash
cd BookNest/backend
set -a; source ../../.env; set +a      # exports DB_*, RAILS_DB_NAME, SECRET_KEY_BASE, ...

bin/rails db:prepare    # empty DB: create tables + seed. Existing DB: apply pending migrations
bin/rails db:migrate    # only apply pending migrations
bin/rails db:seed       # re-run the seeds (safe to repeat, but resets seeded accounts' passwords to your .env values)
```

### 4. Check that it worked

Open the **SQL Editor** in the Neon Console (or connect with
`psql "postgresql://USER:PASSWORD@HOST/booknest_shop?sslmode=require"`) and run:

```sql
\dt                          -- psql only; lists the shop tables
SELECT count(*) FROM books;  -- sample catalog after seeding
```

Do the same against `booknest_auth` to see the `users` table.

### Good to know

- **Data survives Docker cleanups.** `docker compose down -v` removes only the
  bundle cache and the local SQLite scratch storage — everything in Neon is
  untouched.
- **Cover images are files, not rows.** Uploaded covers are written to
  `BookNest/backend/public/uploads/covers/` and only their URL is stored in
  `books.cover_image_url`. The compose file bind-mounts that directory from
  the host, so keep it (or back it up) if you upload real covers.
- **Cold starts.** On Neon's free tier the database may suspend after a
  period of inactivity, so the first request after a quiet spell can take a
  moment longer while it wakes up.
- **Schema changes.** For the shop database add a new Rails migration and
  restart; `db:prepare` applies it. `auth-service`'s `ddl-auto: update` only
  adds columns and tables, so adopt Flyway or Liquibase before relying on it
  for anything more serious than the current schema.
- **Local development without Neon.** Rails defaults to a file-based SQLite
  database (`RAILS_ENV=development`, seeded automatically). `auth-service`
  always needs PostgreSQL: use a separate Neon branch/database, or the
  optional local container from `auth-service/docker-compose.yml`
  (`DB_SSLMODE=disable`).

---

## 🐳 Getting Started — Docker (recommended)

This runs Kafka and the combined backend container (Rails + auth-service, see
[above](#-why-one-combined-container)). Both services connect to your Neon
project, so create the databases first — see
[Database & Neon Setup](#️-database--neon-setup).

**1. Create the root `.env`.** Start from `.env.example` (it already targets a
Neon-backed setup) and make sure it contains the variables below — the ones
Rails needs (`RAILS_ENV`, `SECRET_KEY_BASE`, `GUEST_*`) are also listed in
`BookNest/.env.example`:

```env
# Neon PostgreSQL
DB_HOST=ep-your-endpoint.eu-central-1.aws.neon.tech
DB_PORT=5432
DB_USERNAME=neondb_owner
DB_PASSWORD=your-neon-password
DB_SSLMODE=require
DB_NAME=booknest_auth
RAILS_DB_NAME=booknest_shop

# Rails
RAILS_ENV=production
SECRET_KEY_BASE=<output of: openssl rand -hex 64>

# Shared JWT secret (identical for both services)
JWT_SECRET=<output of: openssl rand -hex 32>
JWT_ACCESS_MINUTES=15
JWT_REFRESH_DAYS=7

# auth-service
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FRONTEND_URL=https://vscrm.github.io/BookNest
CORS_ALLOWED_ORIGINS=https://vscrm.github.io
# GitHub Pages (https) calling this backend is a cross-site request:
# keep SameSite=None + Secure. For a frontend on http://localhost:5174 use
# COOKIE_SAME_SITE=Lax and COOKIE_SECURE=false, and add that origin above.
COOKIE_SECURE=true
COOKIE_SAME_SITE=None

# Seeded accounts
ADMIN_EMAIL=admin@booknest.local
ADMIN_PASSWORD=change-me
GUEST_EMAIL=guest@booknest.local
GUEST_PASSWORD=change-me
```

**2. Start the stack.**

```bash
# Neon is the database, so skip the (unused) local Postgres container:
docker compose up --build --no-deps backend kafka

# ...or detached (keeps running after you close the terminal):
docker compose up --build -d --no-deps backend kafka
```

A plain `docker compose up --build` works too, but it also starts the optional
`auth_postgres` container — unused when `DB_HOST` points at Neon, and it would
occupy host port `DB_PORT` for nothing.

> **Prefer not to build locally?** Every release publishes a ready-made image to
> `ghcr.io/vscrm/booknest-backend` — see
> [Releases & Docker Image](#-releases--docker-image).

On the first start the logs show Rails creating and seeding the shop tables
and `auth-service` creating `users` in Neon; later starts just reconnect.

| Service           | URL                                                  |
| ----------------- | ---------------------------------------------------- |
| Rails API         | http://localhost:8080                                |
| Swagger / OpenAPI | http://localhost:8080/api                            |
| auth-service      | http://localhost:9000                                |
| Kafka (host-side) | localhost:9092                                       |
| Database          | Neon — remote, `DB_HOST:5432` (nothing runs locally) |

Managing the stack once it's up:

```bash
docker compose ps               # confirm every container is healthy
docker compose logs -f          # tail all services' logs
docker compose logs -f backend  # tail just the combined Rails + auth-service container
docker compose restart backend  # restart just the combined container (e.g. after an .env change)
docker compose down             # stop and remove the containers (keeps named volumes)
docker compose down -v          # ALSO remove volumes: bundle cache and SQLite scratch storage. Data in Neon is never touched
```

> The frontend is **not** part of this compose file — run it separately:
> `cd Frontend && npm install && npm run dev` (see the
> [frontend README](https://github.com/VSCRM/BookNest#readme)), or use the
> deployed build, which is compiled to talk to this backend.

> **Don't run this alongside** `auth-service/docker-compose.yml` (a
> standalone Postgres-only file for running `auth-service` outside Docker)
> — they'd fight over the same container names and ports. Use exactly one of
> the two setups at a time.

---

## 🛠️ Getting Started — Running Services Individually

Useful for active development on just one service, with fast reload/debugger
support.

### Rails (`BookNest/backend`)

Rails does not load `.env` files itself, so export the variables into your
shell first. With no variables set it runs in development mode on a local
SQLite file; with `RAILS_ENV=production` and the `DB_*` values it uses Neon.

```bash
cd BookNest/backend
bundle install

# Against Neon (production mode)
cp ../.env.example ../.env        # then fill in the Neon values and secrets
set -a; source ../.env; set +a
bin/rails db:prepare              # creates/updates tables in booknest_shop
bin/rails server -p 8080

# ...or plain local development on SQLite (no variables needed)
bin/rails db:prepare && bin/rails server -p 8080
```

### auth-service (Spring Boot)

Needs a PostgreSQL database. Point it at your Neon `booknest_auth` database
(recommended), or start a local one with the standalone
`auth-service/docker-compose.yml`:

```bash
cd auth-service
cp .env.example .env        # fill in the Neon DB_* values (DB_SSLMODE=require)
./mvnw spring-boot:run      # Hibernate creates the users table on first start

# Local Postgres instead of Neon (optional):
#   docker compose up -d    # Postgres only, on localhost:5433
#   ...and set DB_HOST=localhost, DB_PORT=5433, DB_SSLMODE=disable in .env
```

Kafka is optional for local development of `auth-service` alone — event
publishing fails fast and silently (`max.block.ms: 2000`) if no broker is
reachable, so authentication itself keeps working without one.

---

## ⚙️ Environment Variables

Three `.env.example` files exist — one per deployment shape:

- **`.env.example`** (repo root, identical to `auth-service/.env.example`) —
  used by the combined `docker-compose.yml` stack, which reads one shared
  `.env` for both processes. Fill in your Neon connection details.
- **`auth-service/.env.example`** — used when running `auth-service` on its
  own (`./mvnw spring-boot:run`), against Neon or a standalone local Postgres.
- **`BookNest/.env.example`** — used when running the Rails app on its own
  against Neon (`RAILS_ENV=production`).

| Variable                                    | Description                                                                                                              | Example / Default                                                                                     |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| `SERVER_PORT`                               | `auth-service`'s HTTP port                                                                                               | `9000`                                                                                                |
| `JWT_SECRET`                                | **Shared** HS256 signing secret — must be identical on both services                                                     | `openssl rand -hex 32`                                                                                |
| `JWT_ACCESS_MINUTES`                        | Access-token lifetime                                                                                                    | `15`                                                                                                  |
| `JWT_REFRESH_DAYS`                          | Refresh-token lifetime                                                                                                   | `7`                                                                                                   |
| `DB_HOST` / `DB_PORT`                       | PostgreSQL host/port for **both** services — the Neon endpoint host and `5432`                                           | `ep-xxx.eu-central-1.aws.neon.tech` / `5432` (local container: `localhost` / `5433`)                  |
| `DB_NAME`                                   | `auth-service`'s database name inside the Neon project                                                                   | `booknest_auth`                                                                                       |
| `RAILS_DB_NAME`                             | Rails' **own, separate** database name inside the same Neon project — must differ from `DB_NAME`                         | `booknest_shop`                                                                                       |
| `DB_USERNAME` / `DB_PASSWORD`               | Neon role and password (shared by both services)                                                                         | `neondb_owner` / _(from the Neon Console)_                                                            |
| `DB_SSLMODE`                                | `require` for Neon (mandatory); `disable` only for a local Postgres container                                            | `require` (Neon) / `disable` (local container)                                                        |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 app credentials                                                                                            | _(placeholder values allow the app to start — Google login itself will fail until real ones are set)_ |
| `FRONTEND_URL`                              | Where `auth-service` redirects back to after OAuth2                                                                      | `http://localhost:5174`                                                                               |
| `CORS_ALLOWED_ORIGINS`                      | Comma-separated origins allowed to call **both** backends with credentials                                               | `http://localhost:5174,http://localhost:5173,https://vscrm.github.io`                                 |
| `COOKIE_SECURE`                             | Must be `true` behind HTTPS; `false` only for local HTTP dev                                                             | `false` (local) / `true` (production)                                                                 |
| `COOKIE_SAME_SITE`                          | `Lax` for same-site local dev; `None` once frontend and backend are on different domains (requires `COOKIE_SECURE=true`) | `Lax` / `None`                                                                                        |
| `KAFKA_BOOTSTRAP_SERVERS`                   | Broker address `auth-service` publishes events to                                                                        | `kafka:9092` (compose) / `localhost:9092` (standalone)                                                |
| `SECRET_KEY_BASE`                           | Rails' production secret (no `credentials.yml.enc` in this repo, so this is what production Rails actually needs)        | `openssl rand -hex 64`                                                                                |
| `RAILS_ENV`                                 | `production` uses PostgreSQL (Neon); `development` uses a local SQLite file                                              | `production` (Neon) / `development`                                                                   |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD`            | Seeded admin account (same values on both services)                                                                      | `admin@booknest.local` / `admin12345`                                                                 |
| `ADMIN_ACCOUNTS`                            | Optional: seed several admins as `email1:pass1,email2:pass2` (overrides the pair above)                                  | —                                                                                                     |
| `GUEST_EMAIL` / `GUEST_PASSWORD`            | Seeded guest/demo account (Rails only)                                                                                   | `guest@booknest.local` / `guest12345`                                                                 |

> **Never** ship the placeholder `JWT_SECRET`, `GOOGLE_CLIENT_SECRET`,
> `SECRET_KEY_BASE`, or database passwords to a real deployment — they exist
> only so the stack boots out-of-the-box for local development.

---

## 🧪 Testing

### auth-service

```bash
cd auth-service
./mvnw test
```

Covers controllers (`AuthControllerTest`), services (`UserServiceTest`,
`JwtServiceTest`, `AuthEventPublisherTest`), security filters
(`JwtAuthenticationFilterTest`, `OAuth2LoginSuccessHandlerTest`,
`OAuth2LoginFailureHandlerTest`, `CustomAuthorizationRequestResolverTest`),
configuration (`SecurityConfigTest`, `KafkaProducerConfigTest`,
`KafkaTopicsTest`, `AdminSeederTest`, `AppConfigTest`,
`ConfigurationPropertiesRecordsTest`,
`DotenvEnvironmentPostProcessorTest`), domain/DTO/exception message tests
(`EnumsTest`, `UserTest`, `DtoTest`, `RequestValidationTest`,
`ExceptionMessagesTest`, `GlobalExceptionHandlerTest`), a repository test
(`UserRepositoryTest`, against an in-memory H2 database), and an end-to-end
`AuthServiceApplicationIT` integration test. `mvn verify` additionally
enforces a 90% line-coverage floor via the JaCoCo plugin configured in
`pom.xml`.

### Rails (`BookNest/backend`)

This service currently ships **without an automated test suite** — there is
no `test/` directory and no RSpec/Minitest configuration beyond the default
`config/environments/test.rb`. `rubocop` (used at runtime by the playground's
`/lint` endpoint) is the only static-analysis tool wired in. Contributions
adding request/model specs are welcome.

---

## 🏗️ Project Structure

Every file actually present in the repository (generated/build output —
`tmp/`, `log*/`, `storage/`, `.idea/`, `target/`, `node_modules/`, `release-assets/`, uploaded
cover images — is left out, as those aren't source), folders first then
files, sorted alphabetically at each level — the same order a file explorer
would show.

```
BookNest_backend/
├── .github/
│   └── workflows/
│       └── docker-publish.yml                                                            # Builds & publishes the Docker image to ghcr.io when a release is published
│
├── auth-service/                                                                         # Spring Boot authentication service
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/booknest/auth/
│   │   │   │   ├── config/
│   │   │   │   │   ├── AdminSeeder.java                                                  # Seeds ADMIN_EMAIL/ADMIN_ACCOUNTS on boot
│   │   │   │   │   ├── AppConfig.java
│   │   │   │   │   ├── AppProperties.java                                                # booknest.app.* config binding
│   │   │   │   │   ├── DotenvEnvironmentPostProcessor.java                               # Loads .env when run outside Docker
│   │   │   │   │   ├── JwtProperties.java                                                # booknest.jwt.* config binding
│   │   │   │   │   ├── KafkaProducerConfig.java
│   │   │   │   │   ├── KafkaTopics.java                                                  # Topic name constants
│   │   │   │   │   └── SecurityConfig.java                                               # Filter chain, OAuth2, CORS
│   │   │   │   ├── controller/
│   │   │   │   │   └── AuthController.java                                               # /api/auth/* endpoints (see API table)
│   │   │   │   ├── domain/
│   │   │   │   │   ├── AuthProvider.java                                                 # LOCAL / GOOGLE enum
│   │   │   │   │   ├── Role.java                                                         # CUSTOMER / ADMIN enum
│   │   │   │   │   └── User.java                                                         # JPA entity
│   │   │   │   ├── dto/
│   │   │   │   │   ├── AuthResponse.java
│   │   │   │   │   ├── ForgotPasswordRequest.java
│   │   │   │   │   ├── ForgotPasswordResponse.java
│   │   │   │   │   ├── LoginFailedEvent.java                                             # Kafka event payload
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── ResetPasswordRequest.java
│   │   │   │   │   ├── UpdateProfileRequest.java
│   │   │   │   │   ├── UserDto.java
│   │   │   │   │   ├── UserLoggedInEvent.java                                            # Kafka event payload
│   │   │   │   │   └── UserRegisteredEvent.java                                          # Kafka event payload
│   │   │   │   ├── exception/
│   │   │   │   │   ├── EmailAlreadyRegisteredException.java
│   │   │   │   │   ├── GlobalExceptionHandler.java                                       # @ControllerAdvice error mapping
│   │   │   │   │   ├── InvalidCredentialsException.java
│   │   │   │   │   └── InvalidResetCodeException.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── UserRepository.java                                               # Spring Data JPA repository
│   │   │   │   ├── security/
│   │   │   │   │   ├── CookieUtil.java                                                   # Builds the booknest_jwt / booknest_refresh cookies
│   │   │   │   │   ├── CustomAuthorizationRequestResolver.java                           # Preserves post-OAuth2 redirect target
│   │   │   │   │   ├── JwtAuthenticationFilter.java                                      # Verifies the access-token cookie per request
│   │   │   │   │   ├── OAuth2LoginFailureHandler.java
│   │   │   │   │   └── OAuth2LoginSuccessHandler.java                                    # Issues cookies after a Google login
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthEventPublisher.java                                           # Publishes to Kafka (fire-and-forget)
│   │   │   │   │   ├── JwtService.java                                                   # Signs/verifies/refreshes tokens
│   │   │   │   │   └── UserService.java                                                  # Register/login/update/reset business logic
│   │   │   │   └── AuthServiceApplication.java                                           # Spring Boot entry point
│   │   │   └── resources/
│   │   │       ├── META-INF/spring/
│   │   │       │   └── org.springframework.boot.env.EnvironmentPostProcessor.imports
│   │   │       ├── application.yml                                                       # Spring config (see Environment Variables)
│   │   │       └── log4j2.xml                                                            # Logging configuration
│   │   └── test/
│   │       ├── java/com/booknest/auth/
│   │       │   ├── config/
│   │       │   │   ├── AdminSeederTest.java
│   │       │   │   ├── AppConfigTest.java
│   │       │   │   ├── ConfigurationPropertiesRecordsTest.java
│   │       │   │   ├── DotenvEnvironmentPostProcessorTest.java
│   │       │   │   ├── KafkaProducerConfigTest.java
│   │       │   │   ├── KafkaTopicsTest.java
│   │       │   │   └── SecurityConfigTest.java
│   │       │   ├── controller/
│   │       │   │   └── AuthControllerTest.java
│   │       │   ├── domain/
│   │       │   │   ├── EnumsTest.java
│   │       │   │   └── UserTest.java
│   │       │   ├── dto/
│   │       │   │   ├── DtoTest.java
│   │       │   │   └── RequestValidationTest.java
│   │       │   ├── exception/
│   │       │   │   ├── ExceptionMessagesTest.java
│   │       │   │   └── GlobalExceptionHandlerTest.java
│   │       │   ├── repository/
│   │       │   │   └── UserRepositoryTest.java
│   │       │   ├── security/
│   │       │   │   ├── CookieUtilTest.java
│   │       │   │   ├── CustomAuthorizationRequestResolverTest.java
│   │       │   │   ├── JwtAuthenticationFilterTest.java
│   │       │   │   ├── OAuth2LoginFailureHandlerTest.java
│   │       │   │   └── OAuth2LoginSuccessHandlerTest.java
│   │       │   ├── service/
│   │       │   │   ├── AuthEventPublisherTest.java
│   │       │   │   ├── JwtServiceTest.java
│   │       │   │   └── UserServiceTest.java
│   │       │   └── AuthServiceApplicationIT.java                                         # End-to-end integration test
│   │       └── resources/
│   │           └── application.yml                                                       # Test profile config (in-memory H2)
│   ├── .env.example                                                                      # Standalone-run config (local Postgres)
│   ├── .gitignore
│   ├── docker-compose.yml                                                                # Standalone: optional local Postgres, for offline dev
│   ├── Dockerfile                                                                        # Standalone auth-service-only image
│   └── pom.xml                                                                           # Maven build + dependencies
│
├── BookNest/
│   ├── backend/                                                                          # Rails API
│   │   ├── app/
│   │   │   ├── assets/
│   │   │   │   ├── config/
│   │   │   │   │   └── manifest.js
│   │   │   │   ├── images/
│   │   │   │   │   └── .keep
│   │   │   │   └── stylesheets/
│   │   │   │       └── application.css
│   │   │   ├── controllers/
│   │   │   │   ├── api/v1/
│   │   │   │   │   ├── admin/
│   │   │   │   │   │   └── books_controller.rb                                           # Admin catalog CRUD (React admin panel)
│   │   │   │   │   ├── books_controller.rb                                               # Public, localized catalog (index/show)
│   │   │   │   │   ├── carts_controller.rb                                               # Guest + account cart JSON API
│   │   │   │   │   ├── orders_controller.rb                                              # Checkout + order history/detail
│   │   │   │   │   ├── playground_controller.rb                                          # Sandboxed Ruby code execution + lint
│   │   │   │   │   └── users_controller.rb                                               # Saved-books ("wishlist") JSON API
│   │   │   │   ├── application_controller.rb                                             # current_user via JWT, guest-cart merge
│   │   │   │   └── welcome_controller.rb                                                 # Hidden /welcome easter-egg page
│   │   │   ├── helpers/
│   │   │   │   └── application_helper.rb
│   │   │   ├── javascript/
│   │   │   │   └── application.js
│   │   │   ├── models/
│   │   │   │   ├── application_record.rb
│   │   │   │   ├── book.rb                                                               # Search/genre/sort scopes, i18n field lookup
│   │   │   │   ├── cart.rb
│   │   │   │   ├── cart_item.rb
│   │   │   │   ├── order.rb
│   │   │   │   ├── order_item.rb
│   │   │   │   ├── saved_book.rb
│   │   │   │   └── user.rb                                                               # Local shadow record — identity lives in auth-service
│   │   │   ├── services/
│   │   │   │   ├── cart_service.rb                                                       # Add/update/remove/checkout logic
│   │   │   │   └── jwt_authenticator.rb                                                  # Verifies auth-service's HS256 tokens
│   │   │   └── views/
│   │   │       ├── layouts/
│   │   │       │   └── welcome.html.erb
│   │   │       └── welcome/
│   │   │           └── index.html.erb
│   │   ├── bin/
│   │   │   ├── rails
│   │   │   └── setup
│   │   ├── config/
│   │   │   ├── environments/
│   │   │   │   ├── development.rb
│   │   │   │   ├── production.rb
│   │   │   │   └── test.rb
│   │   │   ├── initializers/
│   │   │   │   └── cors.rb                                                               # Reads CORS_ALLOWED_ORIGINS
│   │   │   ├── application.rb
│   │   │   ├── boot.rb
│   │   │   ├── cable.yml
│   │   │   ├── database.yml                                                              # SQLite (dev/test) + PostgreSQL (production)
│   │   │   ├── environment.rb
│   │   │   ├── importmap.rb
│   │   │   ├── puma.rb
│   │   │   ├── routes.rb                                                                 # All route definitions
│   │   │   └── storage.yml
│   │   ├── db/
│   │   │   ├── migrate/
│   │   │   │   ├── 20260101000001_create_books.rb
│   │   │   │   ├── 20260101000002_create_users.rb
│   │   │   │   ├── 20260101000003_create_carts.rb
│   │   │   │   ├── 20260101000004_create_cart_items.rb
│   │   │   │   ├── 20260101000005_create_orders.rb
│   │   │   │   ├── 20260101000006_create_order_items.rb
│   │   │   │   ├── 20260101000007_make_password_digest_nullable.rb
│   │   │   │   ├── 20260101000008_add_english_fields_to_books.rb
│   │   │   │   └── 20260101000009_create_saved_books.rb
│   │   │   ├── schema.rb
│   │   │   └── seeds.rb                                                                  # Admin/guest accounts + sample catalog
│   │   ├── public/
│   │   │   ├── docs/
│   │   │   │   └── index.html                                                            # Swagger UI shell
│   │   │   ├── uploads/
│   │   │   │   └── covers/                                                               # Uploaded book cover images (runtime)
│   │   │   └── openapi.yaml                                                              # OpenAPI spec served by Swagger UI
│   │   ├── .dockerignore
│   │   ├── .gitignore
│   │   ├── .ruby-version
│   │   ├── config.ru
│   │   ├── Dockerfile                                                                    # Standalone Rails-only image
│   │   ├── Gemfile
│   │   ├── Gemfile.lock
│   │   └── Rakefile
│   └── .env.example                                                                      # Template for running Rails standalone (PostgreSQL)
│
├── combined/                                                                             # Builds BOTH services into one container
│   ├── Dockerfile                                                                        # Multi-stage: Maven build → Ruby+JRE runtime
│   └── entrypoint.sh                                                                     # Clock sync, db:prepare, runs both processes
│
├── .env.example                                                                          # Template for the root combined-stack .env
├── .gitignore
├── docker-compose.yml                                                                    # Combined stack: Kafka + backend (+ optional local Postgres)
├── LICENSE                                                                               # MIT license
├── pack-release.sh                                                                       # Builds the release assets: source archive, Docker image archive, checksums
└── README.md
```

---

## ☁️ Deployment

BookNest is deployed as three independent pieces, and only the backend
container needs any care:

- **Frontend — GitHub Pages.** The React storefront is a static build published
  with `npm run deploy` (see the [frontend README](https://github.com/VSCRM/BookNest#readme)).
  It is compiled against this backend (`VITE_USE_MOCK=false`), not against
  sample data.
- **Backend — one Docker container.** The root `docker-compose.yml` runs the
  combined Rails + auth-service container (and Kafka) on any machine with
  Docker: your own computer or a VPS. Put a reverse proxy (nginx, Caddy, etc.)
  in front of ports `8080` and `9000` when it should be reachable from the
  internet, terminate TLS there, and set `COOKIE_SECURE=true`. The container
  keeps no critical state of its own, so it can be rebuilt or moved freely.
  Instead of building it yourself you can pull the prebuilt image from
  `ghcr.io` — see [Releases & Docker Image](#-releases--docker-image).
- **Database — Neon.** Every persistent record lives in the Neon project
  (`booknest_auth` and `booknest_shop`), which is managed, backed by Neon's own
  storage, and reachable from wherever the container runs. The only local
  state worth preserving is the `public/uploads/covers/` folder with uploaded
  book covers.

A few things to check before going live:

- **Secrets.** Replace the placeholder `JWT_SECRET`, `SECRET_KEY_BASE`,
  `GOOGLE_CLIENT_SECRET`, seeded admin/guest passwords, and use a dedicated
  Neon role instead of the project owner if you can. Never commit `.env`.
- **Cookies across domains.** Frontend and backend live on different sites, so
  keep `COOKIE_SAME_SITE=None` together with `COOKIE_SECURE=true` and serve
  the backend over HTTPS.
- **CORS.** Set `CORS_ALLOWED_ORIGINS` on both services to your deployed
  frontend's real origin(s); never widen it to `*` while credentials are
  enabled.
- **Split hosts.** The combined container exists specifically to avoid clock
  skew between two _separate_ hosts signing and verifying JWTs. If you do
  split the services, make sure both hosts are NTP-synced, or the
  "expired token" symptom described [above](#-why-one-combined-container) will
  resurface.
- **Schema management.** Rails tables are managed by migrations (`db:prepare`
  on every start). Switch `auth-service` from `ddl-auto: update` to a real
  migration tool before relying on it long term.

---

## 📦 Releases & Docker Image

Every release is cut from a git tag (`v1.0`, `v1.1`, …) and comes in two forms: a
ready-to-run **Docker image** published to the GitHub Container Registry, and —
optionally — offline **release assets** (a source archive, an image archive and
checksums) attached to the GitHub Release page. Two files in this repository take
care of it: `.github/workflows/docker-publish.yml` and `pack-release.sh`.

### Automatic image publishing — `.github/workflows/docker-publish.yml`

A GitHub Actions workflow runs whenever a GitHub Release is **published**. It checks
out the tagged commit, builds the combined image from `combined/Dockerfile`, and
pushes it to `ghcr.io/vscrm/booknest-backend` under two tags: the release tag
(for example `v1.0`) and `latest`. It authenticates with the built-in
`GITHUB_TOKEN`, so there are no secrets to configure, and it builds from a clean
checkout, so a local `.env` can never end up inside the image. Build layers are
cached between runs to keep repeat releases fast.

Two one-time settings on GitHub: after the first publish, open the package
(github.com/VSCRM → **Packages → booknest-backend → Package settings**) and change
its visibility to **Public**, otherwise `docker pull` will ask for a login. If the
package was pushed manually before, also add this repository under **Manage Actions
access** with the **Write** role.

### Offline release assets — `pack-release.sh`

The script builds everything worth attaching to a release page in one go. Run it
from the repository root on Linux or macOS (on Windows use Git Bash or WSL) with
Docker running:

```bash
chmod +x pack-release.sh     # once
./pack-release.sh 1.0

# Building on Apple Silicon for a regular Linux server?
PLATFORM=linux/amd64 ./pack-release.sh 1.0
```

It writes three files to `release-assets/` (add that folder to `.gitignore`):

| File                                       | Purpose                                                                                                     |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| `BookNest-backend-v1.0-source.tar.gz`      | Tracked source files at tag `v1.0` (or at `HEAD` if the tag doesn't exist yet) — no `.env`, no build output |
| `booknest-backend-1.0-docker-image.tar.gz` | The combined image, restored with `docker load`                                                             |
| `SHA256SUMS.txt`                           | Checksums for both archives (`sha256sum -c SHA256SUMS.txt` verifies them)                                   |

Before saving the image the script checks that no `/rails/.env` was baked into it
and stops with an error if one was. It also warns about uncommitted changes and
about archives above GitHub's 2 GiB limit per release asset — in that case rely on
the registry image instead of attaching the archive.

### Cutting a release

1. Commit and push everything to `main`, including the two files above.
2. Optionally run `./pack-release.sh 1.0` to create the offline assets.
3. On GitHub open **Releases → Draft a new release**, create the tag `v1.0` on
   publish, paste the release notes, drop the files from `release-assets/` into
   **Attach binaries**, and click **Publish release**.
4. Watch the **Actions** tab. When the run turns green, the image is available as
   `ghcr.io/vscrm/booknest-backend:v1.0`.

### Running the published image

```bash
docker pull ghcr.io/vscrm/booknest-backend:v1.0

docker run -d --name booknest_backend --restart unless-stopped \
  --env-file .env \
  -p 8080:3000 -p 9000:9000 \
  --cap-add SYS_TIME \
  ghcr.io/vscrm/booknest-backend:v1.0
```

A few details to keep in mind:

- `.env` is the same file as in [Getting Started](#-getting-started--docker-recommended),
  but with real values instead of placeholders. Docker's `--env-file` keeps quotes
  literally and does not support trailing comments, so keep each line a plain
  `KEY=value`.
- `--cap-add SYS_TIME` lets the entrypoint sync the container's clock, exactly as in
  `docker-compose.yml`.
- Kafka is optional here: without a broker authentication keeps working, the events
  just aren't published. With a broker add
  `-e SPRING_KAFKA_BOOTSTRAP_SERVERS=host:9092`.
- Uploaded book covers live on the container's filesystem; mount a folder to keep
  them, e.g. `-v "$(pwd)/covers:/rails/public/uploads/covers"`.
- To run from the offline archive instead, use `docker load -i
booknest-backend-1.0-docker-image.tar.gz` and start the local image
  `booknest-backend:1.0` with the same `docker run` options. (The script tags the
  local image with the plain version number, while the registry tags follow the git
  tag, including the leading `v`.)

---

## 📄 License

Released under the [MIT License](./LICENSE).

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
