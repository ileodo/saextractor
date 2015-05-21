set JAVA_HOME=../jre
set PATH=../jre/bin;%PATH%
java -Xmx512M -XX:MaxPermSize=128M -cp dist/iet.jar;dist/util.jar;dist/ex.jar;dist/elgwrp.jar;lib/nekohtml.jar;lib/xercesImpl.jar;lib/xml-apis.jar;lib/js.jar;lib/commons-math-1.1.jar;lib/trove.jar;lib/mysql-connector-java-3.1.12-bin.jar;lib/weka.jar;lib/SentenceSplitter.jar medieq.iet.test.RunTask %1
