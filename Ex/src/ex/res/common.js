// $Id: $
// Common routines available under JS in Ex

// importPackage(Packages.uep.util); // don't use this, Rhino complains

var USR=0;
var ERR=1;
var WRN=2;
var INF=3;
var TRC=4;
var MML=5;
function LG() {
    var level;
    var msg='';
    switch(arguments.length) {
    case 0: return;
    case 1: level=USR; msg=arguments[0]; break;
    default:
      if(typeof(arguments[0]=='number')) {
        level=arguments[0];
        for(var i=1;i<arguments.length;i++) msg+=arguments[i];
      }else {
        level=USR;
        for(var i=0;i<arguments.length;i++) msg+=arguments[i];
      }
    }
    // java.lang.System.out.println(st1+" vs "+st2);
    // Logger.LOG(level,msg);
    Packages.uep.util.Logger.LOG(level,msg);   
}

// both set for each document by Model.setCurrentDocument()
var document=null;
var model=null;
