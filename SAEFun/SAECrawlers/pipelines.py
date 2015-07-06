# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: http://doc.scrapy.org/en/latest/topics/item-pipeline.html
import cPickle as pickle
from socket import error as socket_error

from scrapy import log

from util import tool, config


class ItemPipeline(object):
    def process_item(self, item, spider):
        # Here, all item are new / updated (need to judge)
        # item holds old data
        # new data is holed in item['tree']

        log.msg("ItemPipe get page [%s]:- %s" % (item['id'], item['url']), level=log.DEBUG)

        item['content_hash'] = tool.hash_for_text(item.raw_content())
        item['layout_hash'] = tool.hash_for_text(item.get_layout())
        item['title'] = item.get_short_title()

        item.save()

        try:
            self.send_to_judge(item)
        except socket_error as err:
            log.msg(str(err.strerror), level=log.ERROR)

        return item

    @staticmethod
    def send_to_judge(item):
        # save raw_content to judge inbox
        f = open(config.path_judge_inbox + "/%s" % item.filename(), 'w')
        f.write(str(item.raw_content()))
        f.close()
        # signal
        data = {"operation": config.socket_CMD_judge_new,
                "id": item['id']}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_judge)
