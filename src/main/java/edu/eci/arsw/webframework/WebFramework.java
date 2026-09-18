package edu.eci.arsw.webframework;

import java.io.IOException;

public class WebFramework {

    private static final Router ROUTER = new Router();
    private static String staticFilesLocation = "/webroot";
    private static HttpServer server;

    public static void staticfiles(String root) {
        staticFilesLocation = root;
    }

    public static void get(String path, RouteHandler handler) {
        ROUTER.addGetRoute(path, handler);
    }

    public static void start() throws IOException {
        start(resolvePort());
    }

    public static void start(int port) throws IOException {
        server = new HttpServer(ROUTER, new StaticFileService(staticFilesLocation));
        server.start(port);
    }

    public static void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private static int resolvePort() {
        String portValue = System.getenv("PORT");
        return (portValue == null || portValue.isBlank()) ? 8080 : Integer.parseInt(portValue);
    }
}
