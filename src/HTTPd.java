/*
 * GreyLurk Httpd Server
 *
 * Simple threaded Apache-like HTTP server coded purely in Java
 *
 */

package com.greylurk.httpd;

// Standard Java includes for IO, Network, and other various utilities.
import java.lang.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

public class HTTPd extends Thread {
  public final static String SERVER_SOFTWARE="GreyLurk/1.0 (Java)";
  public final static int DEFAULT_PORT=80;
  protected int port;
  protected ServerSocket listen_socket;
  protected ServerConf configuration;

  // Arrays for translating numerical dates into ASCII Dates as per
  // RFC 1945
  private static final String MONTH[] = {"Jan", "Feb", "Mar", "Apr", "May",
					 "Jun", "Jul", "Aug", "Sep", "Oct",
					 "Nov", "Dec"};

  private static final String WKDAY[] = {"Mon", "Tue", "Wed", "Thu",
					 "Fri", "Sat", "Sun"};

  // The main program:  Listen to the port specified in the configuration
  // and then start a thread to listen to it.
  public HTTPd(ServerConf sc) {
    configuration=sc;
    this.port = sc.getPort();
    try{listen_socket = new ServerSocket(port);}
    catch (IOException e) {
      sc.log.println("HTTPd: Error opening port.  Is there already a " +
		     "webserver running?\n" + e);
    }
    sc.log.println ("HTTPd: listening on port " + port);
    this.start();
  }

  /**
   * <code> public void run() </code> overrides java.lang.Thread.run().
   * Executes the main thread of the program, spawning new threads to deal
   * with incoming connections.
   */
  public void run() {
    try {
      while (true) {
	Socket client_socket = listen_socket.accept();
	Connection c = new Connection(client_socket, configuration);
      }
    }
    catch (IOException e) {
      configuration.log.println("HTTPd: Error while listening for connections\n"+e);
    }
  }

  public static void main(String[] args) {
    int i;
    ServerConf sc;
    for(i=0;i<args.length;i++) {
      if(args[i].equals("-v")) {
	      System.out.println("Server: "+SERVER_SOFTWARE);
	      return;
      }
    }
    sc = new ServerConf(args);
    new HTTPd(sc);
    System.out.println("HTTPd: Done initializing server");
  }

  public final static String httpDate() {
    String returnString = "";
    GregorianCalendar now = new GregorianCalendar();
    int wkday = now.get(Calendar.DAY_OF_WEEK);
    int month = now.get(Calendar.MONTH);
    int date = now.get(Calendar.DAY_OF_MONTH);
    int year = now.get(Calendar.YEAR);
    int hours = now.get(Calendar.HOUR_OF_DAY);
    int minutes = now.get(Calendar.MINUTE);
    int seconds = now.get(Calendar.SECOND);

    returnString += WKDAY[wkday];
    returnString += " " + MONTH[month];

    if (date/10 >= 1) returnString += " " + date;  // Pad the day of month out to two chars
    else returnString += "  " + date;

    /* This section ensures that the times are padded out to two digits */
    if (hours == 0) returnString += " 00";
    else if (hours/10 >= 1) returnString += " " + hours;
    else returnString += " 0" + hours;
    if (minutes == 0) returnString += ":00";
    else if (minutes/10 >= 1) returnString += ":" + minutes;
    else returnString += ":0" + minutes;
    if (seconds == 0) returnString += ":00";
    else if (seconds/10 >= 1) returnString += " " + seconds;
    else returnString += ":0" + seconds;

    returnString += " " + year;
    return returnString;
  }
}



