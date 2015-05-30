__author__ = 'LeoDong'

import socket

import sys

from util import config
import SAEJudge.JudgeQueue


# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
print >> sys.stderr, '[Judge] starting up on %s' % str(config.socket_addr_judge)
sock.bind(config.socket_addr_judge)
# Listen for incoming connections
sock.listen(10)

while True:
    # Wait for a connection
    print >> sys.stderr, '[Judge] waiting for a connection'
    connection, client_address = sock.accept()
    SAEJudge.JudgeQueue.process(connection, client_address)
