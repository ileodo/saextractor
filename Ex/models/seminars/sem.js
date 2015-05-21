// $Id: contact.js 1023 2007-09-13 22:37:31Z labsky $
// Accompanying scripts for the contact extraction ontology

var synonymNames=[['robert','bob'],['joseph','joe'],['charles','charlie','chuck'],['william','bill','will','billy','willy'],['larry','lawrence'],['abraham','avi']];

function getSynonyms(w, set) {
    var hash=set.__hash;
    if(hash==undefined) {
        hash={};
        for(var i=0;i<set.length;i++)
            for(var j=0;j<set.length;j++)
                hash[set[i][j]]=set[i];
        set.__hash=hash;
    }
    var synonyms=hash[w.toLowerCase()];
    if(synonyms==undefined)
        synonyms=[w];
    return synonyms;
}

function nameMatchesEmail(name, email) {
    if(!name || !email)
        return 0;
    email=email.substr(0,email.indexOf('@')).toLowerCase();
    email=email.replace(/[,.\-_]/g,"");
    if(email.length==0)
        return 0;
    name=name.toLowerCase();
    var words=name.split(/[. ,]+/);

    // try concatenations of name parts with possible omissions
    // and name parts optionally replaced by initials
    var cands=[""];
    for(var i=0;i<words.length;i++) {
        var w=words[i];
        var ccnt=cands.length;
        for(var k=0;k<ccnt;k++) {
            var base=cands[k];
            if(w.length==0)
                continue;

            var syns=getSynonyms(w, synonymNames);
            for(var j=0;j<syns.length;j++) {
                var w1=syns[j];
                var c=base+w1;
                if(c==email)
                    return 1;
                else if(email.indexOf(c)==0)
                    return 0.75;
                cands[cands.length]=c;
            }

            for(var j=0;j<syns.length;j++) {
                var w1=syns[j];
                var c=base+w1[0];
                if(c==email)
                    return 1;
                else if(email.indexOf(c)==0)
                    return 0.5;
                cands[cands.length]=c;
            }
        }
    }
    // search for a common substring >=3 of one of name parts with email
    var minLen=3;
    if(email.length>=minLen) {
        for(var i=0;i<words.length;i++) {
            if(words[i].length>=minLen) {
                var com=commonSubstr(email, words[i], minLen);
                if(com!=null)
                    return 0.5;
            }
        }
    }
    return 0;
}

function commonSubstr(s1, s2, minLen) {
    var s1ei=s1.length-minLen;
    var s2ei=s2.length-minLen;
    for(var i=0;i<=s1ei;i++) {
        // now search s2 for s1.substr(i,minLen)
        for(var j=0;j<=s2ei;j++) {
            // check if s1.substr(i,minLen) == s2.substr(j,minLen)
            var k=0;
            for(;k<minLen;k++)
                if(s1[i+k]!=s2[j+k])
                    break;
            if(k==minLen)
                return s1.substr(i,minLen);
        }
    }
    return null;
}

function checkPersonName(name) {
    if ((/\d/i).test(name))
		return false;
    if ((/^the|^saint|^santa|^st\.|^st \.|branch|company|hospital|department|university|school|faculty|institute|england|seminars|seminar|lecture|lectures|^new|nombre|email|e-mail|pública|^del( |$)|(^|\s)st($|\s)/i).test(name))
        return false;
    if ((/^(Mo|Tu|We|Th|Fr|Sa|Su|Lu|Ma|Mi|Ju|Vi|Sá|Do)(| \w{1,2})$/i).test(name))
        return false;
    if ((/^[A-Z]{1,3}$/).test(name)) // ban abbreviations like SUN or DOE
        return false;
    return true;
}

function checkRoomNameLG(room) {
    var rc=checkRoomName(room);
    LG(WRN,"Checking room name '"+room+"' rc="+rc);
    return rc;
}

function checkRoomName(room) {
    if((/^room$|^lounge$|^classroom$|^hall$|your/i).test(room))
        return false;
    if(/(DATE|TIME|NOTE|TODAY)/.test(room))
        return false;
    if(/(199|200|201)[0-9]/.test(room))
        return false;
    if((/^\d[012]? ?[ap][ .]*m[ .]*/i).test(room) ||
       (/^\d[012]? ?: ?[0-5][05]/i).test(room))
        return false;
    return true;
}

