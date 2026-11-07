# Spring Boot Course Project: Bookshelf API

A REST API for managing books, built step by step as a Spring Boot course. It grows from a simple CRUD API into a system where database changes flow through Kafka to refresh a Redis cache.

**Stack:** Spring Boot 3.5 · Java 17 · Spring Web · Spring Data JPA · PostgreSQL · Redis · Apache Kafka · Debezium (Kafka Connect) · RestClient · Maven · Docker Compose

## Branches

| Branch | Contents |
|---|---|
| `v1` | REST API, Postgres, Redis cache (`@Cacheable`/`@CachePut`/`@CacheEvict`), RestClient |
| `v2` | Kafka pipeline with a transactional outbox and a relay (app-written events) |
| `v3` | **This README.** Change data capture (CDC) with Debezium: the database's change log feeds Kafka directly. No outbox. |

## What it does

- **Books API** (`/api/books`): create, read, update, delete, list, and search by author. Books are stored in PostgreSQL. Reads are cached in Redis.
- **Change data capture:** Debezium reads Postgres's write-ahead log and publishes every change to the Kafka topic `bookshelf.public.book`. The app doesn't write any event rows.
- **Cache invalidation:** a Kafka listener reads those changes and removes the matching Redis entry, so the next read loads fresh data.
- **Posts proxy** (`/api/posts/{id}`): fetches a post from `jsonplaceholder.typicode.com` through a `RestClient`.
- **Errors** are returned as `ProblemDetail` JSON (`application/problem+json`).

## How the pieces connect

```
 POST / PUT / DELETE /api/books
           │
           ▼
 BookService ──► Postgres 'book' table
                        │
                        │ write-ahead log (wal_level=logical)
                        ▼
              Debezium connector (in Kafka Connect)
                        │
                        ▼
              Kafka topic "bookshelf.public.book"
                        │
                        │ BookEventListener
                        ▼
              Redis: evict books::{id}

 GET /api/books/{id}  ──► Redis hit?  yes → return cached book
                              │ no
                              ▼
                        read Postgres, store in Redis (@Cacheable)
```

**Why CDC?** The database already records every committed change in its log. Debezium reads that log, so the app doesn't have to publish anything. Nothing can be saved without its change being captured, and a change can't be captured unless it was committed.

Debezium messages look like this (plain JSON, without the schema):

```json
{"before": null,
 "after": {"id": 5, "title": "Persuasion", "author": "Jane Austen", "published_year": 1817},
 "op": "c"}
```

- `op` is `r` (initial snapshot), `c` (create), `u` (update), or `d` (delete).
- `after` holds the row after the change. For a delete, `after` is `null` and `before` holds the row id.
- After each delete, Debezium also sends a **tombstone**: a message with an empty value. The listener ignores it.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker Desktop | any recent | `docker compose version` |
| curl | any | `curl --version` |

If `docker` is not found in your terminal, open Docker Desktop once. On macOS, its command-line tool is at `/Applications/Docker.app/Contents/Resources/bin/docker`.

## 1. Start the infrastructure

Postgres, Redis, Kafka, and Kafka Connect run in Docker. The definitions are in `docker-compose.yml`.

```bash
docker compose up -d
docker compose ps
```

These containers should show `running`: `bookshelf-postgres`, `bookshelf-redis`, `bookshelf-kafka`, and `bookshelf-connect`.

Check each service:

```bash
docker exec bookshelf-redis redis-cli ping
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "show wal_level;"
docker exec bookshelf-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
curl -s localhost:8083/
```

- Redis prints `PONG`.
- `wal_level` prints `logical`. Debezium needs this.
- Kafka lists its topics.
- Kafka Connect returns JSON with a `version` field.

Kafka has two addresses. Your Mac uses `localhost:9092`. Containers, including Kafka Connect, use `kafka:29092`.

To stop the containers: `docker compose down`. Data is kept in Docker volumes, so it survives `down`. To delete it too: `docker compose down -v`.

## 2. Register the Debezium connector (once)

The connector tells Debezium which database and table to watch. The config is in `debezium/book-connector.json`.

```bash
curl -s -X POST localhost:8083/connectors -H 'Content-Type: application/json' -d @debezium/book-connector.json
curl -s localhost:8083/connectors/bookshelf-postgres-connector/status
```

The status should show the connector and its task as `RUNNING`.

If you change the config file later, apply it to the running connector:

```bash
curl -s -X PUT localhost:8083/connectors/bookshelf-postgres-connector/config -H 'Content-Type: application/json' \
  -d "$(python3 -c 'import json; print(json.dumps(json.load(open("debezium/book-connector.json"))["config"]))')"
```

The first time the connector starts, it reads every existing book as a snapshot (`op = r`). After that, it reads only new changes.

## 3. Run the application

```bash
mvn spring-boot:run
```

The app starts on <http://localhost:8080>. Hibernate creates the `book` table if it doesn't exist. The Kafka listener starts reading `bookshelf.public.book`.

The application logs one line per step, with a tag in front:

| Tag | Meaning |
|---|---|
| `[DB]` | a book was saved, updated, or deleted |
| `[KAFKA]` | the listener received a change (`op` and book id) |
| `[REDIS]` | a cache entry was evicted, or there was nothing to evict |
| `[CACHE MISS]` | the book wasn't in Redis, so it was read from Postgres |

## 4. Run the tests

```bash
mvn test
```

Expected result: `Tests run: 23, Failures: 0, Errors: 0`.

| Test | What it checks | Needs |
|---|---|---|
| `BookServiceTest` | service logic with a mock repository, including page size | nothing |
| `BookRepositoryTest` | queries and entity mapping | nothing (in-memory H2) |
| `BookControllerTest` | HTTP status codes and validation | nothing |
| `BookCacheTest` | cache hits and misses; a write leaves the cache alone until an event evicts it | Redis running |
| `BookEventListenerTest` | update and delete messages remove the cached book; tombstones, old-format messages, and malformed messages are handled | Redis running |
| `HelloControllerTest` | the starter endpoints | nothing |

The cache tests use an in-memory database and switch off the Kafka listener, so they don't depend on the running app.

## 5. Sample data

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

## 6. Try the API

```bash
# list all books (max 50, from app.max-page-size)
curl -s localhost:8080/api/books

# one book (cached in Redis after the first read)
curl -s localhost:8080/api/books/1

# search by author, case-insensitive and partial
curl -s "localhost:8080/api/books/search?author=martin"

# update book 1
curl -s -X PUT localhost:8080/api/books/1 -H 'Content-Type: application/json' \
  -d '{"title":"Clean Code 2nd Edition","author":"Robert C. Martin","publishedYear":2009}'

# delete book 1 (returns 204)
curl -i -X DELETE localhost:8080/api/books/1

# validation error: 400 with a list of field errors
curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"","author":"","publishedYear":1000}'

# external API through RestClient
curl -s localhost:8080/api/posts/1

# missing post: 404 with "Post not found"
curl -i -s localhost:8080/api/posts/9999
```

## 7. Test the pipeline

Use three terminal windows. Start the app in window 1.

**Window 2: watch the change topic**

```bash
docker exec -it bookshelf-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bookshelf.public.book --from-beginning
```

Leave it open. Each database change appears as one JSON line.

**Window 3: run the scenarios below.**

### Scenario A: cache miss, then cache hit

```bash
curl -s localhost:8080/api/books/1
curl -s localhost:8080/api/books/1
docker exec bookshelf-redis redis-cli keys '*'
```

- The first read logs `[CACHE MISS]`. The second logs nothing, because Redis answered.
- `books::1` appears in the key list.

### Scenario B: an update removes the cached entry

```bash
curl -s localhost:8080/api/books/1
curl -s -X PUT localhost:8080/api/books/1 -H 'Content-Type: application/json' \
  -d '{"title":"Clean Code (revised)","author":"Robert Martin","publishedYear":2009}'
sleep 3
docker exec bookshelf-redis redis-cli keys '*'
curl -s localhost:8080/api/books/1
```

The app log should show:

```
[DB] updated book 1 ...
[KAFKA] received op=u for book 1
[REDIS] evicted books::1 ...
```

The consumer window shows a message with `"op":"u"`. `books::1` is gone from Redis. The next read logs `[CACHE MISS]` and returns the new title.

### Scenario C: delete

```bash
curl -s localhost:8080/api/books/2
curl -i -X DELETE localhost:8080/api/books/2
sleep 3
docker exec bookshelf-redis redis-cli keys '*'
curl -i -s localhost:8080/api/books/2
```

- The `DELETE` returns `204`.
- The consumer shows a `"op":"d"` message, followed by a tombstone (an empty value).
- The log shows `[KAFKA] received op=d for book 2`, then `[REDIS] evicted books::2` if it was cached.
- The last `GET` returns `404`.

