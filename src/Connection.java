package com.greylurk.httpd;

import java.net.*;
import java.io.*;
import java.util.*;

public class Connection extends Thread {
  protected Socket client;
  protected BufferedReader in;
  protected PrintWriter out;

  private ServerConf sc;
  private String method;
  private String URI;
  private int majorVersion;
  private int minorVersion;

  //Initialize the streams and start the thread
  public Connection(Socket client_socket,ServerConf sc) {
    client = client_socket;
    this.sc = sc;
    try {
      in = new BufferedReader(new InputStreamReader(client.getInputStream()));
      out = new PrintWriter(client.getOutputStream(), true);
      //out = new PrintWriter(System.out, true);
    }
    catch (IOException e) {
      try {client.close();}
      catch (IOException e2) {}
      System.err.println("HTTPd: Exception while getting socket streams: "+e);
      return;
    }
    this.start();
  }

  public void parseRequest(){
    String requestLine;
    String requestBody;
    String version;
    StringTokenizer st;
    StringTokenizer versionParse;

    try {requestLine = in.readLine();}
    catch (IOException e) {
      System.err.println("HTTPd: Network error communicating with Client\n"+e);
      requestLine = "";
    }
    st = new StringTokenizer(requestLine);
    if (st.hasMoreTokens()) { method = st.nextToken();}
    else {method = null;} // If there's no request, then we
                          // need to give an error message.
    if (st.hasMoreTokens()) { URI = st.nextToken();}
    else {method = null;} // If there is no URI listed,
                          // then there's an error in the request
    if (st.hasMoreTokens()) {
      version = st.nextToken().substring(5);
      versionParse = new StringTokenizer(version,".");
      try {
	      majorVersion = Integer.parseInt(versionParse.nextToken());
	      minorVersion = Integer.parseInt(versionParse.nextToken());
      } catch (NumberFormatException e) {
	      // If the version is improperly formatted use HTTP/0.9
	      // for compatability
	      majorVersion = 0; minorVersion = 9;
      }
    } else {
      //If there is no version listed, it is HTTP/0.9
      majorVersion=0; minorVersion = 9;
    }
  }

  public void postResponse() {
    if(method.equals("GET")) {
      postFile();
    } else if (method.equals("POST")) {
      postCGI();
    } else if (method.equals("HEAD")) {
      postHead();
    } else {
      postError(501);
    }
  }

  private void postCGI(){
    Vector env_table = new Vector();
    String request_line;
    String header_directive;
    String env_array[];
    Runtime cgi_runtime;
    Process cgi_proc;
    PrintWriter cgi_in;
    BufferedReader cgi_out;
    if(URI.indexOf("?") >= 0) {
      env_table.addElement("QUERY="+URI.substring(URI.indexOf("?")));
    }
    env_table.addElement("SERVER_SOFTWARE="+HTTPd.SERVER_SOFTWARE);
    try { env_table.addElement("SERVER_NAME="+InetAddress.getLocalHost()); }
    catch (UnknownHostException e) {}
    env_table.addElement("GATEWAY_INTERFACE=CGI/1.1");
    env_table.addElement("SERVER_PROTOCOL=HTTP/1.0");
    env_table.addElement("SERVER_PORT="+sc.getPort());
    env_table.addElement("REQUEST_METHOD="+method);
    env_table.addElement("REMOTE_ADDR="+client.getInetAddress());
    if (majorVersion >= 1) {   // HTTP/1.0 and higher have other
                               // things in the request.
      try {
	      while (!(request_line = in.readLine()).equals("")) {
          StringTokenizer parser = new StringTokenizer(request_line);
          header_directive = parser.nextToken();
          if(header_directive.equals("Content-Type:")) {
            env_table.addElement("CONTENT_TYPE="+parser.nextToken());
          } else if (header_directive.equals("Content-Length:")) {
            env_table.addElement("CONTENT_LENGTH="+parser.nextToken());
          } else if (header_directive.equals("Host:")) {
            env_table.addElement("REMOTE_HOST="+parser.nextToken());
          } else if (header_directive.equals("Accept:")) {
            env_table.addElement("HTTP_ACCEPT="+parser.nextToken());
          } else if (header_directive.equals("User-Agent:")) {
            env_table.addElement("HTTP_USER_AGENT="+parser.nextToken());
          }
	      }
      } catch (IOException e) {
	      System.err.println("HTTPd: Network error communicating with Client\n"
			                     + e);
      }
    }
    env_array = new String[env_table.size()];
    env_table.copyInto(env_array);
    cgi_runtime = Runtime.getRuntime();
    String file_name = sc.translatePath(URI);
    try {
      System.out.println("Execing CGI Script "+file_name);
      cgi_proc = cgi_runtime.exec(file_name,env_array);
    } catch (IOException e) {
      postError(403);
      System.err.println("Error runnig CGI script:\n"+e);
      return;
    }

    cgi_in = new PrintWriter(new OutputStreamWriter(cgi_proc.getOutputStream()));
    cgi_out = new BufferedReader(new InputStreamReader(cgi_proc.getInputStream()));

    try {
      request_line = in.readLine();
      while (request_line != null) {
        cgi_in.println(request_line);
        request_line = in.readLine();
      }
    } catch (IOException e) {
      postError(501);
      return;
    }


    try {
      out.println("HTTP/1.0 200 OK");
      String str = cgi_out.readLine();
      // write out to browser
      while (str != null) {
        out.println(str);
        str = cgi_out.readLine();
      }
      out.flush();
      out.close();
    } catch (IOException e) {
      postError(501);
      return;
    }
  }


