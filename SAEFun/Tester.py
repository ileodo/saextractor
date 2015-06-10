import re

from bs4 import BeautifulSoup
import hashlib
import copy
from SAECrawlers.items import UrlretriverItem
import requests
from bs4 import BeautifulSoup
from lxml import etree
from lxml.html.clean import Cleaner
from io import StringIO
from lxml.html.soupparser import fromstring
import lxml

import cPickle as pickle
from SAEJudge.FeatueExtract import FeatureExtract
from SAECrawlers.items import UrlretriverItem
from util import db

fe = FeatureExtract("featurespace.xml")
# fe.print_featuremap()

list = db.get_all_ids_istarget()
print "%s,%s,%s" %('id','url',fe.str_featuremap_line())
print len(list)
for x in list:
    item = UrlretriverItem.s_load_id(x)
    content = requests.get(item['url']).content
    item['raw_content'] = content
    item['content'] = content
    item['title'] = item.title_of_tree()
    item['content'] = str(item.get_soup())
    f = fe.extract_item(item)
    print "%s,%s,[%s],%s" %(item['id'],item['url'],item['is_target'], FeatureExtract.str_feature(f))

