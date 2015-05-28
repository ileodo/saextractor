__author__ = 'LeoDong'

import hashlib
import os
import socket
import shutil

from bs4 import BeautifulSoup
from scrapy import log


def initial_folder(src):
    '''delete files and folders'''
    if os.path.exists(src):
        shutil.rmtree(src)

    os.makedirs(src)


def get_tree_for_response(response):
    return BeautifulSoup(response.body)


def get_content_type_for_response(response):
    return response.headers['content-type'].split(";")[0]


def hash_for_text(text):
    return hashlib.md5(text).hexdigest()


def send_message(msg, address, port):
    # Create a TCP/IP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    # Connect the socket to the port where the server is listening
    server_address = (address, port)
    sock.connect(server_address)

    try:
        # Send data

        sock.sendall(msg)
        log.msg("send: %s" % str(msg))

    finally:
        sock.close()
        log.msg("connection closed")
