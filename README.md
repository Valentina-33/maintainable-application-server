# Maintainable Application Server

A small Java web framework, built on top of a sequential socket-based HTTP server, that lets a
developer register GET routes as lambda functions instead of hardcoding them inside the
connection-handling loop. It serves static files (HTML/CSS/JS/images), extracts query-string
parameters, reads its configuration from environment variables, and supports a graceful,
sequential shutdown.

## 1. Architecture

```
Application (edu.eci.arsw.app)
    Registers routes and static-files location
        |
WebFramework (edu.eci.arsw.webframework)
    Exposes staticfiles(), get(), start(), stop()
        |
Router
    Maps a path to a lambda handler (RouteHandler)
        |
HttpServer
    Accepts connections one at a time, parses requests, writes responses
        |
Request / Response
    Small wrappers the lambda receives: query params in, content-type/status out
        |
StaticFileService
    Serves a file from the configured static-files location when no route matches
```

Adding a new endpoint means calling `get(...)` once in `Application`. Nothing in `Router` or
`HttpServer` changes. That is the difference from a server that has `if (path.equals("/hello"))`
written directly inside its accept loop.

### Responsibilities of the main components

| Component | Package | Responsibility |
|---|---|---|
| `Application` | `edu.eci.arsw.app` | Registers the app's own routes and static-files location. The only class a developer using the framework needs to touch. |
| `WebFramework` | `edu.eci.arsw.webframework` | Static facade (`staticfiles`, `get`, `start`, `stop`) that hides the `Router`/`HttpServer` wiring from the application. |
| `Router` | `edu.eci.arsw.webframework` | Registry that maps a path to a `RouteHandler`. No knowledge of sockets or HTTP parsing. |
| `RouteHandler` | `edu.eci.arsw.webframework` | Functional interface `(Request, Response) -> Object`, the contract a lambda must satisfy. |
| `Request` | `edu.eci.arsw.webframework` | Wraps the parsed HTTP request; `getValue(name)` reads one query-string parameter. |
| `Response` | `edu.eci.arsw.webframework` | Carries the content type and status code the framework should use to wrap the lambda's return value. |
| `HttpServer` | `edu.eci.arsw.webframework` | The sequential accept loop: parse the request, ask the `Router` for a match, fall back to `StaticFileService`, or return 404. |
| `StaticFileService` | `edu.eci.arsw.webframework` | Serves a file from the classpath location configured with `staticfiles(...)`. |
| `HttpRequest`/`HttpResponse`/`HttpRequestParser`/`HttpResponseWriter` | `edu.eci.arsw.networking.http` | Low-level HTTP plumbing (parsing the request line, writing the response) reused from the socket layer built in the previous lab. |

### Architecture metaphor: an office building

| Building | Framework component |
|---|---|
| Entrance and receptionist | `HttpServer`: accepts every visitor (connection) one at a time and reads what they're asking for |
| Directory in the lobby | `Router`: looks up which office should handle this visitor's request |
| Individual offices | The lambdas registered with `get(...)`: each one knows how to handle exactly one kind of request |
| The memo an office worker fills out | `Response`: says how the reply should be wrapped (content type, status) before it leaves the building |
| Document archive | `StaticFileService`: hands out a pre-existing document (HTML/CSS/JS/image) when no office was asked for |
| Building configuration board | Environment variables (`PORT`, `APP_ENV`, `GREETING_PREFIX`): set once when the building opens, not carved into its walls |
| Closing procedure | Graceful shutdown: the receptionist finishes with the visitor currently at the desk, hands them their reply, and only then locks the door, so no one already inside is cut off mid-conversation |

## 2. Build and run locally

Requires JDK 17+ and Maven.

```bash
mvn clean package
java -jar target/maintainable-application-server-1.0.0.jar
```

The server starts on port `8080` by default and serves `src/main/resources/webroot/index.html` at
`http://localhost:8080/`.

To run with a different configuration:

```bash
PORT=8081 GREETING_PREFIX=Hola APP_ENV=development java -jar target/maintainable-application-server-1.0.0.jar
```

Run the test suite (unit tests plus an integration test that starts a real `HttpServer` and drives
it with real HTTP requests):

```bash
mvn test
```

## 3. Environment variables

| Variable | Purpose | Local default |
|---|---|---|
| `PORT` | HTTP server port | `8080` |
| `GREETING_PREFIX` | Prefix used by the `/hello` route | `Hello` |
| `APP_ENV` | Execution environment; `/shutdown` is only registered when this is `development` | `development` |

None of these are secrets, so they are safe to set directly in the process environment or in a
deployment platform's dashboard.

## 4. Example URLs

