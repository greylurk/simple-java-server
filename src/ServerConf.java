package com.greylurk.httpd;

import java.io.*;
import java.util.*;

public class ServerConf	{
  /* the log file */
  public PrintWriter log;

  /* a place to store the aliases in */
  private Hashtable aliases;

  /* the port on which the server should run */
  private int port;

  /* the document root */
  private String documentRoot;
  private Hashtable directoryAliases;
  private String defaultFile;

  /* the Mime type translations */
  private Hashtable mime_types;

  /* the list of cgi-bin directories */
  private Vector cgi_dirs;

  /* this hashtable stores the values that are returned */
  protected Hashtable returnCodes;

  /**
   * Builds the Hashtable that is used by the postError function to reply
   * to errors
   */

  private void buildErrorHash() {
    returnCodes = new Hashtable();
    returnCodes.put(new Integer(200),"OK");
    returnCodes.put(new Integer(201),"Created");
    returnCodes.put(new Integer(202),"Accepted");
    returnCodes.put(new Integer(204),"No Content");
    returnCodes.put(new Integer(301),"Moved Permananently");
    returnCodes.put(new Integer(302),"Moved Temporarily");
    returnCodes.put(new Integer(304),"Not Modified");
    returnCodes.put(new Integer(400),"Bad Request");
    returnCodes.put(new Integer(401),"Unauthorized");
    returnCodes.put(new Integer(403),"Forbidden");
    returnCodes.put(new Integer(404),"Not Found");
    returnCodes.put(new Integer(500),"Internal Server Error");
    returnCodes.put(new Integer(501),"Not Implemented");
    returnCodes.put(new Integer(502),"Bad Gateway");
    returnCodes.put(new Integer(503),"Service Unavailable");
  }

  private void setPort(String listenLine) {
    StringTokenizer st = new StringTokenizer(listenLine);
    st.nextToken();
    try {
      port = Integer.parseInt(st.nextToken());
      System.out.println("Setting port to " + port);
    } catch (NumberFormatException e) {
      port = 80;
    }
  }

  private void configureHttpd() {
    BufferedReader confFile;
    String currentLine;
    String command;
    try {
      confFile = new BufferedReader(new FileReader("etc/httpd.conf"));
      while ((currentLine = confFile.readLine()) != null) {
		if(currentLine.indexOf('#')>=0) {
	  	  currentLine = currentLine.substring(0,currentLine.indexOf('#'));
	  	  System.out.println(currentLine);
	    }
	    if(currentLine.substring(0,6).equalsIgnoreCase("listen")) {
	      setPort(currentLine);
	    }
      }
    }
    catch (FileNotFoundException e) {
      System.out.println("HTTPd: Error opening configuration file\n"+e);
    }
    catch (IOException e) {
      System.out.println("HTTPd: Error reading configuration file\n"+e);
    }
  }

  /* The Alias table is used to map a URL into a file path.  This function
   * reads the srm.conf file, and builds the necessary inf
   */

  private void buildAliasTable() {
    BufferedReader confFile;
    String currentLine;
    String directive, alias, target;
    StringTokenizer lineParser;
    directoryAliases = new Hashtable();
    cgi_dirs = new Vector();
    try {
      confFile = new BufferedReader(new FileReader("etc/srm.conf"));
      while ((currentLine = confFile.readLine()) != null) {
      	if(currentLine.indexOf('#')>=0) {
		  // Trim off any comments at the end of the line.
      	  currentLine = currentLine.substring(0,currentLine.indexOf('#'));
      	}
      	// Tokenize the line.
        lineParser = new StringTokenizer(currentLine);
      	if (lineParser.hasMoreTokens()) {
			// The first token is the configuration directive.
	        directive = lineParser.nextToken();

			// Configure the document root if there isn't already one configured.
	        if (directive.equals("DocumentRoot") &&
	            (documentRoot == null)) {
	          documentRoot = lineParser.nextToken();

	        // Configure a new alias
	        } else if((directive.equals("Alias")) &&
		                (lineParser.hasMoreTokens())) {
	          alias = lineParser.nextToken();
			  // Trim off the trailing /'s
	          while (alias.endsWith("/")) {
	            alias = alias.substring(0,alias.lastIndexOf('/'));
	          }

	          if (lineParser.hasMoreTokens()) {
				// Map to directory.
	            target = lineParser.nextToken();
	            directoryAliases.put(alias,target);
	            System.out.println(alias + " = " + target);
	          } else {
	            System.err.println("HTTPd: Error in srm.conf file.\n" + currentLine);
	          }
          } else if(directive.equals("ScriptAlias") &&
                    lineParser.hasMoreTokens()) {
            alias = lineParser.nextToken();
	          while (alias.endsWith("/")) {
	            alias = alias.substring(0,alias.lastIndexOf('/'));
	          }
	          if (lineParser.hasMoreTokens()) {
	            target = lineParser.nextToken();
	            directoryAliases.put(alias,target);
              cgi_dirs.addElement(alias);
	            System.out.println(alias + "(CGI) = " + target);
	          } else {
	            System.err.println("HTTPd: Error in srm.conf file.\n" + currentLine);
	          }
          } else {
            System.err.println("HTTPd: Error in srm.conf file.\n"
			       + currentLine);
          }
        }
      }
    }
    catch (FileNotFoundException e) {
      System.out.println("HTTPd: Error opening srm.conf file\n"+e);
    }
    catch (IOException e) {
      System.out.println("HTTPd: Error reading srm.conf file\n"+e);
    }
    if (documentRoot == null) {
      documentRoot = ".";  // we need to have at least the document root set.
    }
  }


