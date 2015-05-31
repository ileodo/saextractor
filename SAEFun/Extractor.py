__author__ = 'LeoDong'
import socket
import cPickle as pickle

import sys

from util import tool
from util import config



# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
print >> sys.stderr, '[Extractor] starting up on %s' % str(config.socket_addr_extractor)
sock.bind(config.socket_addr_extractor)
# Listen for incoming connections
sock.listen(10)

while True:
    # Wait for a connection
    print >> sys.stderr, '[Extractor] waiting for a connection'
    connection, client_address = sock.accept()
    try:
        print >> sys.stderr, '[Extractor] connection from', client_address
        data = tool.recv_msg(connection)
        data_loaded = pickle.loads(data)
        print >> sys.stderr, '[Extractor] received:', str(data_loaded)

    finally:
        # Clean up the connection
        connection.close()
