GreyLurk httpd
-----
Simple threaded Java HTTP server.


Features
--------
- GET/POST CGI execution
- Static page serving
- Supports HTTP/1.1

Installation
------------
Extract all files to a single directory, (i.e. C:\httpd\)
Edit etc/httpd.conf to set the listen port
Edit etc/srm.conf to set the Document root, and any aliases & script aliases
Edit etc/mime.types to add any mime-types you'd like to use and their extensions.
Execute com.greylurk.httpd.HTTPd from the home directory (i.e. c:\httpd\)

