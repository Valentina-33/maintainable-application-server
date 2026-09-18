package edu.eci.arsw.webframework;

import edu.eci.arsw.networking.http.HttpMethod;
import edu.eci.arsw.networking.http.HttpRequest;
import edu.eci.arsw.networking.http.HttpRequestParser;
import edu.eci.arsw.networking.http.HttpResponse;
import edu.eci.arsw.networking.http.HttpResponseWriter;
import edu.eci.arsw.networking.http.MalformedRequestException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accepts one client connection at a time, parses the request, asks the
 * {@link Router} for a lambda and falls back to {@link StaticFileService}.
 * The loop is intentionally sequential: the next {@code accept()} only
 * happens once the current connection has been fully handled.
 */
public final class HttpServer {

    private static final Logger LOGGER = Logger.getLogger(HttpServer.class.getName());

    private final Router router;
    private final StaticFileService staticFileService;
    private boolean running;

    public HttpServer(Router router, StaticFileService staticFileService) {
        this.router = router;
        this.staticFileService = staticFileService;
    }

    public void start(int port) throws IOException {
        running = true;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info(() -> "Web framework server listening on port " + port);
            while (running) {
                try (Socket clientSocket = serverSocket.accept()) {
                    handleConnection(clientSocket);
                }
            }
        }

        LOGGER.info("Server stopped gracefully.");
    }

    public void stop() {
        running = false;
    }

    private void handleConnection(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = clientSocket.getOutputStream()) {

            HttpRequest httpRequest;
            try {
                httpRequest = HttpRequestParser.parse(in);
            } catch (MalformedRequestException e) {
                HttpResponseWriter.write(out, HttpResponse.badRequestPlain("400 Bad Request"));
                return;
            }
            if (httpRequest == null) {
                return; // client closed the socket without sending anything
            }

            HttpResponseWriter.write(out, dispatch(httpRequest));

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "I/O error while handling a connection", e);
        }
    }

    private HttpResponse dispatch(HttpRequest httpRequest) {
        if (httpRequest.method() != HttpMethod.GET) {
            return HttpResponse.methodNotAllowed(httpRequest.rawMethod());
        }

        Optional<RouteHandler> route = router.match(httpRequest.path());
        if (route.isPresent()) {
            return runRoute(route.get(), httpRequest);
        }

        return staticFileService.serve(httpRequest.path());
    }

    private HttpResponse runRoute(RouteHandler handler, HttpRequest httpRequest) {
        try {
            Request request = new Request(httpRequest);
            Response response = new Response();
            Object result = handler.handle(request, response);
            String body = result == null ? "" : result.toString();
            return HttpResponse.of(response.getStatus(), reasonPhrase(response.getStatus()),
                    response.getContentType(), body.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in route " + httpRequest.path(), e);
            return HttpResponse.internalError();
        }
    }

    private static String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }
}
