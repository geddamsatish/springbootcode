# Spring Boot Course Project: Bookshelf API

A REST API for managing books, built step by step as a Spring Boot course.

**Stack:** Spring Boot 3.5 · Java 17 · Spring Web · Spring Data JPA · PostgreSQL · Redis cache · RestClient · Maven · Docker Compose

## What it does

- **Books API** (`/api/books`): create, read, update, delete, list, and search by author. Data is stored in PostgreSQL. Single-book reads are cached in Redis.
- **Posts proxy** (`/api/posts/{id}`): fetches a post from the public API `jsonplaceholder.typicode.com` through a `RestClient`.
- **Errors** are returned as `ProblemDetail` JSON (`application/problem+json`).

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker Desktop | any recent | `docker compose version` |
| curl | any | `curl --version` |

If `docker` is not found in your terminal, open Docker Desktop once. On macOS, its command-line tool is at `/Applications/Docker.app/Contents/Resources/bin/docker`.

## 1. Start the infrastructure

PostgreSQL and Redis run in Docker. The definitions are in `docker-compose.yml`.

```bash
docker compose up -d
docker compose ps
```

Both `bookshelf-postgres` and `bookshelf-redis` should show `running`.

Check that each service answers:

```bash
docker exec bookshelf-redis redis-cli ping
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "select 1;"
```

Redis should print `PONG`.

To stop the containers later: `docker compose down`. Data in Postgres is kept in a Docker volume, so it survives `down` and restarts. To delete it too: `docker compose down -v`.

## 2. Run the application

```bash
mvn spring-boot:run
```

The app starts on <http://localhost:8080>. On startup, Hibernate creates the `book` table in Postgres if it doesn't exist yet.

## 3. Run the tests

```bash
mvn test
```

Expected result: `Tests run: 18, Failures: 0, Errors: 0`.

The repository and cache tests use an in-memory H2 database, so they don't need Postgres. The cache test does use the running Redis container, so start Docker first.

## 4. Sample data

Start the app first. Then load the sample books with this loop:

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

Each line prints the saved book with its new `id`.

## 5. Try the API

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

# delete book 1 (returns 204 with no body)
curl -i -X DELETE localhost:8080/api/books/1

# validation error: returns 400 with a list of field errors
curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"","author":"","publishedYear":1000}'

# external API through RestClient
curl -s localhost:8080/api/posts/1

# missing post: returns 404 with "Post not found"
curl -i -s localhost:8080/api/posts/9999
```

### Check the cache and database

```bash
# cache keys in Redis (one key per cached book, e.g. books::1)
docker exec bookshelf-redis redis-cli keys '*'

# rows in Postgres
docker exec bookshelf-postgres psql -U bookshelf -d bookshelf -c "select id, title, author from book;"
```

Each `GET /api/books/{id}` adds a key to Redis. Each `PUT` refreshes it. Each `DELETE` removes it.

## Configuration

Settings are in `src/main/resources/application.properties`:

| Property | Value | Meaning |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/bookshelf` | Postgres connection |
| `spring.jpa.hibernate.ddl-auto` | `update` | create or extend tables, keep data |
| `spring.cache.type` | `redis` | use Redis for `@Cacheable` |
| `spring.cache.redis.time-to-live` | `10m` | cached entries expire after 10 minutes |
| `app.max-page-size` | `50` | maximum books returned by `GET /api/books` |

> The database username and password in this file are for local development only. Don't reuse them in any shared environment.

## Troubleshooting

| Problem | Fix |
|---|---|
| `Connection refused` on port 5432 or 6379 | Run `docker compose up -d` and check `docker compose ps`. |
| `Port 8080 already in use` | A previous app is still running. Stop it with `lsof -t -i :8080 \| xargs kill`. |
| `Table "book" not found` | Start the app once so Hibernate creates the table. |
| Cached book looks outdated | Check whether the change went through `PUT` or `DELETE`. Direct SQL updates bypass the cache, so the entry lasts until its 10-minute expiry. |
| `docker: command not found` | Use the full path, or add Docker's `bin` folder to your `PATH`. |

## Project layout

```
src/main/java/com/example/simpleapp/
├── controller/   REST endpoints (BookController, PostController)
├── service/      business logic and caching (BookService)
├── repository/   Spring Data JPA repository (BookRepository)
├── model/        JPA entity (Book)
├── dto/          request objects with validation (BookRequest)
├── client/       RestClient for the external API (PostClient, Post)
├── config/       beans and configuration properties
├── exception/    error handling (GlobalExceptionHandler)
└── utils/        helpers (BookHelper)
```

## Course docs

Work in progress. The next branch, `v2`, adds Kafka: a database change is published as an event, and a consumer updates the Redis cache.
