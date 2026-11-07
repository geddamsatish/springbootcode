# Spring Boot Course Project: Bookshelf API

A REST API for managing books, built step by step as a Spring Boot course. It grows from a simple CRUD API into an event-driven system with a Postgres database, a Redis cache, and Kafka.

**Stack:** Spring Boot 3.5 · Java 17 · Spring Web · Spring Data JPA · PostgreSQL · Redis · Apache Kafka · RestClient · Maven · Docker Compose

## Branches

| Branch | Contents |
|---|---|
| `v1` | REST API, Postgres, Redis cache (`@Cacheable`/`@CachePut`/`@CacheEvict`), RestClient |
| `v2` | Kafka pipeline: transactional outbox, relay, Redis invalidation by events (this README) |
| `v3` | Next stage (see the branch for details) |

## What it does

- **Books API** (`/api/books`): create, read, update, delete, list, and search by author. Books are stored in PostgreSQL. Reads are cached in Redis.
- **Change events:** every create, update, and delete writes an event to an `outbox` table in the same transaction as the change. A relay publishes those events to the Kafka topic `book-changes`.
- **Cache invalidation:** a Kafka listener removes the cached book from Redis when an event arrives, so the next read loads the fresh data.
- **Posts proxy** (`/api/posts/{id}`): fetches a post from `jsonplaceholder.typicode.com` through a `RestClient`.
- **Errors** are returned as `ProblemDetail` JSON (`application/problem+json`).

## How the pieces connect

```
 POST / PUT / DELETE /api/books
           │
           ▼
 BookService ──(one transaction)──► books table  +  outbox table (published = false)
                                                        │
                                                        │ OutboxRelay, every 1 s
                                                        ▼
                                              Kafka topic "book-changes"
                                                        │
                                                        │ BookEventListener
                                                        ▼
                                              Redis: evict books::{id}

 GET /api/books/{id}  ──► Redis hit?  yes → return cached book
                              │ no
                              ▼
                        read Postgres, store in Redis (@Cacheable)
```

Why an outbox? If the app saved the book and then sent the event directly, a crash between the two steps would leave them out of sync. The outbox writes both in one transaction, so the event exists if and only if the change was saved. The relay sends it later, and retries until Kafka confirms it.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker Desktop | any recent | `docker compose version` |
| curl | any | `curl --version` |

If `docker` is not found in your terminal, open Docker Desktop once. On macOS, its command-line tool is at `/Applications/Docker.app/Contents/Resources/bin/docker`.

## 1. Start the infrastructure

PostgreSQL, Redis, and Kafka run in Docker. The definitions are in `docker-compose.yml`.

```bash
docker compose up -d
docker compose ps
```

All three containers (`bookshelf-postgres`, `bookshelf-redis`, `bookshelf-kafka`) should show `running`.

Check that each service answers:

```bash
docker exec bookshelf-redis redis-cli ping
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "select 1;"
docker exec bookshelf-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Redis prints `PONG`. Postgres prints a result table. Kafka lists its topics.

If `book-changes` is missing from the list, create it once:

```bash
docker exec bookshelf-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic book-changes --partitions 3 --replication-factor 1
```

To stop the containers: `docker compose down`. Data is kept in Docker volumes, so it survives `down`. To delete it too: `docker compose down -v`.

## 2. Run the application

```bash
mvn spring-boot:run
```

The app starts on <http://localhost:8080>. On startup, Hibernate creates the `book` and `outbox` tables if they don't exist, and the Kafka listener starts consuming `book-changes`.

The application logs one line per step of the pipeline, with a tag in front:

| Tag | Meaning |
|---|---|
| `[DB]` | a book was saved, updated, or deleted |
| `[OUTBOX]` | an event was recorded, or a row was sent to Kafka |
| `[KAFKA]` | the listener received an event |
| `[REDIS]` | a cache entry was evicted, or there was nothing to evict |
| `[CACHE MISS]` | the book wasn't in Redis, so it was read from Postgres |

## 3. Run the tests

```bash
mvn test
```

Expected result: `Tests run: 26, Failures: 0, Errors: 0`.

| Test | What it checks | Needs |
|---|---|---|
| `BookServiceTest` | service logic and outbox rows, with mocks | nothing |
| `OutboxRelayTest` | order, marking rows published, stopping on failure | nothing |
| `BookRepositoryTest` | queries and entity mapping | nothing (in-memory H2) |
| `BookControllerTest` | HTTP status codes and validation | nothing |
| `BookCacheTest` | cache hits and misses | Redis running |
| `BookEventListenerTest` | a `books::{id}` entry is removed when an event arrives | Redis running |
| `HelloControllerTest` | the starter endpoints | nothing |

The cache tests use an in-memory database and turn off Kafka and the relay, so they don't depend on your running app.

## 4. Sample data

Start the app first. Then load the sample books:

```bash
for book in \
  '{"title":"Clean Code","author":"Robert Martin","publishedYear":2008}' \
  '{"title":"Effective Java","author":"Joshua Bloch","publishedYear":2018}' \
  '{"title":"Refactoring","author":"Martin Fowler","publishedYear":1999}' \
  '{"title":"Dune","author":"Frank Herbert","publishedYear":1965}' \
  '{"title":"The Pragmatic Programmer","author":"Andrew Hunt","publishedYear":1999}'
