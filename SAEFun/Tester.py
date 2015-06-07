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
from SAEJudge.FeatueExtract import features_extract
from SAECrawlers.items import UrlretriverItem
item = UrlretriverItem.s_load_id(1)
content = requests.get("http://www2.physics.ox.ac.uk/research/seminars?date=2007").content
item['url']="http://www2.physics.ox.ac.uk/research/seminars?date=2007"
item['raw_content'] = content
item['content'] = content
item['title'] = item.title_of_tree()
item['content'] = str(item.get_soup())

features_extract(item)

