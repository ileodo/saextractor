# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: http://doc.scrapy.org/en/latest/topics/item-pipeline.html

class UrlretriverPipeline(object):
    def process_item(self, item, spider):
        # TODO Decide whether its a seminar page
        # point based
        # specific word in title,url
        # Store Files
        ext = item['content_type'].split('/')[1]
        f = open("../data/%s.%s" % (item['id'], ext), 'w')
        f.write(item['content'])
        f.close()
        return item
