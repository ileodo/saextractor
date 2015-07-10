__author__ = 'LeoDong'

__author__ = 'LeoDong'

import scrapy
import logging

from util import db, tool
from SAECrawlers.items import UrlItem


class Updater(scrapy.Spider):
    name = "updater"
    start_urls = db.get_all_ids_istarget()

    def parse(self, response):
        item = UrlItem.load_with_content(url=response.url, response=response)
        logging.debug("Updater get page [%s]:- %s" % (item['id'], item['url']))
        yield item
