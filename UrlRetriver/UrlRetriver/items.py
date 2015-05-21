# -*- coding: utf-8 -*-

# Define here the models for your scraped items
#
# See documentation in:
# http://doc.scrapy.org/en/latest/topics/items.html

import scrapy


class UrlretriverItem(scrapy.Item):
    # define the fields for your item here like:
    id = scrapy.Field()
    url = scrapy.Field()
    title = scrapy.Field()
    content_hash = scrapy.Field()
    layout_hash = scrapy.Field()
    content = scrapy.Field()
    content_type = scrapy.Field()
    pass
