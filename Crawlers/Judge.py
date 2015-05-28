__author__ = 'LeoDong'
import socket
import sys
from util import config
import cPickle as pickle
# Create a TCP/IP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

# Bind the socket to the port
server_address = (config.socket_addr_judge, config.socket_port_judge)
print >>sys.stderr, '[Judge] starting up on %s port %s' % server_address
sock.bind(server_address)

# Listen for incoming connections
sock.listen(1)

while True:
    # Wait for a connection
    print >>sys.stderr, '[Judge] waiting for a connection'
    connection, client_address = sock.accept()
    try:
        print >>sys.stderr, '[Judge] connection from', client_address
        data = ""
        # Receive the data in small chunks and retransmit it
        while True:
            tmp = connection .recv(16)
            if tmp:
                data = data + tmp
            else:
                data_loaded = pickle.loads(data)
                print >>sys.stderr, '[Judge] received:', str(data_loaded)
                break

    finally:
        # Clean up the connection
        connection.close()