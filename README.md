# COS332_Prac4

This practical now implements a simple Java HTTP server. 


Implemented features:

- Reads raw HTTP requests from the socket.
- Parses the request line, headers, query parameters, and POST body parameters.
- Supports `GET` requests for searching and deleting appointments.
- Supports `POST` requests for adding appointments using form data such as `time=10&desc=Meeting`.
- Stores appointments in memory using an `Appointment` class and an `ArrayList`.
- Returns structured HTML responses for add, search, delete, and error cases.
- Sends proper HTTP headers including `Content-Type`, `Content-Length`, and `Connection: close`.
- Uses appropriate status codes such as `200 OK`, `400 Bad Request`, `404 Not Found`, and `405 Method Not Allowed`.
- Serves a JPEG image from the `/image` route using raw binary HTTP output.

Example routes:

- `GET /?action=search`
- `GET /?action=delete&time=10`
- `POST /` with body `time=10&desc=Meeting`
- `GET /image`
