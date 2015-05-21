java -Xmx256M -XX:MaxPermSize=128M -cp dist/iet.jar;../util/dist/util.jar;../ex/dist/ex.jar;../elgwrp/dist/elgwrp.jar;../ex/lib/nekohtml.jar;../ex/lib/xercesImpl.jar;../ex/lib/xml-apis.jar;../ex/lib/js.jar;../ex/lib/commons-math-1.1.jar;../ex/lib/trove.jar;../ex/lib/mysql-connector-java-3.1.12-bin.jar;../ex/lib/weka.jar;../ex/lib/SentenceSplitter.jar medieq.iet.test.Extract %1 %2 %3 %4 %5

@rem e.g. extract.cmd iet.cfg ..\ex\data\med\contact_en6.xml c:\Collections\contact_src_docs\AAP_-_How_to_Contact_the_AAP\index.html
