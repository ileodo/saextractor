__author__ = 'LeoDong'

import socket
import cPickle as pickle

import sys

from util import tool
from extractor.SAEExtractor import SAEExtractor
from util import config

import logging
from util.logger import log

# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
log.info('start listening on %s' % str(config.socket_addr_extractor))
sock.bind(config.socket_addr_extractor)
# Listen for incoming connections
sock.listen(10)
extractor = SAEExtractor()

while True:
    # Wait for a connection
    connection, client_address = sock.accept()
    extractor.process(connection,client_address)
