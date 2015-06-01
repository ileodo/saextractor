# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: http://doc.scrapy.org/en/latest/topics/item-pipeline.html
import re
import cPickle as pickle
import random
from scrapy import log

from util import tool, config


class ItemPipeline(object):
    def process_item(self, item, spider):
        # item holds old data
        # new data is holed in item['tree']

        log.msg("ItemPipe get page [%s]:- %s" % (item['id'], item['url']), level=log.DEBUG)
        auto_judge_result = ItemPipeline.auto_judge(item)
        confidence = auto_judge_result[1]
        if confidence>50:
            item['is_target'] = auto_judge_result[0]
        else:
            item['is_target'] = config.const_IS_TARGET_UNKNOW
        item['content_hash'] = tool.hash_for_text(item.raw_content_of_tree())
        item['layout_hash'] = tool.hash_for_text(item.layout_of_tree())
        item['title'] = item.title_of_tree()
        item.save()

        if item['is_target'] in [config.const_IS_TARGET_MULTIPLE, config.const_IS_TARGET_SIGNLE]:
            self.send_to_extractor(item)
        elif item['is_target'] == config.const_IS_TARGET_UNKNOW:
            self.send_to_judge(item,auto_judge_result[0],auto_judge_result[1])
        else:
            pass
        return item

    @staticmethod
    def auto_judge(item):
        target = random.randint(-1,2)
        confidence = random.randint(0, 100)
        return target, confidence

    @staticmethod
    def judge_score(item):
        # TODO Decide whether its a seminar page
        # point based
        # specific word in title,url
        # Store Files
        score = 0
        # 1. Based on previous is_Target
        score += item['is_target'] * 10
        # 2. Based on previous layout/url knowledge
        # TODO

        # 3. Based on judge.xml
        r = re.compile(r"seminar", re.S)
        if len(r.findall(item["url"])) > 0:
            score += 10
        else:
            score -= 10

        return score

    @staticmethod
    def send_to_extractor(item):
        ext = item['content_type'].split('/')[1]
        filename = "%s.%s" % (item['id'], ext)
        f = open(config.path_inbox_extractor + "/%s" % filename, 'w')
        f.write(str(item['soup']))
        f.close()
        data = {"id": item['id'], "filename": filename, "type": ext}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_extractor)

    @staticmethod
    def send_to_judge(item, decision, confidence):
        ext = item['content_type'].split('/')[1]
        filename = "%s.%s" % (item['id'], ext)
        f = open(config.path_inbox_judge + "/%s" % filename, 'w')
        f.write(str(item['soup']))
        f.close()
        data = {"operation": config.socket_CMD_judge_new,
                "id": item['id'],
                "url": item['url'],
                "title": item['title'],
                "decision": decision,
                "confidence": confidence,
                "filename": filename,
                "type": ext}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_judge)
