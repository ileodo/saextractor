__author__ = 'LeoDong'
import cPickle as pickle
import os
import shutil

from sklearn import tree

from SAECrawlers.items import UrlItem
from util import tool
from util import config
from util.logger import log
from FeatueExtract import FeatureExtract


class SAEJudge:
    def __init__(self, dtreefile, dtree_param):
        self.__judge_queue = {}
        self.__dtree_param = dtree_param
        # id : item{title, url, filename, confidence, decision }
        self.__fe = FeatureExtract(config.path_fe_space)
        dtree = pickle.loads(open(dtreefile).read())
        self.__F = dtree['F']
        self.__L = dtree['L']
        self.__clf = dtree['tree']
        pass


    def save(self):
        # queue
        queue_file = open(config.path_judge_list, "w")
        queue_file.write(pickle.dumps(self.__judge_queue, -1))
        queue_file.close()
        # dtree_cs.ox.ac.uk
        dtree_file = open(config.path_judge_dtree, "w")
        dtree_file.write(pickle.dumps(self.__clf, -1))
        dtree_file.close()

    def __refresh_list(self):
        delete_ids = []
        for key, ent in self.__judge_queue.iteritems():
            decision, confidence = self.__auto_judge(ent['feature'])
            if confidence > config.const_CONFIDENCE_THRESHOLD:
                # pretty sure, save to db, and pass to extract
                item = UrlItem.load_db_item(id=key)
                item['is_target'] = decision
                item.save()
                delete_ids.append(key)
                if int(item['is_target']) in [config.const_IS_TARGET_MULTIPLE, config.const_IS_TARGET_SIGNLE]:
                    self.__send_to_extractor(item)
                else:
                    os.remove(config.path_judge_inbox + "/%s" % ent['filename'])
            else:
                self.__judge_queue[key]['confidence'] = confidence
                self.__judge_queue[key]['decision'] = decision
        # clear delete_ids
        for ent_id in delete_ids:
            del self.__judge_queue[ent_id]


    def __auto_judge(self, feature):
        fv = FeatureExtract.vector_feature(feature)
        if self.__clf is not None:
            target = self.__clf.predict(fv)[0]
            confidence = 100 * max(self.__clf.predict_proba(fv)[0])
        else:
            target = -1
            confidence = 0
        return target, confidence

    def __op_new(self, data_loaded, connection):
        item_id = int(data_loaded['id'])
        item = UrlItem.load(id=item_id,file_path=config.path_judge_inbox)
        feature = self.__fe.extract_item(item)

        if 'decision' not in data_loaded.keys():
            decision, confidence = self.__auto_judge(feature)
            log.info("[%s]: [%s] # %s # %s%%" % (item_id, FeatureExtract.str_feature(feature), decision, confidence))
        else:
            decision, confidence = data_loaded['decision'],100
            log.info("[%s]: back from Extractor # %s # %s%%" % (item_id, decision, confidence))
            self.__relearn_clf(feature,decision)

        if confidence > config.const_CONFIDENCE_THRESHOLD:
            # pretty sure, save to db, and pass to extract
            item['is_target'] = decision
            item.save()
            if int(item['is_target']) in [config.const_IS_TARGET_MULTIPLE, config.const_IS_TARGET_SIGNLE]:
                # item is target
                self.__send_to_extractor(item)
            else:
                # item is not target
                os.remove(config.path_judge_inbox + "/%s" % item.filename())
        else:
            # not sure, put it in queue, involving human-being
            item['is_target'] = config.const_IS_TARGET_UNKNOW
            item.save()

            self.__judge_queue[item_id] = {
                "title": item['title'],
                "url": item['url'],
                "filename": item.filename(),
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

        item = UrlItem.load_db_item(id=item_id)
        item['is_target'] = decision
        item.save()

        if int(item['is_target']) in [config.const_IS_TARGET_MULTIPLE, config.const_IS_TARGET_SIGNLE]:
            # item is target
            self.__send_to_extractor(item)
        else:
            # item is not target
            os.remove(config.path_judge_inbox + "/%s" % item.filename())

        self.__relearn_clf(self.__judge_queue[item_id]['feature'],decision)

        del self.__judge_queue[item_id]
        tool.send_msg(connection, "0")
        pass

    def __op_refresh(self, data_loaded, connection):
        self.__refresh_list()

    def __relearn_clf(self,feature,decision):
        self.__F.append(FeatureExtract.vector_feature(feature))
        self.__L.append(decision)

        self.__clf = tree.DecisionTreeClassifier(**self.__dtree_param)
        self.__clf.fit(self.__F, self.__L)

    @staticmethod
    def __operations(cmd):
        maps = {
            config.socket_CMD_judge_new: SAEJudge.__op_new,
            config.socket_CMD_judge_done: SAEJudge.__op_done,
            config.socket_CMD_judge_list: SAEJudge.__op_list,
            config.socket_CMD_judge_refresh: SAEJudge.__op_refresh,
        }
        return maps[cmd]

    @staticmethod
    def __send_to_extractor(item):
        # move file
        shutil.move(config.path_judge_inbox + "/%s" % item.filename(),
                    config.path_extractor_inbox + "/%s" % item.filename())

        # SIGNAL
        data = {"operation": config.socket_CMD_extractor_new,"id": item['id']}
        data_string = pickle.dumps(data, -1)
        tool.send_message(data_string, config.socket_addr_extractor)

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
