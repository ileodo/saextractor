import cPickle as pickle

from django.shortcuts import render

import util.tool as tool
import util.config as config
import socket
# Create your views here.

def index(request):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect(config.socket_addr_judge)

    data_string = pickle.dumps({"operation": config.socket_CMD_judge_list},-1)
    tool.send_msg(sock,data_string)
    data_string = tool.recv_msg(sock)
    ll = pickle.loads(data_string)
    return render(request, 'judge/test.html', {'list': ll})
