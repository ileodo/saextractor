__author__ = 'LeoDong'

from scrapy import log
from scrapy.contrib.spiders import CrawlSpider, Rule
from scrapy.contrib.linkextractors.lxmlhtml import LxmlLinkExtractor
import config
import db
import util
from SAECrawlers.items import UrlretriverItem


class PagesCrawler(CrawlSpider):
    name = "pagescrawler"
    allowed_domains = config.retriever_allow_domains
    start_urls = config.retriever_start_urls

    rules = [
        Rule(LxmlLinkExtractor(allow_domains=config.retriever_allow_domains,
                               deny_domains=config.retriever_deny_domains,
                               deny_extensions=config.retriever_deny_extensions,
                               unique=True,
                               deny=config.retriever_deny_regxs
                               ),
             callback='parse_item',
             follow=True)
    ]

    def parse_start_url(self, response):
        return self.parse_item(response)

    def parse_item(self, response):
        # is this url in URL_LIB
        id = db.get_url_by_url(util.getUrl(response))

        item = UrlretriverItem(id=id['id'],
                               url=util.getUrl(response),
                               title=util.getHtmlTitle(response),
                               content_hash=util.getHashedContent(response),
                               layout_hash=util.getHashedLayout(response),
                               content=util.getContent(response),
                               content_type=util.getContentType(response))
        yield item

