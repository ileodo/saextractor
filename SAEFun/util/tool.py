__author__ = 'LeoDong'

import hashlib
import os
import socket
import shutil
import struct
import time
from scrapy import log

import config


def initial_folder(src):
    '''delete files and folders'''
    if os.path.exists(src):
        shutil.rmtree(src)
    os.makedirs(src)


def get_content_type_for_response(response):
    return response.headers['content-type'].split(";")[0]


def hash_for_text(text):
    return hashlib.md5(text).hexdigest()


# socket

def send_msg(sock, msg):
    # Prefix each message with a 4-byte length (network byte order)
    msg = struct.pack('>I', len(msg)) + msg
    sock.sendall(msg)


def recv_msg(sock):
    # Read message length and unpack it into an integer
    raw_msglen = recvall(sock, 4)
    if not raw_msglen:
        return None
    msglen = struct.unpack('>I', raw_msglen)[0]
    # Read the message data
    return recvall(sock, msglen)


def recvall(sock, n):
    # Helper function to recv n bytes or return None if EOF is hit
    data = ''
    while len(data) < n:
        packet = sock.recv(n - len(data))
        if not packet:
            return None
        data += packet
    return data


def send_message(msg, address):
    # Create a TCP/IP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    while True:
        try:
            sock.connect(address)
            break
        except socket.error:
            log.msg("cannot establish connection to socket %s, please open the socket, this will retry in %s seconds" % (str(address), config.socket_retry_seconds), level=log.ERROR)
            time.sleep(config.socket_retry_seconds)
    try:
        # Send data
        send_msg(sock, msg)
        log.msg("send: %s" % str(msg), level=log.DEBUG)

    finally:
        sock.close()
        log.msg("connection closed", level=log.DEBUG)
