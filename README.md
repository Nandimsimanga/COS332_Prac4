# COS332_Prac4

This project implements a custom HTTP server in Java using low-level socket programming. The server manually processes HTTP requests and responses without using any built-in web frameworks or libraries, demonstrating an understanding of the HTTP protocol.


Back end Implementation:

Features
	•	Reads raw HTTP requests directly from a socket connection
	•	Parses:
	•	Request line (method, path, version)
	•	Headers
	•	Query parameters (GET)
	•	Request body parameters (POST using Content-Length)
	•	Supports multiple HTTP methods:
	•	GET for search and delete operations
	•	POST for adding appointments
	•	Stores appointment data in memory using an Appointment class and an ArrayList
	•	Implements core functionality:
	•	Add appointments
	•	Search appointments (with optional filters)
	•	Delete appointments
	•	Returns dynamically generated HTML responses
	•	Sends proper HTTP responses including:
	•	Status line (e.g. 200 OK, 404 Not Found)
	•	Headers (Content-Type, Content-Length, Connection)
	•	Response body (HTML or binary data)
	•	Serves binary image data via /image route with correct headers (image/jpeg)

Example Routes
	•	GET /?action=search
	•	GET /?action=delete&time=10
	•	POST / with body: time=10&desc=Meeting
	•	GET /image

How It Works
	1.	The server listens on a port using a ServerSocket
	2.	A browser connects and sends an HTTP request
	3.	The server:
	•	Parses the request manually
	•	Extracts parameters from the URL or request body
	4.	Based on the request:
	•	Adds, searches, or deletes appointments
	5.	The server constructs a full HTTP response:
	•	Status line
	•	Headers
	•	Body (HTML or image data)
	6.	The response is sent back to the browser

Important Notes
	•	No external libraries or frameworks are used for HTTP handling
	•	All protocol handling is implemented manually using sockets
	•	Data is stored in memory (no database)

Purpose

This project demonstrates:
	•	Understanding of the HTTP protocol
	•	Manual request/response handling
	•	Socket programming in Java
	•	Backend logic design without frameworks
:::
