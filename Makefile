#Makefile for GreyLurk webserver.

SOURCES = src/HTTPd.java src/ServerConf.java src/Connection.java \
	  src/Testbed.java

CLASSES = 

JAVAHOME = /usr/usc/jdk/1.1.5

JAVAC = $(JAVAHOME)/bin/javac
JAVA = $(JAVAHOME)/bin/java
CLASSPATH = $(JAVAHOME)/lib/classes.zip

zip: HTTPd.jar

HTTPd.jar: $(SOURCES)
	$(JAVAC) -d lib -classpath $(CLASSPATH) src/*.java
	cd lib; jar cvf ../HTTPd.jar com; cd ..

run: lib/HTTPd.zip
	$(JAVA) -cp HTTPd.jar com.greylurk.httpd.HTTPd

testbed: lib/HTTPd.zip
	$(JAVA) -cp HTTPd.jar com.greylurk.httpd.Testbed

clean: 
	rm -rf lib/com src/*~ HTTPd.jar
