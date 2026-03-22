import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleHttpServer {

    private static class HttpRequest {
        String method;
        String path;
        String version;
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> queryParams = new LinkedHashMap<>();
    }

    public static void main(String[] args) {
        int port = 8080;

        try {
            // 1. Create server socket (this "opens the door")
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            while (true) {
                // 2. Wait for a client (browser)
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected!");

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                );

                HttpRequest request = parseRequest(in);
                logRequest(request);

                // 4. Send a simple response
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream());

                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: text/html");
                out.println();
                out.println(buildResponseBody(request));

                out.flush();

                // 5. Close connection
                clientSocket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static HttpRequest parseRequest(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Received an empty HTTP request.");
        }

        HttpRequest request = new HttpRequest();
        parseRequestLine(requestLine, request);
        request.headers = parseHeaders(in);
        request.queryParams = parseQueryParams(request.path);
        return request;
    }

    private static void parseRequestLine(String requestLine, HttpRequest request) throws IOException {
        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            throw new IOException("Invalid HTTP request line: " + requestLine);
        }

        request.method = parts[0];
        request.path = parts[1];
        request.version = parts[2];
    }

    private static Map<String, String> parseHeaders(BufferedReader in) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;

        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String headerName = line.substring(0, colonIndex).trim();
                String headerValue = line.substring(colonIndex + 1).trim();
                headers.put(headerName, headerValue);
            }
        }

        return headers;
    }

    private static Map<String, String> parseQueryParams(String fullPath) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        int queryIndex = fullPath.indexOf('?');

        if (queryIndex == -1 || queryIndex == fullPath.length() - 1) {
            return queryParams;
        }

        String queryString = fullPath.substring(queryIndex + 1);
        String[] pairs = queryString.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            String[] keyValue = pair.split("=", 2);
            String key = decodeUrlComponent(keyValue[0]);
            String value = keyValue.length > 1 ? decodeUrlComponent(keyValue[1]) : "";
            queryParams.put(key, value);
        }

        return queryParams;
    }

    private static String decodeUrlComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void logRequest(HttpRequest request) {
        System.out.println("Request Line: " + request.method + " " + request.path + " " + request.version);

        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            System.out.println("Header: " + header.getKey() + ": " + header.getValue());
        }

        System.out.println("Action: " + request.queryParams.getOrDefault("action", "(none)"));
        System.out.println("Time: " + request.queryParams.getOrDefault("time", "(none)"));
        System.out.println("Description: " + request.queryParams.getOrDefault("desc", "(none)"));
    }

    private static String buildResponseBody(HttpRequest request) {
        String action = request.queryParams.getOrDefault("action", "(none)");
        String time = request.queryParams.getOrDefault("time", "(none)");
        String description = request.queryParams.getOrDefault("desc", "(none)");

        return "<html><body>"
                + "<h1>Server is working!</h1>"
                + "<p>Action: " + action + "</p>"
                + "<p>Time: " + time + "</p>"
                + "<p>Description: " + description + "</p>"
                + "</body></html>";
    }
}
