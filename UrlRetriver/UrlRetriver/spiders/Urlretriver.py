__author__ = 'LeoDong'

from scrapy import log
from scrapy.contrib.spiders import CrawlSpider, Rule
from scrapy.contrib.linkextractors.lxmlhtml import LxmlLinkExtractor
import Config
import DB_Helper
import Util
from UrlRetriver.items import UrlretriverItem


class Urlretriver(CrawlSpider):
    name = "urlretriver"
    allowed_domains = Config.retriever_allow_domains
    start_urls = Config.retriever_start_urls

    rules = [
        Rule(LxmlLinkExtractor(allow_domains=Config.retriever_allow_domains,
                               deny_domains=Config.retriever_deny_domains,
                               deny_extensions=Config.retriever_deny_extensions,
                               unique=True,
                               deny=Config.retriever_deny_regxs
                               ),
             callback='parse_item',
             follow=True)
    ]

    def parse_start_url(self, response):
        return self.parse_item(response)

    def parse_item(self, response):
        # is this url in URL_LIB
        id = DB_Helper.getUrlByUrl(Util.getUrl(response));

        item = UrlretriverItem(id=id['id'],
                               url=Util.getUrl(response),
                               title=Util.getHtmlTitle(response),
                               content_hash=Util.getHashedContent(response),
                               layout_hash=Util.getHashedLayout(response),
                               content=Util.getContent(response),
                               content_type=Util.getContentType(response))
        yield item

