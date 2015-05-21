__author__ = 'LeoDong'

from twisted.internet import reactor
from scrapy.crawler import Crawler
from scrapy import log, signals
from SAECrawlers.spiders.PagesCrawler import PagesCrawler
from scrapy.utils.project import get_project_settings
import db
import util
import config

#reset DB
log.msg("Start cleaning DB and data folder")
db.reset_db()
util.initial_data_folder(config.path_todo)
log.msg("Finish cleaning DB and data folder")

spider = PagesCrawler()
settings = get_project_settings()
crawler = Crawler(settings)
crawler.signals.connect(reactor.stop, signal=signals.spider_closed)
crawler.configure()
crawler.crawl(spider)
crawler.start()
log.start()
reactor.run()
# the script will block here until the spider_closed signal was sent