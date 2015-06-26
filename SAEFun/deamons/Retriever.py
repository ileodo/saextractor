__author__ = 'LeoDong'

from twisted.internet import reactor
from scrapy.crawler import Crawler
from scrapy import log, signals
from scrapy.utils.project import get_project_settings

from SAECrawlers.spiders.PagesCrawler import PagesCrawler
from util import tool

tool.init_database()
tool.init_working_path()

spider = PagesCrawler()
settings = get_project_settings()
crawler = Crawler(settings)
crawler.signals.connect(reactor.stop, signal=signals.spider_closed)
crawler.configure()
crawler.crawl(spider)
crawler.start()
log.start_from_settings(settings, crawler)
reactor.run()
