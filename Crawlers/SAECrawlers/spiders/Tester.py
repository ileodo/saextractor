__author__ = 'LeoDong'

__author__ = 'LeoDong'

import scrapy
import config
import db
import util
from SAECrawlers.items import UrlretriverItem


class Tester(scrapy.Spider):
    name = "tester"
    allowed_domains = config.retriever_allow_domains
    start_urls = ["http://www.cs.liv.ac.uk"];

    def parse(self, response):
        print response.body