/** Checks whether 2 person names may reference each other */
function nameRefersTo(a, b) {
    var n1=a.split(/[\s+\.,\-]/);
    var n2=b.split(/[\s+\.,\-]/);
    for(var i=0;i<n1.length;i++) {
        for(var j=0;j<n2.length;j++) {
            if(n1[i]==n2[j]) {
                LG(INF,"<-> "+a+","+b);
                return true;
            }
        }
    }
    return false;
}

function returnTrue() { return true; }

function sameTime(st1, st2) {
    LG(INF,st1+" vs "+st2);
    var t1=parseTime(st1,1);
    var t2=parseTime(st2,1);
    if(t1 && t2) {
        if(t1[0]==t2[0]) {
            if(t1[1]==t2[1]) {
                return true;
            }
        }
    }
    return false;
}

function timeDiff(st1, st2) {
    var t1=parseTime(st1,1);
    var t2=parseTime(st2,1);
    var diff=-1;
    if(t1 && t2) {
//        if(t2[0]<t1[0] && t2[0]<12 && t1[0]<=12)
//            t2[0]+=12;
        var mi1=t1[0]*60+t1[1];
        var mi2=t2[0]*60+t2[1];
        diff=mi1-mi2;
        if(diff<0 && (t1[0]<=6 && t1[0]>=1 && t2[0]<=12 && t2[0]>=7))
            diff+=12*60;
    }
//    LG(WRN,"diff("+st1+t1+","+st2+t2+")="+diff);
    return diff;
}

function parseTime(st, amer) {
	if(st==null)
		return null;
	st = (st.indexOf("ref: ")==0)? st.substring(5): st;
    var re=/^\s*(\d+)(\s*[:\.\-]\s*(\d+))?\s*([apm.]+)?/;
    st=st.toLowerCase();
    var matches=re.exec(st);
    if(matches) {
        var aps=matches[4];
        if(!aps)
            aps='?'
        else if(aps.indexOf('a')!=-1)
            aps='A';
        else if(aps.indexOf('p')!=-1)
            aps='P';
        else
            aps='?';
        var hr=matches[1]*1;
        if(hr<0||hr>=24)
            return null;
        var mis=matches[3];
        var mi=mis? (mis*1): 0;
        if(mi<0||mi>59)
            return null;
        if(amer) {
            if(hr>12) {
                aps='P';
                hr-=12;
            }
        }else {
        	if(aps=='P' && hr<12) {
        	    hr+=12;
        	}
        }
        return [hr, mi, aps];
    }else if(st=="noon") {
        return [12, 0, 'P'];
    }
    return null;
}

function finalizeSeminar() {
	// only transform the best sequence of extrated objects from n-best
  	if(document.extractedPaths.size()==0)
  		return;
  	var bestSeq=document.extractedPaths.get(0);
  	var edits=0;
	
	// if there are time occurrences but no stime exists,
	// change the first time and its references to stime
	var stimes=bestSeq.getAttributesByName("stime");
	if(stimes.size()==0) {
		var times=bestSeq.getAttributesByName("time");
		if(times.size()>0) {
			var avTime=times.get(0);
			LG(USR,"Changing "+avTime.toString()+": "+avTime.getText());
			avTime.setAttributeName("stime", model);
			LG(USR,"to "+avTime.toString());
			edits++;
			for(var i=1; i<times.size(); i++) {
				var avTime2=times.get(i);
				LG(USR,"Examining "+avTime2.toString()+": "+avTime2.getText());
				if(sameTime(avTime.getText(), avTime2.getText())) {
				    LG(USR,"Changing "+avTime2.toString()+"...");
					avTime2.setAttributeName("stime", model);
					LG(USR,"to "+avTime2.toString());
					edits++;
				}
			}
		}
	}
	if(edits>0) {
  		LG(USR,"Post-processing made "+edits+" edits to the document");
	}
}
