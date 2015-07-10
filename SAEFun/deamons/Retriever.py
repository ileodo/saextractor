__author__ = 'LeoDong'


from scrapy.utils.log import configure_logging
from scrapy.crawler import CrawlerProcess
from scrapy.utils.project import get_project_settings
from util import tool

# tool.init_database()
# tool.init_working_path()

configure_logging()
process = CrawlerProcess(get_project_settings())
process.crawl('retriever')
process.start()