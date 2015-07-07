import cPickle as pickle
import socket
from socket import error as socket_error
import logging
import django.utils.html as html

from django.http import HttpResponse
from django.shortcuts import render

import util.tool as tool
import util.config as config

log = logging.getLogger("django")
log.setLevel(logging.INFO)
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
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(config.socket_addr_extractor)
    except socket_error as serr:
        return render(request, 'app/error.html',
                      {"page": {"title": "Error"}, "code": serr.errno, "content": str(serr.strerror)})

    data_string = pickle.dumps({"operation": config.socket_CMD_extractor_list}, -1)
    tool.send_msg(sock, data_string)
    data_string = tool.recv_msg(sock)
    ll = pickle.loads(data_string)
    return render(request, 'app/extract.html', {"page": {"title": "Extract"}, 'ext_list': ll})


def extract_modal_rule(request):
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(config.socket_addr_extractor)
    except socket_error as serr:
        return render(request, 'app/error.html',
                      {"page": {"title": "Error"}, "code": serr.errno, "content": str(serr.strerror)})

    data_string = pickle.dumps({"operation": config.socket_CMD_extractor_maps}, -1)
    tool.send_msg(sock, data_string)
    data_string = tool.recv_msg(sock)
    maps = pickle.loads(data_string)
    return render(request, 'app/extract_modal_rule.html',
                  {"page": {"title": "Extract"}, 'rule_maps': maps['rule'], 'action': maps['action']})


def extract_modal_preview(request):
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(config.socket_addr_extractor)
    except socket_error as serr:
        return render(request, 'app/error.html',
                      {"page": {"title": "Error"}, "code": serr.errno, "content": str(serr.strerror)})

    data_string = pickle.dumps({"operation": config.socket_CMD_extractor_preview}, -1)
    tool.send_msg(sock, data_string)
    data_string = tool.recv_msg(sock)
    preview = pickle.loads(data_string)
    return render(request, 'app/extract_modal_preview.html', {"page": {"title": "Extract"}, 'preview': preview})


def result(request):
    return render(request, 'app/result.html', {"page": {"title": "Result"}})


def ajax(request):
    def judge_finish(post):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect(config.socket_addr_judge)
        except socket_error as serr:
            return '{"status": "error", "code": serr.errno, "msg": str(serr.strerror)}'

        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_judge_done,
                "id": post.get("id"),
                "decision": post.get("decision")
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        if data_string == "0":
            return '{"status": "success"}'
        else:
            return '{"status": "failed"}'

    def re_judge_finish(post):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect(config.socket_addr_extractor)
        except socket_error as serr:
            return '{"status": "error", "code": serr.errno, "msg": str(serr.strerror)}'

        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_rejudge_done,
                "id": post.get("id"),
                "decision": post.get("decision")
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        if data_string == "0":
            return '{"status": "success"}'
        else:
            return '{"status": "failed"}'

    def extract_rule_test(post):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect(config.socket_addr_extractor)
        except socket_error as serr:
            return '{"status": "error", "code": serr.errno, "msg": str(serr.strerror)}'


        d=dict(
            on=post.get('rule[on]'),
            description=post.get('rule[description]')
        )
        if post.get('rule[on]')=="content":
            d['scope']=dict(
                sel=post.get('rule[scope[sel]]'),
                target=post.get('rule[scope[target]]')
            )
        if post.get('rule[match]')!="":
            d['match']=post.get('rule[match]')

        if post.get('rule[substring[after]]') !="" and post.get('rule[substring[before]]') !="":
            d['substring'] = dict(
                after=post.get('rule[substring[after]]'),
                before=post.get('rule[substring[before]]')
            )

        if post.get('rule[actions]') is None:
            d['actions']=list()
        else:
            d['actions']=post.get('rule[actions]')

        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_test_rule,
                "id": post.get("id"),
                "attrid":post.get("attrid"),
                "rule": d
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        if data_string:
            return data_string
        else:
            return '#ERROR'

    if request.method == 'POST':
        operations_list = {
            'judge_finish': judge_finish,
            're_judge_finish': re_judge_finish,
            'extract_rule_test': extract_rule_test,
        }
        op_type = request.POST.get('type')
        log.info(str(request.POST))
        return HttpResponse(operations_list[op_type](request.POST))
    else:
        return HttpResponse("ILLEGAL")


def loadfile(request, type, filename):
    if type == "judge":
        path = config.path_judge_inbox
    elif type == "extract":
        path = config.path_extractor_inbox
    file = open(path + "/" + filename)
    return HttpResponse(file.read())
