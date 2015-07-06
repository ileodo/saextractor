__author__ = 'LeoDong'
import requests
from util import config
from bs4 import BeautifulSoup
from extractor.InfoExtractor import InfoExtractor
from SAECrawlers.items import UrlItem
import json;


ie = InfoExtractor(config.path_extract_onto+"/seminar.xml",config.path_extract_onto)
# # soup = BeautifulSoup(open(config.path_extract_onto+"/seminar.xml").read(),"xml")
print json.dumps(ie.map(), indent=2)

# r = UrlItem.load_db_item(id=1)
# print r.id

# page =