  private void postHead() {
  }

  private void postError(int errNo) {
    postHeader(errNo);
    out.println("Date: " + HTTPd.httpDate());
    out.println("Server: " + HTTPd.SERVER_SOFTWARE);
    out.println("Connection: close");
    out.println("Content-Type: text/html");
    out.println();
    out.println("<HTML><HEAD><TITLE>Error Number "+errNo+": " +
		sc.returnCodes.get(new Integer(errNo))+"</TITLE>");
    out.println("<BODY><H1>" + errNo + "</H1>");
    out.println(sc.returnCodes.get(new Integer(errNo)));
  }

  private int fileStatus(String filename){
    File requestedFile = new File(filename);
    int returnCode;
    if (!requestedFile.exists()) {
      returnCode = 404; // File doesn't exist
    } else if (!requestedFile.canRead()) {
      returnCode = 403; // Permissions are incorrect
    } else if (requestedFile.isDirectory()) {
      returnCode = 404;
    } else {
      returnCode = 200;
    }
    return returnCode;
  }

  private void postFile() {
    int returnCode;
    String translatedFileName = sc.translatePath(URI);;
    sc.log.println(translatedFileName);
    String mimeType = sc.getMimeType(translatedFileName);
    StringTokenizer mimeParser = new StringTokenizer(mimeType,"/");
    String mainType = mimeParser.nextToken();
    String subType = mimeParser.nextToken();
    returnCode = fileStatus(translatedFileName);
    if (returnCode == 200) {
      if (mimeType.equals("application/x-cgi") || sc.isCGI(URI)) {
	      postCGI();
      } else if (mainType.equals("text")) {
	      postHeader(200);
	      out.println("Date: " + HTTPd.httpDate());
	      out.println("Server: " + HTTPd.SERVER_SOFTWARE);
	      out.println("Connection: close");
	      out.println("Content-Length: " +
		                new File(translatedFileName).length());
      	out.println("Content-Type: " + mimeType);
	      out.println();
	      postText(translatedFileName);
      } else {
	      postHeader(200);
	      out.println("Date: " + HTTPd.httpDate());
	      out.println("Server: " + HTTPd.SERVER_SOFTWARE);
	      out.println("Content-Length: " +
		                new File(translatedFileName).length());
	      sc.log.println("Content-length: " +
		                   new File(translatedFileName).length());
	      out.println("Connection: close");
	      out.println("Content-Type: " + mimeType);
	      out.println();
	      postBinary(translatedFileName);
      }
    } else {
      postError(returnCode);
    }
  }

  private void postBinary(String fileName) {
    FileInputStream binFile;
    try {
      binFile = new FileInputStream(fileName);
      while (binFile.available() > 0) {
	out.write((char)binFile.read());
      }
    } catch (IOException e) {
      System.err.println("HTTPd: Error reading binary file\n" + e);
      return;
    }
  }

  private void postText(String fileName) {
    BufferedReader fileSource;
    try {
      fileSource = new BufferedReader(new FileReader(fileName));
    } catch (FileNotFoundException e) {
      return;
    }
    String line_read;
    try {
      while ((line_read = fileSource.readLine()) != null) {
        out.println(line_read);
      }
    } catch (IOException e) {return;}
  }

  private void postHeader(int returnCode) {
    sc.log.println("Printing a header");
    out.println("HTTP/1.0 "+returnCode+" " +
		sc.returnCodes.get(new Integer(returnCode)));
  }

  public void run() {
    parseRequest();
    postResponse();
    if (out.checkError())
      sc.log.println("Error is true");
    try {
      client.close();
    } catch (IOException e){}
  }
}

