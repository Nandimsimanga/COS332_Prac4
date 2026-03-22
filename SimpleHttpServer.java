import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimpleHttpServer {

    private static class Appointment {
        String time;
        String description;

        Appointment(String time, String description) {
            this.time = time;
            this.description = description;
        }
    }

    private static class HttpRequest {
        String method;
        String path;
        String routePath;
        String version;
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> queryParams = new LinkedHashMap<>();
        Map<String, String> bodyParams = new LinkedHashMap<>();
    }

    private static class HttpResponse {
        int statusCode;
        String statusText;
        String contentType;
        byte[] bodyBytes;

        HttpResponse(int statusCode, String statusText, String body) {
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.contentType = "text/html";
            this.bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        }

        HttpResponse(int statusCode, String statusText, String contentType, byte[] bodyBytes) {
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.contentType = contentType;
            this.bodyBytes = bodyBytes;
        }
    }

    private static final List<Appointment> appointments = new ArrayList<>();

    public static void main(String[] args) {
        int port = 8080;

        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected!");

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                );

                HttpRequest request = parseRequest(in);
                logRequest(request);
                HttpResponse response = buildResponse(request);

                sendHttpResponse(clientSocket, response);

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
        request.routePath = extractRoutePath(request.path);
        request.headers = parseHeaders(in);
        request.queryParams = parseQueryParams(request.path);
        request.bodyParams = parseBodyParams(in, request.headers);
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
        int queryIndex = fullPath.indexOf('?');

        if (queryIndex == -1 || queryIndex == fullPath.length() - 1) {
            return new LinkedHashMap<>();
        }

        String queryString = fullPath.substring(queryIndex + 1);
        return parseParameterString(queryString);
    }

    private static Map<String, String> parseBodyParams(BufferedReader in, Map<String, String> headers) throws IOException {
        Map<String, String> bodyParams = new LinkedHashMap<>();
        String contentLengthValue = getHeaderValue(headers, "Content-Length");
        if (contentLengthValue == null) {
            return bodyParams;
        }

        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthValue.trim());
        } catch (NumberFormatException e) {
            return bodyParams;
        }

        if (contentLength <= 0) {
            return bodyParams;
        }

        char[] bodyChars = new char[contentLength];
        int totalRead = 0;
        while (totalRead < contentLength) {
            int count = in.read(bodyChars, totalRead, contentLength - totalRead);
            if (count == -1) {
                break;
            }
            totalRead += count;
        }

        if (totalRead <= 0) {
            return bodyParams;
        }

        String body = new String(bodyChars, 0, totalRead);
        return parseParameterString(body);
    }

    private static Map<String, String> parseParameterString(String paramString) {
        Map<String, String> params = new LinkedHashMap<>();
        String[] pairs = paramString.split("&");

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }

            String[] keyValue = pair.split("=", 2);
            String key = decodeUrlComponent(keyValue[0]);
            String value = keyValue.length > 1 ? decodeUrlComponent(keyValue[1]) : "";
            params.put(key, value);
        }

        return params;
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
        if (!request.bodyParams.isEmpty()) {
            System.out.println("POST Body Time: " + request.bodyParams.getOrDefault("time", "(none)"));
            System.out.println("POST Body Description: " + request.bodyParams.getOrDefault("desc", "(none)"));
        }
    }

    private static HttpResponse buildResponse(HttpRequest request) {
        if ("/image".equals(request.routePath)) {
            return handleImageRequest();
        }

        if (!"/".equals(request.routePath)) {
            return new HttpResponse(
                    404,
                    "Not Found",
                    buildHtmlPage(
                            "Route Not Found",
                            "The requested route does not exist on this server.",
                            "#ffd6d6",
                            buildDetailsSection(request, "Use the root route '/' with query parameters or a POST body.")
                    )
            );
        }

        if ("POST".equalsIgnoreCase(request.method)) {
            return handlePost(request);
        }

        if (!"GET".equalsIgnoreCase(request.method)) {
            return new HttpResponse(
                    405,
                    "Method Not Allowed",
                    buildHtmlPage(
                            "Unsupported Method",
                            "Use POST for add and GET for search or delete.",
                            "#ffe8cc",
                            buildDetailsSection(request, "Try POST with time and desc, or GET /?action=search")
                    )
            );
        }

        String action = getParamOrDefault(request, "action", "search");
        if ("add".equalsIgnoreCase(action)) {
            return new HttpResponse(
                    405,
                    "Method Not Allowed",
                    buildHtmlPage(
                            "Use POST For Add",
                            "Adding appointments must be done with POST, not GET.",
                            "#ffe8cc",
                            buildDetailsSection(request, "Use POST body data like time=10&desc=Meeting")
                    )
            );
        }
        if ("search".equalsIgnoreCase(action)) {
            return handleSearch(request);
        }
        if ("delete".equalsIgnoreCase(action)) {
            return handleDelete(request);
        }

        return new HttpResponse(
                400,
                "Bad Request",
                buildHtmlPage(
                        "Invalid Action",
                        "Unsupported action '" + action + "'. Use add, search, or delete.",
                        "#ffd6d6",
                        buildDetailsSection(request, "Example URLs: /?action=add&time=10&desc=Meeting or /?action=search")
                )
        );
    }

    private static HttpResponse handleImageRequest() {
        Path imagePath = Path.of("test.jpg");
        if (!Files.exists(imagePath)) {
            return new HttpResponse(
                    404,
                    "Not Found",
                    buildHtmlPage(
                            "Image Not Found",
                            "The file test.jpg was not found in the server directory.",
                            "#ffd6d6",
                            "<div class='card'><p>Place a JPEG image named <strong>test.jpg</strong> in the project root and visit <strong>/image</strong>.</p></div>"
                    )
            );
        }

        try {
            byte[] imageBytes = Files.readAllBytes(imagePath);
            return new HttpResponse(200, "OK", "image/jpeg", imageBytes);
        } catch (IOException e) {
            return new HttpResponse(
                    500,
                    "Internal Server Error",
                    buildHtmlPage(
                            "Image Read Error",
                            "The server could not read test.jpg.",
                            "#ffd6d6",
                            "<div class='card'><p>" + escapeHtml(e.getMessage()) + "</p></div>"
                    )
            );
        }
    }

    private static HttpResponse handlePost(HttpRequest request) {
        String action = getParamOrDefault(request, "action", "add");
        if (!"add".equalsIgnoreCase(action)) {
            return new HttpResponse(
                    400,
                    "Bad Request",
                    buildHtmlPage(
                            "Invalid POST Action",
                            "POST is only used for adding appointments.",
                            "#ffd6d6",
                            buildDetailsSection(request, "Submit POST data with time and desc in the request body.")
                    )
            );
        }

        if (isBlank(getParam(request, "time")) || isBlank(getParam(request, "desc"))) {
            return new HttpResponse(
                    400,
                    "Bad Request",
                    buildHtmlPage(
                            "Missing POST Parameters",
                            "POST add requests require both 'time' and 'desc' in the request body.",
                            "#ffd6d6",
                            buildDetailsSection(request, "Example POST body: time=10&desc=Meeting")
                    )
            );
        }

        return handleAdd(request);
    }

    private static HttpResponse handleAdd(HttpRequest request) {
        String time = getParamOrDefault(request, "time", "unspecified");
        String description = getParamOrDefault(request, "desc", "No description provided");

        Appointment appointment = new Appointment(time, description);
        appointments.add(appointment);

        String actionHtml = "<div class='card'>"
                + "<h2>Added Appointment</h2>"
                + "<p><strong>Time:</strong> " + escapeHtml(time) + "</p>"
                + "<p><strong>Description:</strong> " + escapeHtml(description) + "</p>"
                + "<p><strong>Total Stored:</strong> " + appointments.size() + "</p>"
                + "</div>"
                + buildAppointmentListHtml(null, null);

        return new HttpResponse(
                200,
                "OK",
                buildHtmlPage(
                        "Appointment Added",
                        "The appointment was saved in memory successfully.",
                        "#d9f7be",
                        buildAppointmentOperationSection(request, actionHtml, "Missing values default to safe placeholders.")
                )
        );
    }

    private static HttpResponse handleSearch(HttpRequest request) {
        String timeFilter = getOptionalParam(request, "time");
        String descriptionFilter = getOptionalParam(request, "desc");

        return new HttpResponse(
                200,
                "OK",
                buildHtmlPage(
                        "Appointment Search",
                        "Displaying appointments currently stored in memory.",
                        "#d7ecff",
                        buildAppointmentOperationSection(
                                request,
                                buildAppointmentListHtml(timeFilter, descriptionFilter),
                                buildSearchNote(timeFilter, descriptionFilter)
                        )
                )
        );
    }

    private static HttpResponse handleDelete(HttpRequest request) {
        String time = getOptionalParam(request, "time");
        String description = getOptionalParam(request, "desc");

        if (time == null && description == null) {
            return new HttpResponse(
                    400,
                    "Bad Request",
                    buildHtmlPage(
                            "Missing Delete Parameters",
                            "Delete requests require 'time' or 'desc'.",
                            "#ffd6d6",
                            buildDetailsSection(request, "Example: /?action=delete&time=10")
                    )
            );
        }

        int removedCount = deleteMatchingAppointments(time, description);

        String message;
        if (removedCount == 0) {
            message = "No matching appointments were found.";
        } else {
            message = "Removed " + removedCount + " appointment(s) from memory.";
        }

        String actionHtml = "<div class='card'>"
                + "<h2>Delete Result</h2>"
                + "<p><strong>Time Filter:</strong> " + escapeHtml(defaultDisplay(time, "(not provided)")) + "</p>"
                + "<p><strong>Description Filter:</strong> " + escapeHtml(defaultDisplay(description, "(not provided)")) + "</p>"
                + "<p><strong>Deleted Count:</strong> " + removedCount + "</p>"
                + "</div>"
                + buildAppointmentListHtml(null, null);

        return new HttpResponse(
                200,
                "OK",
                buildHtmlPage(
                        "Appointment Deleted",
                        message,
                        "#ffe8cc",
                        buildAppointmentOperationSection(request, actionHtml, "Deletion matches time or description.")
                )
        );
    }

    private static int deleteMatchingAppointments(String time, String description) {
        if (time == null && description == null) {
            return 0;
        }

        int removedCount = 0;
        Iterator<Appointment> iterator = appointments.iterator();
        while (iterator.hasNext()) {
            Appointment appointment = iterator.next();
            boolean timeMatches = time != null && time.equalsIgnoreCase(appointment.time);
            boolean descriptionMatches = description != null && description.equalsIgnoreCase(appointment.description);

            if (timeMatches || descriptionMatches) {
                iterator.remove();
                removedCount++;
            }
        }

        return removedCount;
    }

    private static String buildAppointmentListHtml(String timeFilter, String descriptionFilter) {
        StringBuilder html = new StringBuilder();
        html.append("<div class='card'>");
        html.append("<h2>Stored Appointments</h2>");

        int matchCount = 0;
        StringBuilder items = new StringBuilder();
        items.append("<ul class='appointment-list'>");

        for (Appointment appointment : appointments) {
            boolean matchesTime = timeFilter == null || timeFilter.equalsIgnoreCase(appointment.time);
            boolean matchesDescription = descriptionFilter == null || descriptionFilter.equalsIgnoreCase(appointment.description);

            if (matchesTime && matchesDescription) {
                matchCount++;
                items.append("<li><strong>Time:</strong> ")
                        .append(escapeHtml(appointment.time))
                        .append("<br><strong>Description:</strong> ")
                        .append(escapeHtml(appointment.description))
                        .append("</li>");
            }
        }

        items.append("</ul>");

        if (matchCount == 0) {
            html.append("<p>No appointments matched the current view.</p>");
        } else {
            html.append("<p>Found ").append(matchCount).append(" appointment(s).</p>");
            html.append(items);
        }

        html.append("</div>");
        return html.toString();
    }

    private static String buildSearchNote(String timeFilter, String descriptionFilter) {
        if (timeFilter == null && descriptionFilter == null) {
            return "No search filters supplied, so all stored appointments are shown.";
        }

        return "Current filters. Time: "
                + defaultDisplay(timeFilter, "(not provided)")
                + ", Description: "
                + defaultDisplay(descriptionFilter, "(not provided)");
    }

    private static String buildAppointmentOperationSection(HttpRequest request, String actionHtml, String footerText) {
        return actionHtml + buildDetailsSection(request, footerText);
    }

    private static String getParamOrDefault(HttpRequest request, String key, String defaultValue) {
        String value = getOptionalParam(request, key);
        return value == null ? defaultValue : value;
    }

    private static String getOptionalParam(HttpRequest request, String key) {
        String value = getParam(request, key);
        return isBlank(value) ? null : value.trim();
    }

    private static String getParam(HttpRequest request, String key) {
        if ("POST".equalsIgnoreCase(request.method) && request.bodyParams.containsKey(key)) {
            return request.bodyParams.get(key);
        }
        return request.queryParams.get(key);
    }

    private static String getHeaderValue(Map<String, String> headers, String targetHeader) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase(targetHeader)) {
                return header.getValue();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String extractRoutePath(String fullPath) {
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex == -1) {
            return fullPath;
        }
        return fullPath.substring(0, queryIndex);
    }

    private static void sendHttpResponse(Socket clientSocket, HttpResponse response) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(response.statusCode).append(" ").append(response.statusText).append("\r\n");
        headers.append("Content-Type: ").append(response.contentType).append("\r\n");
        headers.append("Content-Length: ").append(response.bodyBytes.length).append("\r\n");
        headers.append("Connection: close\r\n");
        headers.append("\r\n");

        OutputStream out = clientSocket.getOutputStream();
        out.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        out.write(response.bodyBytes);
        out.flush();
    }

    private static String defaultDisplay(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String buildDetailsSection(HttpRequest request, String footerText) {
        String action = request.queryParams.getOrDefault("action", "(missing)");
        String time = request.queryParams.getOrDefault("time", "(missing)");
        String description = request.queryParams.getOrDefault("desc", "(missing)");

        StringBuilder html = new StringBuilder();
        html.append("<div class='card'>");
        html.append("<h2>Parsed Request</h2>");
        html.append("<p><strong>Method:</strong> ").append(escapeHtml(request.method)).append("</p>");
        html.append("<p><strong>Path:</strong> ").append(escapeHtml(request.path)).append("</p>");
        html.append("<p><strong>Action:</strong> ").append(escapeHtml(action)).append("</p>");
        html.append("<p><strong>Time:</strong> ").append(escapeHtml(time)).append("</p>");
        html.append("<p><strong>Description:</strong> ").append(escapeHtml(description)).append("</p>");
        if (!request.bodyParams.isEmpty()) {
            html.append("<p><strong>POST Time:</strong> ")
                    .append(escapeHtml(request.bodyParams.getOrDefault("time", "(missing)")))
                    .append("</p>");
            html.append("<p><strong>POST Description:</strong> ")
                    .append(escapeHtml(request.bodyParams.getOrDefault("desc", "(missing)")))
                    .append("</p>");
        }
        html.append("</div>");

        html.append("<div class='card'>");
        html.append("<h2>Headers</h2>");
        if (request.headers.isEmpty()) {
            html.append("<p>No headers supplied.</p>");
        } else {
            html.append("<ul>");
            for (Map.Entry<String, String> header : request.headers.entrySet()) {
                html.append("<li><strong>")
                        .append(escapeHtml(header.getKey()))
                        .append(":</strong> ")
                        .append(escapeHtml(header.getValue()))
                        .append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</div>");

        if (footerText != null) {
            html.append("<p class='note'>").append(escapeHtml(footerText)).append("</p>");
        }

        return html.toString();
    }

    private static String buildHtmlPage(String title, String message, String accentColor, String content) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><title>Simple HTTP Server</title>"
                + "<style>"
                + "body{font-family:Arial,sans-serif;background:#f4f6f8;color:#1f2933;margin:0;padding:32px;}"
                + ".wrapper{max-width:760px;margin:0 auto;}"
                + ".banner{background:" + accentColor + ";padding:20px 24px;border-radius:12px;margin-bottom:20px;}"
                + ".banner h1{margin:0 0 8px 0;font-size:28px;}"
                + ".banner p{margin:0;font-size:16px;}"
                + ".card{background:#ffffff;padding:18px 20px;border-radius:12px;margin-bottom:16px;"
                + "box-shadow:0 4px 16px rgba(15,23,42,0.08);}"
                + ".card h2{margin-top:0;font-size:20px;}"
                + ".card p,.card li{line-height:1.5;}"
                + "ul{padding-left:20px;margin-bottom:0;}"
                + ".appointment-list li{margin-bottom:12px;}"
                + ".note{font-weight:bold;color:#334e68;}"
                + "</style></head><body><div class='wrapper'>"
                + "<div class='banner'><h1>" + escapeHtml(title) + "</h1><p>" + escapeHtml(message) + "</p></div>"
                + content
                + "</div></body></html>";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
