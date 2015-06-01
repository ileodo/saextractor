import cPickle as pickle
import socket
import errno
from socket import error as socket_error

from django.shortcuts import render

import util.tool as tool
import util.config as config

# Create your views here.

def index(request):
    return render(request, 'app/index.html', {"page": {"title": "Home"}})


def judge(request):
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(config.socket_addr_judge)
    except socket_error as serr:
        return render(request, 'app/error.html',
                      {"page": {"title": "Error"}, "code": serr.errno, "content": str(serr.strerror)})

    data_string = pickle.dumps({"operation": config.socket_CMD_judge_list}, -1)
    tool.send_msg(sock, data_string)
    data_string = tool.recv_msg(sock)
    ll = pickle.loads(data_string)
    return render(request, 'app/judge.html', {"page": {"title": "Judge"}, 'judge_list': ll})

def extract(request):
    return render(request, 'app/extract.html', {"page": {"title": "Extract"}})


def result(request):
    return render(request, 'app/result.html', {"page": {"title": "Result"}})

