__author__ = 'LeoDong'
import socket
import sys
from util import config
import cPickle as pickle


# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
print >> sys.stderr, '[Judge] starting up on %s' % str(config.socket_addr_extractor)
sock.bind(config.socket_addr_extractor)
# Listen for incoming connections
sock.listen(10)

while True:
    # Wait for a connection
    print >>sys.stderr, '[Extractor] waiting for a connection'
    connection, client_address = sock.accept()
    try:
        print >>sys.stderr, '[Extractor] connection from', client_address
        data = ""
        # Receive the data in small chunks and retransmit it
        while True:
            tmp = connection .recv(16)
            if tmp:
                data = data + tmp
            else:
                data_loaded = pickle.loads(data)
                print >>sys.stderr, '[Extractor] received:', str(data_loaded)
                break

    finally:
        # Clean up the connection
        connection.close()