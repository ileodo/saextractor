__author__ = 'LeoDong'
import socket
import sys
import Queue
from util import tool
from util import config
import cPickle as pickle

judge_queue = []

def op_new(data_loaded, connection):
    global judge_queue
    judge_queue.append(data_loaded)
    pass

def op_list(data_loaded, connection):
    global judge_queue
    tool.send_msg(connection, pickle.dumps(judge_queue, -1))
    pass

def op_done(data_loaded, connection):
    pass

operations = {
    config.socket_CMD_judge_new: op_new,
    config.socket_CMD_judge_done: op_done,
    config.socket_CMD_judge_list: op_list,
}

def process(connection,client_address):
    global judge_queue
    try:
        print >>sys.stderr, '[Judge] connection from', client_address
        data = tool.recv_msg(connection)
        data_loaded = pickle.loads(data)
        print >>sys.stderr, '[Judge] received:', str(data_loaded)
        operations[data_loaded['operation']](data_loaded, connection)
    finally:
        # Clean up the connection
        connection.close()

