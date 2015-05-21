__author__ = 'LeoDong'

from twisted.internet import reactor
from scrapy.crawler import Crawler
from scrapy import log, signals
from UrlRetriver.spiders.Urlretriver import Urlretriver
from scrapy.utils.project import get_project_settings
import DB_Helper
import Util

#reset DB
log.msg("Start cleaning DB and data folder")
DB_Helper.resetdb()
Util.initial_data_folder("../data/")
log.msg("Finish cleaning DB and data folder")

spider = Urlretriver()
settings = get_project_settings()
crawler = Crawler(settings)
crawler.signals.connect(reactor.stop, signal=signals.spider_closed)
crawler.configure()
crawler.crawl(spider)
crawler.start()
log.start()
reactor.run()
# the script will block here until the spider_closed signal was sent