### Scenario D: a write while Kafka is down

```bash
docker compose stop kafka
curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"Emma","author":"Jane Austen","publishedYear":1815}'
docker compose start kafka
sleep 15
```

The book is saved in Postgres right away. Debezium holds the change in Postgres's log until Kafka accepts it, so the change isn't lost. Once Kafka is back, the consumer window shows the `"op":"c"` message for Emma.

If the connector stops for a long time, check its status (`curl -s localhost:8083/connectors/bookshelf-postgres-connector/status`) and restart it if it's `FAILED`.

### Useful checks

```bash
# cache keys in Redis
docker exec bookshelf-redis redis-cli keys '*'

# is the listener connected and caught up? LAG should be 0
docker exec bookshelf-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group bookshelf-cache

# connector and task state
curl -s localhost:8083/connectors/bookshelf-postgres-connector/status
```

## Configuration

Settings are in `src/main/resources/application.properties`:

| Property | Value | Meaning |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/bookshelf` | Postgres connection |
| `spring.jpa.hibernate.ddl-auto` | `update` | create or extend tables, keep data |
| `spring.cache.type` | `redis` | use Redis for `@Cacheable` |
| `spring.cache.redis.time-to-live` | `10m` | cached entries expire after 10 minutes |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker, from your Mac |
| `spring.kafka.consumer.group-id` | `bookshelf-cache` | the listener's consumer group |
| `spring.kafka.consumer.value-deserializer` | `StringDeserializer` | the listener parses the JSON itself |
| `app.max-page-size` | `50` | maximum books returned by `GET /api/books` |

The connector config is in `debezium/book-connector.json`:

| Setting | Meaning |
|---|---|
| `database.hostname` | `postgres`, the Compose service name (Connect runs in Docker) |
| `topic.prefix` + `table.include.list` | the topic `bookshelf.public.book` for `public.book` |
| `plugin.name` | `pgoutput`, the built-in logical decoding plugin |
| `slot.name` | the replication slot. Postgres keeps the log until Debezium reads it |
| `*.converter` (JSON, schemas off) | plain JSON messages, without the large schema block |

> The database username and password in these files are for local development only. Don't reuse them in any shared environment.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Connection refused` on 5432, 6379, 9092, or 8083 | Run `docker compose up -d` and check `docker compose ps`. |
| `Port 8080 already in use` | A previous app is still running. Stop it with `lsof -t -i :8080 \| xargs kill`. |
| `show wal_level` is not `logical` | Recreate Postgres with the `command:` line in `docker-compose.yml`. |
| Connector status is `FAILED` | Read the `trace` field in the status output. A wrong password or a missing replication slot are the usual causes. |
| `bookshelf.public.book` topic does not exist | Register the connector (section 2). The topic appears after the first change or snapshot. |
| No `[KAFKA]` lines after an update | Restart the app so it runs the current code. Check that the group `bookshelf-cache` shows lag 0. |
| Key stays in Redis after an update | Wait a few seconds, then check the `[KAFKA]` and `[REDIS]` lines. |
| `Table "book" not found` | Start the app once so Hibernate creates the table. |
| `docker: command not found` | Use the full path, or add Docker's `bin` folder to your `PATH`. |

## Known limits

- **Short stale window:** a read can return the old book for a moment between the database write and the cache eviction.
- **Ordering per book only:** messages for the same book stay in order, which is what matters for eviction. Order across different books isn't guaranteed.
- **Replication slot:** if the connector stays down for a long time, Postgres keeps growing its log. Monitor the connector, or drop the slot if you no longer need it.
- **Schema changes:** adding a column to `book` is picked up by Debezium, but the listener only reads `id`, so it's unaffected. Renaming or removing `id` would break it.
- **Several listeners:** each consumer group gets every message. A second service that caches books needs its own group id.

## Project layout

```
debezium/
└── book-connector.json      Debezium connector config (registered once)
src/main/java/com/example/simpleapp/
├── controller/   REST endpoints (BookController, PostController)
├── service/      business logic and @Cacheable reads (BookService)
├── repository/   JPA repository for books (BookRepository)
├── model/        JPA entity (Book)
├── event/        Kafka listener that evicts cached books (BookEventListener)
├── dto/          request objects with validation (BookRequest)
├── client/       RestClient for the external API (PostClient, Post)
├── config/       beans and configuration properties
├── exception/    error handling (GlobalExceptionHandler)
└── utils/        helpers (BookHelper)
```
