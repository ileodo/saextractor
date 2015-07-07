__author__ = 'LeoDong'
import requests
from util import config
from bs4 import BeautifulSoup
from extractor.InfoExtractor import InfoExtractor
from SAECrawlers.items import UrlItem
import json


ie = InfoExtractor(config.path_extract_onto+"/seminar.xml",config.path_extract_onto)

item = UrlItem.load_with_content(id=1,file_path=config.path_judge_inbox)
# print item.get_part('soup').prettify()
# print item.get_part('content')
rule={
    "on": "content",
    "scope": {
        "sel":"section#visible-body .logo",
        "target":"text"
    },
    "description": "url",
    "actions": [
        2
    ],
    "substring": {
        "after": "H",
        "before": ""
    },
}

print ie.extract_attr(item,rule_id_or_dict=rule)

# print json.dumps(ie.map(1), indent=2)
# # r = UrlItem.load_db_item(id=1)
# # print r.id
