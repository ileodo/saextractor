# -*- coding: utf-8 -*-

# Scrapy settings for UrlRetriver project
#
# For simplicity, this file contains only the most important settings by
# default. All the other settings are documented here:
#
#     http://doc.scrapy.org/en/latest/topics/settings.html
#
from util import config

BOT_NAME = 'SAE'

SPIDER_MODULES = ['SAECrawlers.spiders']
NEWSPIDER_MODULE = 'SAECrawlers.spiders'

SPIDER_MIDDLEWARES = {
    'scrapy.contrib.spidermiddleware.offsite.OffsiteMiddleware': 547,
    'scrapy.contrib.spidermiddleware.depth.DepthMiddleware':300,
}

DOWNLOADER_MIDDLEWARES = {
    'SAECrawlers.middlewares.CustomDownloaderMiddleware': 543,
    'scrapy.contrib.downloadermiddleware.downloadtimeout.DownloadTimeoutMiddleware': 300
}

ITEM_PIPELINES = {
    'SAECrawlers.pipelines.ItemPipeline':300
}

LOG_LEVEL = 'INFO'
DOWNLOAD_TIMEOUT = config.retriever_download_time_out
DEPTH_LIMIT = config.retriever_depth_limit

# Crawl responsibly by identifying yourself (and your website) on the user-agent
#USER_AGENT = 'UrlRetriver (+http://www.yourdomain.com)'
