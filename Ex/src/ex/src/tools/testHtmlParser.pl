
my $cmd='java -cp ./lib/nekohtml.jar;./lib/nekohtmlXni.jar;./lib/xmlParserAPIs.jar;./lib/xercesImpl.jar;./lib/xml-apis.jar;./lib/xercesSamples.jar sax.Counter file:///d:/ie/data/bikes/html/';
for(my $i=1;$i<=133;$i++) {
  my $cmd2=$cmd.'h'.pad4($i).'.html';
  print `$cmd2`;
}  

sub pad4 {
    my $i=shift @_;
    return "000$i" if($i<10);
    return "00$i" if($i<100);
    return "0$i" if($i<1000);
    return "$i";
}