| Request | Behavior |
|---|---|
| `GET /hello?name=Pedro` | Dynamic lambda response: `Hello Pedro` (or `<GREETING_PREFIX> Pedro`) |
| `GET /hello` | Missing parameter does not fail: `Hello world` |
| `GET /pi` | Dynamic lambda response: `3.141592653589793` |
| `GET /index.html` or `GET /` | Static HTML page |
| `GET /app.js` | Static JavaScript |
| `GET /style.css` | Static CSS |
| `GET /images/logo.png` | Static binary image |
| `GET /unknown` | `404 Not Found` |
| `GET /shutdown` | Only registered when `APP_ENV=development`; stops the server after replying |

## 5. Graceful shutdown

`GET /shutdown` calls `WebFramework.stop()`, which flips a `running` flag. Because the server is
strictly sequential, that flag is only read again *after* the current response has already been
written and the current connection closed, so the client that asked for the shutdown always gets
its reply. The next iteration of the accept loop sees `running == false` and exits, and the
`ServerSocket` is closed right after.

The route is registered conditionally in `Application`:

```java
String environment = System.getenv().getOrDefault("APP_ENV", "development");
if (environment.equals("development")) {
    get("/shutdown", (req, resp) -> {
        stop();
        return "Server will stop after this response.";
    });
}
```

so it is never reachable when `APP_ENV=production`.

## 6. Cloud deployment

**Platform used:** AWS EC2 (Amazon Linux 2023, t3.micro).
**Public URL:** [http://ec2-54-234-95-53.compute-1.amazonaws.com:8080](http://ec2-54-234-95-53.compute-1.amazonaws.com:8080)
(the instance was terminated after the evidence in `docs/` was collected, as AWS good practice
recommends not leaving cloud resources running once they are no longer needed; the screenshots
below show the application working live at this address while the instance was up)

Deployment steps (AWS EC2, reusing the systemd approach from the previous networking lab):

1. `mvn clean package` locally, or on the instance.
2. Copy `target/maintainable-application-server-1.0.0.jar` to the instance (e.g. `/opt/app/`).
3. Copy [`deploy/maintainable-application-server.service.template`](deploy/maintainable-application-server.service.template) to
   `/etc/systemd/system/maintainable-application-server.service` and replace every `<PLACEHOLDER>`. It already sets
   `APP_ENV=production`, which disables `/shutdown`.
4. `sudo systemctl daemon-reload && sudo systemctl enable --now maintainable-application-server`
5. Open the chosen port in the instance's security group / firewall.
6. Verify with `curl http://localhost:<port>/pi` from inside the instance, then from a browser
   using the instance's public address.

If you deploy to a platform-as-a-service provider instead (Render, Railway, Fly.io), the only
required steps are: point its build command at `mvn clean package`, its start command at
`java -jar target/maintainable-application-server-1.0.0.jar`, and set `APP_ENV=production` (plus any other variable
from the table above) in its environment configuration. The platform supplies `PORT` itself.

## 7. Why this architecture is maintainable

| Principle | Application in this project |
|---|---|
| Separation of concerns | HTTP plumbing (`edu.eci.arsw.networking.http`) is separate from routing (`Router`) and from application behavior (`Application`). |
| Modularity | Routing, request/response, static files, and the accept loop are each their own class. |
| Low coupling | Adding a route means calling `get(...)` in `Application`; `Router` and `HttpServer` never change. |
| High cohesion | Each class has one job: `Router` matches paths, `StaticFileService` reads files, `HttpServer` runs the loop. |
| Externalized configuration | `PORT`, `GREETING_PREFIX`, and `APP_ENV` live outside the source code. |
| Testability | `Router`, `Request`, and the end-to-end HTTP behavior are each covered by a JUnit test, without needing a browser. |

## 8. Tests performed

Automated (`mvn test`, see [`src/test/java/edu/eci/arsw/webframework`](src/test/java/edu/eci/arsw/webframework)):

- `RouterTest`: a registered route is found by `match`; an unregistered path is not.
- `RequestTest`: `getValue` returns the query parameter when present, and `null` when missing.
- `HttpServerIntegrationTest`: starts a real `HttpServer` on a free port and checks, over real HTTP,
  `/hello?name=Pedro`, `/hello` with no parameter, `/pi`, a static `/index.html`, and a 404 for an
  unknown path.

Manual (see evidence below):

- `/hello?name=...` and `/pi` return the expected dynamic responses.
- `/index.html`, `/app.js`, `/style.css`, and `/images/logo.png` are served with the correct
  `Content-Type`.
- `/unknown` returns `404 Not Found`.
- `/shutdown` stops the server locally when `APP_ENV=development`, and returns `404` when
  `APP_ENV=production`.

## 9. Evidence

See [`docs/README.md`](docs/README.md) for the checklist of screenshots to collect (local run,
static resources, both REST endpoints, environment variables, `/shutdown` in development vs.
production, and the deployed cloud application).
