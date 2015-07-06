__author__ = 'LeoDong'

from scrapy import log
from scrapy.contrib.spiders import CrawlSpider, Rule
from scrapy.contrib.linkextractors.lxmlhtml import LxmlLinkExtractor

from util import tool, config
from SAECrawlers.items import UrlItem


class PagesCrawler(CrawlSpider):
    name = "retriever"
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

    @staticmethod
    def parse_item(response):
        # response are all updated or new
        # db not changed
        print "aBCADFSDF"
        # is this url in URL_LIB
        item = UrlItem.load(url=response.url, response=response)
        log.msg("PC get page [%s]:- %s" % (item['id'], item['url']))
        yield item
