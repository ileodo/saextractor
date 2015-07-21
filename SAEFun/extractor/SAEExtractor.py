# -*- coding: utf-8 -*-
__author__ = 'LeoDong'
import cPickle as pickle
import os
import shutil

from SAECrawlers.items import UrlItem
from util import tool, config, db
from util.logger import log
from InfoExtractor import InfoExtractor


class SAEExtractor:
    def __init__(self):
        self.__ext_queue = {}
        if os.path.isfile(config.path_extract_list):
            self.__ext_queue = pickle.loads(open(config.path_extract_list).read())

        self.__ie = InfoExtractor(config.path_extract_onto + "/seminar.xml", config.path_extract_onto)
        # id : item{title, url, filename, decision }
        pass

    def save(self):
        # queue
        queue_file = open(config.path_extract_list, "w")
        queue_file.write(pickle.dumps(self.__ext_queue, -1))
        queue_file.close()

    def __auto_extract(self, ):
        pass


    def __extract(self,item,extractor):
        result = self.__ie.extract(item, extractor)
        log.info(extractor)
        log.info(result)
        info = dict()
        for att, str in result.iteritems():
            info[self.__ie.db_col(att)] = str[0:self.__ie.max_len(att)]
        log.info(info)
        db.new_sem_with_map(item['id'], info)
        item['extractor'] = extractor
        item.save()

        os.remove(config.path_extractor_inbox + "/%s" % item.filename())


    def __op_new(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        item = UrlItem.load_with_content(id=item_id,file_path=config.path_extractor_inbox)

        count, maps = db.get_url_with_same_layout_hash(item['layout_hash'])
        log.info(str(maps))
        log.info(count)
        if len(maps) > 0:
            import operator

            tar_ext = max(maps.iteritems(), key=operator.itemgetter(1))
            log.info(float(tar_ext[1]) / len(maps))
            if tar_ext[1] > config.extractor_same_layout_number:
                extractor = tool.str2extractor(tar_ext[0])
                self.__extract(item,extractor)
                return

        extractor = config.const_RULE_UNKNOW

        self.__ext_queue[item_id] = {
            "title": item['title'],
            "url": item['url'],
            "filename": item.filename(),
            "decision": item['is_target'],
            "extractor": extractor
        }

        log.info("[%s]: # %s " % (item_id, extractor))

        pass

    def __op_refresh(self,data_loaded,connection):
        delete_ids = []
        for key, ent in self.__ext_queue.iteritems():
            item_id = int(key)
            item = UrlItem.load_with_content(id=item_id,file_path=config.path_extractor_inbox)
            count, maps = db.get_url_with_same_layout_hash(item['layout_hash'])
            log.info(str(maps))
            log.info(count)
            if len(maps) > 0:
                import operator
                tar_ext = max(maps.iteritems(), key=operator.itemgetter(1))
                log.info(float(tar_ext[1]) / len(maps))
                if tar_ext[1] > config.extractor_same_layout_number:
                    extractor = tool.str2extractor(tar_ext[0])
                    self.__extract(item,extractor)
                    delete_ids.append(item_id)
        # clear delete_ids
        for ent_id in delete_ids:
            del self.__ext_queue[ent_id]

    def __op_list(self, data_loaded, connection):
        tool.send_msg(connection, pickle.dumps(self.__ext_queue, -1))
        pass

    def __op_maps(self, data_loaded, connection):
        tool.send_msg(connection, pickle.dumps({'rule': self.__ie.map(), 'action': self.__ie.action_map()}, -1))
        pass

    def __op_preview(self, data_loaded, connection):
        log.info(data_loaded['extractor'])
        if data_loaded['extractor'] == config.const_RULE_UNKNOW:
            result = {x: "" for x in xrange(1, self.__ie.num_attr() + 1)}
        else:
            item_id = int(data_loaded['id'])
            item = UrlItem.load_with_content(item_id, file_path=config.path_extractor_inbox)
            extractor = data_loaded['extractor']
            result = self.__ie.extract(item, extractor)
        preview = list()
        for att, str in result.iteritems():
            preview.insert(att, dict(name=self.__ie.name(att), value=str))
        log.info(preview)
        tool.send_msg(connection, pickle.dumps(preview, -1))
        pass

    def __op_rejudge_done(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        decision = int(data_loaded['decision'])
        item = UrlItem.load(id=item_id)
        del self.__ext_queue[item_id]
        self.__send_back_to_judge(item, decision)
        tool.send_msg(connection, "0")
        pass

    def __op_test_rule(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        rule = data_loaded['rule']
        attrid = int(data_loaded['attrid'])
        item = UrlItem.load_with_content(id=item_id, file_path=config.path_extractor_inbox)
        tool.send_msg(
            connection,
            self.__ie.extract_attr(item, rule_id_or_dict=rule, attr_id=attrid)
        )
        pass

    def __op_add_rule(self, data_loaded, connection):
        rule = data_loaded['rule']
        attrid = int(data_loaded['attrid'])
        new_id = self.__ie.add_rule(attrid, rule)
        tool.send_msg(
            connection,
            pickle.dumps({'rule': self.__ie.map(), 'action': self.__ie.action_map()}, -1)
        )
        pass


    def __op_add_extractor(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        extractor = data_loaded['extractor']

        item = UrlItem.load_with_content(item_id, file_path=config.path_extractor_inbox)
        self.__extract(item,extractor)
        del self.__ext_queue[item['id']]
        tool.send_msg(
            connection,
            "0"
        )
        pass

    @staticmethod
    def __operations(cmd):
        maps = {
            config.socket_CMD_extractor_new: SAEExtractor.__op_new,
            config.socket_CMD_extractor_list: SAEExtractor.__op_list,
            config.socket_CMD_extractor_maps: SAEExtractor.__op_maps,
            config.socket_CMD_extractor_preview: SAEExtractor.__op_preview,
            config.socket_CMD_extractor_rejudge_done: SAEExtractor.__op_rejudge_done,
            config.socket_CMD_extractor_test_rule: SAEExtractor.__op_test_rule,
            config.socket_CMD_extractor_add_rule: SAEExtractor.__op_add_rule,
            config.socket_CMD_extractor_add_extract: SAEExtractor.__op_add_extractor,
            config.socket_CMD_extractor_refresh: SAEExtractor.__op_refresh,
        }
        return maps[cmd]

    @staticmethod
    def __send_back_to_judge(item, decision):
        # move file
        shutil.move(config.path_extractor_inbox + "/%s" % item.filename(),
                    config.path_judge_inbox + "/%s" % item.filename())

        # SIGNAL
        data = {"operation": config.socket_CMD_judge_new, "id": item['id'], "decision": decision}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_judge)

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
