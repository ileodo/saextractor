__author__ = 'LeoDong'
import cPickle as pickle
import os
import shutil
import random

from SAECrawlers.items import UrlretriverItem
from util import tool
from util import config
from util.logger import log
from SAEJudge.FeatueExtract import FeatureExtract
from sklearn import tree

class JudgeQueue:
    def __init__(self):
        self.__judge_queue = {}
        # id : item{title, url, filename, confidence, decision }
        self.__fe = FeatureExtract(config.path_featurespace)
        dtree = pickle.loads(open(config.path_dtree).read())
        self.__X = dtree['X']
        self.__Y = dtree['Y']
        self.__clf = dtree['tree']
        pass

    def __refresh_list(self):
        delete_ids = []
        for key, ent in self.__judge_queue.iteritems():
            decision, confidence = self.__auto_judge(ent['feature'])
            if confidence > config.const_CONFIDENCE_THRESHOLD:
                # pretty sure, save to db, and pass to extract
                item = UrlretriverItem.s_load_id(key)
                item['is_target'] = decision
                item.save()
                delete_ids.append(key)
                if item['is_target'] in [config.const_IS_TARGET_MULTIPLE, config.const_IS_TARGET_SIGNLE]:
                    JudgeQueue.__send_to_extractor(item, ent['filename'])
                else:
                    os.remove(config.path_inbox_judge + "/%s" % ent['filename'])
            else:
                self.__judge_queue[key]['confidence'] = confidence
                self.__judge_queue[key]['decision'] = decision
        for ent_id in delete_ids:
            del self.__judge_queue[ent_id]

    def save(self):
        # queue
        queue_file = open(config.path_judge_list, "w")
        queue_file.write(pickle.dumps(self.__judge_queue, -1))
        queue_file.close()
        # dtree_cs.ox.ac.uk
        dtree_file = open(config.path_dtree, "w")
        dtree_file.write(pickle.dumps(self.__clf, -1))
        dtree_file.close()

    def __auto_judge(self, feature):
        fv = FeatureExtract.vector_feature(feature)
        if self.__clf is not None:
            target = self.__clf.predict(fv)[0]
            confidence = 100 * max(self.__clf.predict_proba(fv)[0])
        else:
            target = -1
            confidence = 0
        return target, confidence

    @staticmethod
    def __send_to_extractor(item, filename=None):
        # FILE
        if filename is None:
            ext = item['content_type'].split('/')[1]
            filename = "%s.%s" % (item['id'], ext)
            f = open(config.path_inbox_extractor + "/%s" % filename, 'w')
            f.write(str(item['content']))
            f.close()
        else:
            shutil.move(config.path_inbox_judge + "/%s" % filename,
                        config.path_inbox_extractor + "/%s" % filename)

        # SIGNAL
        data = {"id": item['id'], "filename": filename, "type": ext}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_extractor)

    def __op_new(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        item = data_loaded['item']
        feature = self.__fe.extract_item(item)
        decision, confidence = self.__auto_judge(feature)
        log.info("[%s]: [%s] # %s # %s%%" % (item_id, FeatureExtract.str_feature(feature), decision, confidence))
        if confidence > config.const_CONFIDENCE_THRESHOLD:
            # pretty sure, save to db, and pass to extract
            item['is_target'] = decision
            item.save()
            if item['is_target'] in [config.const_IS_TARGET_MULTIPLE, config.const_IS_TARGET_SIGNLE]:
                JudgeQueue.__send_to_extractor(item, True)
        else:
            # not sure, put it in queue, involving human-being
            item['is_target'] = config.const_IS_TARGET_UNKNOW
            item.save()

            # save file
            ext = item['content_type'].split('/')[1]
            filename = "%s.%s" % (item['id'], ext)
            f = open(config.path_inbox_judge + "/%s" % filename, 'w')
            f.write(str(item['content']))
            f.close()

            self.__judge_queue[item_id] = {
                "title": item['title'],
                "url": item['url'],
                "filename": filename,
                "confidence": confidence,
                "decision": decision,
                "feature": feature
            }
        pass

    def __op_list(self, data_loaded, connection):
        tool.send_msg(connection, pickle.dumps(self.__judge_queue, -1))
        pass

    def __op_done(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        decision = int(data_loaded['decision'])
        item = UrlretriverItem.s_load_id(item_id)
        item['is_target'] = decision
        item.save()
        self.__X.append(FeatureExtract.vector_feature(self.__judge_queue[item_id]['feature']))
        self.__Y.append(decision)
        self.__clf = tree.DecisionTreeClassifier()
        self.__clf.fit(self.__X, self.__Y)
        del self.__judge_queue[item_id]

        self.__op_refresh(data_loaded,connection)

        tool.send_msg(connection, "0")
        pass

    def __op_refresh(self, data_loaded, connection):
        self.__refresh_list()

    @staticmethod
    def __operations(cmd):
        maps = {
            config.socket_CMD_judge_new: JudgeQueue.__op_new,
            config.socket_CMD_judge_done: JudgeQueue.__op_done,
            config.socket_CMD_judge_list: JudgeQueue.__op_list,
            config.socket_CMD_judge_refresh: JudgeQueue.__op_refresh,
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
