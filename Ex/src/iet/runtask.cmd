java -Xmx1024M -XX:MaxPermSize=128M -cp dist/iet.jar;../util/dist/util.jar;../ex/dist/ex.jar;../elgwrp/dist/elgwrp.jar;../ex/lib/nekohtml.jar;../ex/lib/xercesImpl.jar;../ex/lib/xml-apis.jar;../ex/lib/js.jar;../ex/lib/commons-math-1.1.jar;../ex/lib/trove.jar;../ex/lib/mysql-connector-java-3.1.12-bin.jar;../ex/lib/weka.jar;../ex/lib/SentenceSplitter.jar medieq.iet.test.RunTask %1

@rem -agentlib:yjpagent   java -agentlib:yjpagent=onexit=memory,dir=c:\temp\_prof,usedmem=70
