__author__ = 'LeoDong'
import cPickle as pickle
import os
import shutil
import random

from SAECrawlers.items import UrlretriverItem
from util import tool
from util import config
from util.logger import log

class SAEExtractor:
    def __init__(self):
        self.__ext_queue = {}
        # id : item{title, url, filename, decision }
        pass


    def __auto_extract(self,):
        pass

    def __op_new(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        filename = data_loaded['filename']
        item = UrlretriverItem.s_load_id(item_id)

        self.__ext_queue[item_id] = {
            "title": item['title'],
            "url": item['url'],
            "filename": filename,
            "decision": item['is_target']
        }
        log.info(str({
            "title": item['title'],
            "url": item['url'],
            "filename": filename,
            "decision": item['is_target']
        })

        )
        pass

    def __op_list(self, data_loaded, connection):
        tool.send_msg(connection, pickle.dumps(self.__ext_queue, -1))
        pass

    @staticmethod
    def __operations(cmd):
        maps = {
            config.socket_CMD_extractor_new: SAEExtractor.__op_new,
            config.socket_CMD_extractor_list: SAEExtractor.__op_list,
        }
        return maps[cmd]

    def process(self, connection, client_address):
        try:
            data = tool.recv_msg(connection)
            data_loaded = pickle.loads(data)
            log.debug('new connection from %s', client_address)
            log.debug("data received: %s", data_loaded)
            self.__operations(data_loaded['operation'])(self, data_loaded, connection)
        finally:
            # Clean up the connection
            log.debug('connection closed for %s', client_address)
            connection.close()
