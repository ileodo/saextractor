# -*- coding: utf-8 -*-

# Scrapy settings for UrlRetriver project
#
# For simplicity, this file contains only the most important settings by
# default. All the other settings are documented here:
#
#     http://doc.scrapy.org/en/latest/topics/settings.html
#
import Config

BOT_NAME = 'UrlRetriver'

SPIDER_MODULES = ['UrlRetriver.spiders']
NEWSPIDER_MODULE = 'UrlRetriver.spiders'

SPIDER_MIDDLEWARES = {
    'scrapy.contrib.spidermiddleware.offsite.OffsiteMiddleware': 547,
    'scrapy.contrib.spidermiddleware.depth.DepthMiddleware':300,
}

DOWNLOADER_MIDDLEWARES = {
    'UrlRetriver.middlewares.CustomDownloaderMiddleware': 543,
    'scrapy.contrib.downloadermiddleware.downloadtimeout.DownloadTimeoutMiddleware': 300
}

ITEM_PIPELINES = {
    'UrlRetriver.pipelines.UrlretriverPipeline':300
}


LOG_LEVEL = 'INFO'
DOWNLOAD_TIMEOUT = Config.retriever_download_time_out
DEPTH_LIMIT = Config.retriever_depth_limit

# Crawl responsibly by identifying yourself (and your website) on the user-agent
#USER_AGENT = 'UrlRetriver (+http://www.yourdomain.com)'
