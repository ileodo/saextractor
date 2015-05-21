import re

from bs4 import BeautifulSoup
import hashlib

for id in xrange(1,2):
    data = open("../data/%d.html"%id).read()
    soup = BeautifulSoup(data)

    # remove tags
    tag_remove = ["script","map","p","link","meta","img","br","head","span","a","h1","h2","h3","h4","h5","h6","li"]
    for tag in tag_remove+tag_remove:
        for t in soup.select(tag):
            t.decompose()

    result = soup.body.prettify()

    # Comments
    r = re.compile(r"<!.*?>", re.S)
    result = r.sub("", result)

    # Content
    r = re.compile(r"(?<=>).*?(?=<)", re.S)
    result = r.sub("", result)

    # attributes (remove attributes)
    tag_clear_attr = ["input"]
    attr_remove = ["name", "content", "src", "href", "id", "type", "action", "rel", "placeholder", "style", "for","data.*?"]
    attr_clear = ["onclick", "onmouseover", "alt", "title", "value", "onblur", "autocomplete", "maxlength", "onfocus",
                  "usemap", "media","itemscope"]

    r = "|".join(
        ["(?<=<" + x + " ).*?(?=(/)?>)" for x in tag_clear_attr] +
        [" "+x + "=\".*?\"" for x in attr_remove] +
        ["(?<= " + x + "=\").*?(?=\")" for x in attr_clear]
    )

    r = re.compile(r, re.S)
    result = r.sub("", result)

    soup = BeautifulSoup(result)

    print "MD5: "+str(id)+" " + hashlib.md5(soup.prettify()).hexdigest()
    print "=============="
    print str(soup)
