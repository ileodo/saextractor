__author__ = 'LeoDong'

import hashlib
import os
import socket
import shutil
import struct
import time

import config
import db


def init_working_path():
    initial_folder(config.path_working)
    initial_folder(config.path_extractor_inbox)
    initial_folder(config.path_judge_inbox)
    pass

def init_database():
    db.reset_db()
    pass


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
            from util.logger import log
            log.error("cannot establish connection to socket %s, please open the socket, this will retry in %s seconds" % (str(address), config.socket_retry_seconds))
            time.sleep(config.socket_retry_seconds)
            sock.close()
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        # Send data
        send_msg(sock, msg)

    finally:
        sock.close()
