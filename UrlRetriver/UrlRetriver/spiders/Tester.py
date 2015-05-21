__author__ = 'LeoDong'

__author__ = 'LeoDong'

import scrapy
import Config
import DB_Helper
import Util
from UrlRetriver.items import UrlretriverItem


class Tester(scrapy.Spider):
    name = "tester"
    allowed_domains = Config.retriever_allow_domains
    start_urls = ["http://www.cs.liv.ac.uk"];

    def parse(self, response):
        print response.body


