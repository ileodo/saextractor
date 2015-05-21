# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: http://doc.scrapy.org/en/latest/topics/item-pipeline.html
import re
import db
import config

class ItemPipeline(object):
    def process_item(self, item, spider):
        # TODO Decide whether its a seminar page
        # point based
        # specific word in title,url
        # Store Files
        if self.is_target(item):
            db.update_url_istarget(item['id'],"1")
            self.write_to_todo(item)
        else:
            db.update_url_istarget(item['id'],"-1")
        return item

    def is_target(self, item):
        r = re.compile(r"seminar", re.S)
        if len(r.findall(item["url"])) > 0:
            return True
        else:
            return False

    def write_to_todo(self,item):
        ext = item['content_type'].split('/')[1]
        f = open(config.path_todo+"/%s.%s" % (item['id'], ext), 'w')
        f.write(item['content'])
        f.close()

