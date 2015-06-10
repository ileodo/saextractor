__author__ = 'LeoDong'

__author__ = 'LeoDong'

import scrapy
from scrapy import log

from util import db, tool
from SAECrawlers.items import UrlretriverItem


class Updater(scrapy.Spider):
    name = "updater"
    start_urls = db.get_all_ids_istarget()

    def parse(self, response):
        item = UrlretriverItem.s_load_url(response.url)
        item['raw_content'] = response.body
        item['content_type'] = tool.get_content_type_for_response(response)

        log.msg("Updater get page [%s]:- %s" % (item['id'], item['url']))
        yield item
