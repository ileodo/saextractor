__author__ = 'LeoDong'

import socket

import sys

from util import config
from SAEJudge.JudgeQueue import JudgeQueue
import logging
from util.logger import log

#TODO unique id in queue, store to file and reload.
# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
log.info('start listening on %s' % str(config.socket_addr_judge))
sock.bind(config.socket_addr_judge)
# Listen for incoming connections
sock.listen(10)
queue = JudgeQueue()

while True:
    # Wait for a connection
    connection, client_address = sock.accept()
    log.info('new connection from %s',client_address)
    queue.process(connection, client_address)