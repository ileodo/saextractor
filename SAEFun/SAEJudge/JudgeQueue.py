
__author__ = 'LeoDong'
import cPickle as pickle
import random

from FeatueExtract import features_extract
from SAECrawlers.items import UrlretriverItem
from util import tool
from util import config
from util.logger import log

judge_queue = {}
# id : item{title, url, filename, confidence, decision }

def auto_judge(item):
    target = random.randint(-1, 2)
    confidence = random.randint(0, 100)
    return target, confidence

def feature_for(item):
    features_extract(item)

def send_to_extractor(item):
    # MOVE FILE
    ext = item['content_type'].split('/')[1]
    filename = "%s.%s" % (item['id'], ext)
    f = open(config.path_inbox_extractor + "/%s" % filename, 'w')
    f.write(str(item['content']))
    f.close()
    # SIGNAL
    data = {"id": item['id'], "filename": filename, "type": ext}
    data_string = pickle.dumps(data, -1)
    tool.send_message(data_string, config.socket_addr_extractor)


def op_new(data_loaded, connection):
    global judge_queue
    item_id = int(data_loaded['id'])
    item = data_loaded['item']

    decision, confidence = auto_judge(item)

    if confidence > 50:
        # pretty sure, save to db, and pass to extract
        item['is_target'] = decision
        item.save()
        send_to_extractor(item)
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

        judge_queue[item_id] = {
            "title": item['title'],
            "url": item['url'],
            "filename": filename,
            "confidence": confidence,
            "decision": decision
        }
    pass


def op_list(data_loaded, connection):
    global judge_queue
    tool.send_msg(connection, pickle.dumps(judge_queue, -1))
    pass


def op_done(data_loaded, connection):
    global judge_queue
    item_id = int(data_loaded['id'])
    decision = int(data_loaded['decision'])
    item = UrlretriverItem.s_load_id(item_id)
    item['is_target'] = decision
    item.save()
    del judge_queue[item_id]
    tool.send_msg(connection, "0")
    pass


operations = {
    config.socket_CMD_judge_new: op_new,
    config.socket_CMD_judge_done: op_done,
    config.socket_CMD_judge_list: op_list,
}


def process(connection, client_address):
    global judge_queue
    try:
        data = tool.recv_msg(connection)
        data_loaded = pickle.loads(data)
        log.info('new connection from %s', client_address)
        log.debug("data received: %s", data_loaded)
        operations[data_loaded['operation']](data_loaded, connection)
    finally:
        # Clean up the connection
        log.info('connection closed for %s', client_address)
        connection.close()
