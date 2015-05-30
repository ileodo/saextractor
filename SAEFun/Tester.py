import re

from bs4 import BeautifulSoup
import hashlib
import config
import copy
import requests
from bs4 import BeautifulSoup
from lxml import etree
from lxml.html.clean import Cleaner
from io import StringIO
from lxml.html.soupparser import fromstring
import lxml

page = requests.get("http://www.cs.ox.ac.uk/feeds/News-Publications.xml").text

import cPickle as pickle

soup = BeautifulSoup(page)
data_string = pickle.dumps(soup, -1)
data_loaded = pickle.loads(data_string)
# print data_loaded
if data_loaded==soup:
    print "Hah"
else:
    print "DDD"
# print str(soup)
# print (soup.find("title").text)

# cleaner = Cleaner(style=True, scripts=True, page_structure=False, safe_attrs_only=False)
# page = cleaner.clean_html(page)
# parser = lxml.html.HTMLParser(remove_blank_text=True, remove_comments=True)
# tree = lxml.html.document_fromstring(page, parser)
# print lxml.html.tostring(tree, pretty_print=True,method='xml')
# # print tree

