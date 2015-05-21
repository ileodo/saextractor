import re

from bs4 import BeautifulSoup
import hashlib
import config

a="haha seminar sd seminar";
r = re.compile(r"seminar", re.S)
print r.findall(a)

