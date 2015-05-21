function _grpInit(grp) {
  var gid='_LBG'+grp.id;
  grp.el=document.getElementById(gid);
  if(grp.el==null)
    return;
  grp.idx=0;
  if(grp.labels.length==1) {
    applyStyle(grp.el.style, grp.labels[0].st);
    grp.el.title=grp.labels[0].ti;
    grp.el.onclick='';
    return;
  }
  grp.orig=unescape(grp.el.innerHTML);
  _grpShowIdx(grp);
}

function _grpShowIdx(grp) {
  var lab=grp.labels[grp.idx];
  var s=grp.orig.substring(0,lab.idxs[0]);
  for(var i=0;i<lab.idxs.length;i+=2) {
    s+='<span style="'+lab.st+'" title="'+lab.ti+' g'+grp.id+':'+(grp.idx+1)+'/'+grp.labels.length+'">';
    s+=grp.orig.substring(lab.idxs[i],lab.idxs[i+1]);
    s+='</span>';
    if(i+2<lab.idxs.length)
      s+=grp.orig.substring(lab.idxs[i+1],lab.idxs[i+2]);
  }
  s+=grp.orig.substring(lab.idxs[lab.idxs.length-1]);
/*
  var s=grp.orig.substring(0,lab.s);
  // alert('orig='+s);
  s+='<span style="'+lab.st+'" title="'+lab.ti+' g'+grp.id+':'+(grp.idx+1)+'/'+grp.labels.length+'">';
  // alert(s);
  s+=grp.orig.substring(lab.s,lab.e);
  s+='</span>';
  s+=grp.orig.substring(lab.e);
*/
  grp.el.innerHTML=s;
}

function _grpNext(id) {
  var grp=_LBGS[id-1];
  if(grp.labels.length<=1)
    return;
  grp.idx++;
  if(grp.idx>=grp.labels.length)
    grp.idx=0;
  // alert('gid='+id+' next='+grp.idx);
  _grpShowIdx(grp);
}

function _labelInit() {
  var i;
  for(i=0;i<_LBGS.length;i++) {
    _grpInit(_LBGS[i]);
  }
}

function applyStyle(el, st) {
  var lst=st.split(';');
  var i;
  for(i=0;i<lst.length;i++) {
    var av=lst[i].split(':');
    el[av[0]]=av[1];
  }
}

function unescape(s) {
  re1=/&#([0-9]+);/g;
  s=s.replace(re1, function($0,$1,$2) { return(String.fromCharCode(parseInt($1,10))); } );
  re2=/&#x([0-9]+);/g;
  s=s.replace(re2, function($0,$1,$2) { return(String.fromCharCode(parseInt($1,16))); } );
  s=s.replace(/&nbsp;/, ' ');
  s=s.replace(/&amp;/, '&');
  s=s.replace(/&lt;/, '(');
  s=s.replace(/&gt;/, ')');
  s=s.replace(/&quot;/, '"');
  s=s.replace(/&apos;/, '\'');
  return s;
}