do
  curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' -d "$book"
  echo
done
```

Each line prints the saved book with its new `id`. Each create also writes a `CREATED` event.

## 5. Try the API

```bash
# list all books (max 50, from app.max-page-size)
curl -s localhost:8080/api/books

# one book (cached in Redis after the first read)
curl -s localhost:8080/api/books/1

# search by author, case-insensitive and partial
curl -s "localhost:8080/api/books/search?author=martin"

# update book 1 (writes an UPDATED event)
curl -s -X PUT localhost:8080/api/books/1 -H 'Content-Type: application/json' \
  -d '{"title":"Clean Code 2nd Edition","author":"Robert C. Martin","publishedYear":2009}'

# delete book 1 (writes a DELETED event; returns 204)
curl -i -X DELETE localhost:8080/api/books/1

# validation error: 400 with a list of field errors
curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"","author":"","publishedYear":1000}'

# external API through RestClient
curl -s localhost:8080/api/posts/1

# missing post: 404 with "Post not found"
curl -i -s localhost:8080/api/posts/9999
```

## 6. Test the pipeline

Use three terminal windows. Start the app in window 1.

**Window 2: watch the Kafka topic**

```bash
docker exec -it bookshelf-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic book-changes --from-beginning
```

Leave it open. Each event appears as one JSON line.

**Window 3: run the scenarios below.**

### Scenario A: cache miss, then cache hit

```bash
curl -s localhost:8080/api/books/1
curl -s localhost:8080/api/books/1
docker exec bookshelf-redis redis-cli keys '*'
```

- The first read logs `[CACHE MISS]`. The second read logs nothing, because Redis answered.
- `books::1` appears in the Redis key list.

### Scenario B: update removes the cached entry

```bash
curl -s localhost:8080/api/books/1
curl -s -X PUT localhost:8080/api/books/1 -H 'Content-Type: application/json' \
  -d '{"title":"Clean Code (revised)","author":"Robert Martin","publishedYear":2009}'