  /**
   * Creates a ServerConf object, reading the parameters from the srm.conf,
   * httpd.conf, and mime.types files.
   */

  public ServerConf(String[] args) {
    int i;
    buildErrorHash();
    buildMimeTypeTable();
    buildAliasTable();
    configureHttpd();
    for (i=0; i<args.length; i++) {
      if (args[i].equals("-p")) {
	try {
	  port = Integer.parseInt(args[++i]);
	} catch (NumberFormatException e) {
	  System.err.println("HTTPd: bad command line argument: -p "+args[i] +
			     "\n" + e);
	  System.exit(0);
	}
      } else if (args[i].equals("-l")) {
	try {
	  String fileName = args[++i];
	  log = new PrintWriter(new FileWriter(fileName));
	} catch (IOException e) {
	  System.err.println("HTTPd: Could not open log file for writing\n" +
			     e);
	} catch (ArrayIndexOutOfBoundsException e) {
	  System.err.println("HTTPd: Bad command line parameters.\n");
	  System.exit(-1);
	}
      } else if(args[i].equals("-r")) {
	try {
	  String fileName = args[++i];
	  if(new File(fileName).exists()) {
	    documentRoot = fileName;
	  } else {
	    System.err.println("HTTPd: Invalid Doucment Root:"+fileName+"\n");
	    System.exit(-1);
	  }
	} catch (ArrayIndexOutOfBoundsException e) {
	  System.err.println("HTTPd: Bad command line parameters.\n");
	  System.exit(-1);
	}
      }
    }
    if (log == null) {
      log = new PrintWriter(System.out);
    }
  }

  public String getMimeType(String filename) {
    StringTokenizer st = new StringTokenizer(filename,".");
    String extension = "";
    String mime_type;
    while (st.hasMoreTokens()) {
      extension=st.nextToken();
    }
    if ((mime_type = (String)mime_types.get(extension)) == null) {
      mime_type = "text/plain";
    }
    return mime_type;
  }

  public int getPort() {
    return port;
  }

  private void buildMimeTypeTable() {
    BufferedReader confFile;
    String currentLine;
    String extension, type;
    StringTokenizer lineParser;
    mime_types = new Hashtable();
    try {
      confFile = new BufferedReader(new FileReader("etc/mime.types"));
      while ((currentLine = confFile.readLine()) != null) {
      	if(currentLine.indexOf('#')>=0) {
      	  currentLine = currentLine.substring(0,currentLine.indexOf('#'));
      	}
    	  lineParser = new StringTokenizer(currentLine);
      	if (lineParser.hasMoreTokens()) {
	        type = lineParser.nextToken();
	        while (lineParser.hasMoreTokens()) {
	          extension = lineParser.nextToken();
	          mime_types.put(extension,type);
	          System.out.println(extension + " = " + type);
	        }
	      }
      }
    }
    catch (FileNotFoundException e) {
      System.out.println("HTTPd: Error opening mime.types file\n"+e);
    }
    catch (IOException e) {
      System.out.println("HTTPd: Error reading mime.types file\n"+e);
    }
  }


  public boolean isCGI(String path) {
    boolean cgi_script = false;
    Enumeration e = cgi_dirs.elements();
    while (e.hasMoreElements()) {
      if(path.indexOf((String)e.nextElement()) == 0) {
        cgi_script = true;
        break;
      }
    }
    return cgi_script;
  }

  public String translatePath(String path) {
    String translatedPath;
    String startingPath;
    String pathFragment, remainingPath;
    Enumeration keys;

    // make sure that there's an index.html on the end of the path.
    if(path.endsWith("/")) {
      path = path + "index.html";
    }

    // pathFragment is the fragment that we are searching in the hashtable for
    pathFragment = "";
    // remainingPath is the fragment that comes after that.
    remainingPath = path;

    // until we've got a match...
    translatedPath = null;
    while (translatedPath == null) {
      String tempFragment = remainingPath.substring(1);
      // If we've run out of other paths to look in, it's got to be in the document root.
      if(tempFragment.indexOf('/') < 0) {
        translatedPath = documentRoot + pathFragment + remainingPath;
        break;
      }
      pathFragment = path.substring(0,tempFragment.indexOf('/')+1);
      remainingPath = path.substring(tempFragment.indexOf('/')+1);
      keys = directoryAliases.keys();
      while (keys.hasMoreElements()) {
      	if(pathFragment.equals(keys.nextElement())) {
	        translatedPath = directoryAliases.get(pathFragment) + remainingPath;
	      }
      }
    }

    return translatedPath;
  }

  public void finalize() throws java.lang.Throwable{
    log.flush();
    log.close();
    super.finalize();
  }
}





