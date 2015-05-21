__author__ = 'LeoDong'

__author__ = 'LeoDong'

import scrapy
import config
import db
import util
from SAECrawlers.items import UrlretriverItem


class Updater(scrapy.Spider):
    name = "updater"
    start_urls = db.get_all_urls_istarget()

    def parse(self, response):
        id = db.get_url_by_url(util.getUrl(response))

        item = UrlretriverItem(id=id['id'],
                               url=util.getUrl(response),
                               title=util.getHtmlTitle(response),
                               content_hash=util.getHashedContent(response),
                               layout_hash=util.getHashedLayout(response),
                               content=util.getContent(response),
                               content_type=util.getContentType(response))
        yield item