sleep 3
docker exec bookshelf-redis redis-cli keys '*'
curl -s localhost:8080/api/books/1
```

The app log should show, in order:

```
[DB] updated book 1 ...
[OUTBOX] recorded UPDATED event for book 1 ...
[OUTBOX] published outbox row N ...
[KAFKA] received UPDATED event for book 1
[REDIS] evicted books::1 ...
```

The consumer window shows the `UPDATED` JSON. `books::1` is gone from Redis. The next read logs `[CACHE MISS]` and returns the new title.

### Scenario C: delete

```bash
curl -s localhost:8080/api/books/2
curl -i -X DELETE localhost:8080/api/books/2
sleep 3
docker exec bookshelf-redis redis-cli keys '*'
curl -i -s localhost:8080/api/books/2
```

- The `DELETE` returns `204`. The log shows `[OUTBOX] recorded DELETED event`, then `[KAFKA] received DELETED event`, then `[REDIS] evicted books::2` (if it was cached).
- The last `GET` returns `404`.

### Scenario D: Kafka is down (why the outbox exists)

```bash
docker compose stop kafka
curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"Emma","author":"Jane Austen","publishedYear":1815}'
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "select id, event_type, published from outbox order by id desc limit 1;"
```

The book is saved, and the newest outbox row shows `published = f`. The app log shows `[OUTBOX] could not publish row N; stopping, will retry`.

```bash
docker compose start kafka
sleep 10
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "select id, event_type, published from outbox order by id desc limit 1;"
```

The row now shows `published = t`. The consumer window shows the `CREATED` event for Emma. Restart the consumer window if it closed when Kafka stopped.

### Useful checks

```bash
# outbox rows: published = t means Kafka confirmed the send
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "select id, aggregate_id, event_type, published from outbox order by id desc limit 10;"

# cache keys in Redis
docker exec bookshelf-redis redis-cli keys '*'

# is the listener connected and caught up? LAG should be 0
docker exec bookshelf-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group bookshelf-cache
```

## Configuration

Settings are in `src/main/resources/application.properties`:

| Property | Value | Meaning |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/bookshelf` | Postgres connection |
| `spring.jpa.hibernate.ddl-auto` | `update` | create or extend tables, keep data |
| `spring.cache.type` | `redis` | use Redis for `@Cacheable` |
| `spring.cache.redis.time-to-live` | `10m` | cached entries expire after 10 minutes |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker |
| `spring.kafka.producer.value-serializer` | `StringSerializer` | the relay sends JSON text |
| `spring.kafka.consumer.group-id` | `bookshelf-cache` | the listener's consumer group |
| `spring.kafka.consumer.value-deserializer` | `JsonDeserializer` | turns event JSON into `BookEvent` |
| `app.max-page-size` | `50` | maximum books returned by `GET /api/books` |
| `app.outbox.enabled` | `true` (default) | set to `false` to turn off the relay |

> The database username and password in this file are for local development only. Don't reuse them in any shared environment.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Connection refused` on 5432, 6379, or 9092 | Run `docker compose up -d` and check `docker compose ps`. |
| `Port 8080 already in use` | A previous app is still running. Stop it with `lsof -t -i :8080 \| xargs kill`. |
| Outbox row stays `published = f` | Kafka is down or unreachable. Check `docker compose ps`, then the app log for `[OUTBOX] could not publish`. The row is retried automatically. |
| No `[KAFKA]` lines after an update | The app may be an older build. Restart it. Check that the consumer group `bookshelf-cache` shows lag 0. |
| Key stays in Redis after an update | Wait about 3 seconds. The relay runs every second, then the listener. If it still stays, check the `[KAFKA]` and `[REDIS]` lines. |
| `Table "book" not found` | Start the app once so Hibernate creates the tables. |
| `docker: command not found` | Use the full path, or add Docker's `bin` folder to your `PATH`. |

## Known limits

- **Short stale window:** a read can return the old book for about a second, between the database write and the cache eviction.
- **Several app instances:** two running copies could send the same outbox row twice. A fix needs row locking (for example, `SELECT ... FOR UPDATE SKIP LOCKED`).
- **Outbox grows:** published rows are never deleted. A cleanup job would fix this.
- **Pre-outbox data:** books created before the outbox existed have no events. Only changes made after the outbox was added are published.

## Project layout

```
src/main/java/com/example/simpleapp/
├── controller/   REST endpoints (BookController, PostController)
├── service/      business logic and @Cacheable reads (BookService)
├── repository/   JPA repository for books (BookRepository)
├── model/        JPA entity (Book)
├── outbox/       outbox table, repository, and relay (OutboxEvent, OutboxRelay)
├── event/        Kafka event record and the cache-invalidating listener (BookEvent, BookEventListener)
├── dto/          request objects with validation (BookRequest)
├── client/       RestClient for the external API (PostClient, Post)
├── config/       beans and configuration properties
├── exception/    error handling (GlobalExceptionHandler)
└── utils/        helpers (BookHelper)
```
