# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: http://doc.scrapy.org/en/latest/topics/item-pipeline.html
import sys
import cPickle as pickle
from socket import error as socket_error
import random
from scrapy import log

from util import tool, config


class ItemPipeline(object):
    def process_item(self, item, spider):
        # Here, all item are new / updated (need to judge)
        # item holds old data
        # new data is holed in item['tree']

        log.msg("ItemPipe get page [%s]:- %s" % (item['id'], item['url']), level=log.DEBUG)
        item['content_hash'] = tool.hash_for_text(item['raw_content'])
        item['layout_hash'] = tool.hash_for_text(item.layout_of_tree())
        item['title'] = item.title_of_tree()

        item.save()

        #before sending to judge, clear 'soup' and 'raw_content', it's useless(not deepcopiable)
        item['content'] = str(item.get_soup())
        del item['soup']
        del item['raw_content']

        try:
            self.send_to_judge(item)
        except socket_error as err:
            log.msg(str(err.strerror),level=log.ERROR)

        return item

    @staticmethod
    def send_to_judge(item):
        data = {"operation": config.socket_CMD_judge_new,
                "id": item['id'],
                "item": item}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_judge)
