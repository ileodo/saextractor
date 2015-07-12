import cPickle as pickle
import socket
from socket import error as socket_error
import logging

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


def result(request):
    return render(request, 'app/result.html', {"page": {"title": "Result"}})


def ajaxExtractor(request):
    def re_judge(post,sock):
        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_rejudge_done,
                "id": post.get("id"),
                "decision": post.get("decision")
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        return data_string

    def rule_test(post,sock):
        d = rule_post2dict(post)

        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_test_rule,
                "id": post.get("id"),
                "attrid": post.get("attrid"),
                "rule": d
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        if data_string:
            return data_string
        else:
            return '#ERROR'

    def rule_add(post,sock):
        d = rule_post2dict(post)
        selected = post.getlist('selected[]')
        selected = [int(x) for x in selected]
        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_add_rule,
                "attrid": post.get("attrid"),
                "rule": d
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        maps = pickle.loads(data_string)
        return render(request, 'app/extract_modal_rule.html',
                      {"page": {"title": "Extract"}, 'rule_maps': maps['rule'], 'action': maps['action'],'selected':selected})

    def preview(post,sock):
        extractor = [int(x) for x in post.getlist('extractor[]')]

        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_preview,
                "id": post.get("id"),
                "extractor": extractor
            }, -1)
        log.info(data_string)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        preview = pickle.loads(data_string)
        return render(request, 'app/extract_modal_preview.html', {'preview': preview})

    def extract(post,sock):
        selected = post.getlist('selected[]')
        selected = [int(x) for x in selected]
        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_extractor_add_extract,
                "id": post.get("id"),
                "extractor": selected
            }, -1)

        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        return data_string

    if request.method == 'POST':
        operations_list = {
            're_judge': re_judge,
            'rule_test': rule_test,
            'rule_add': rule_add,
            'preview': preview,
            'extract': extract,
        }
        op_method = request.POST.get('method')
        log.info(str(request.POST))
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect(config.socket_addr_extractor)
        except socket_error as serr:
            return '{"status": "error", "code": serr.errno, "msg": str(serr.strerror)}'

        return HttpResponse(operations_list[op_method](request.POST,sock))
    else:
        return HttpResponse("ILLEGAL")

def ajaxJudge(request):
    def judge(post,sock):
        data_string = pickle.dumps(
            {
                "operation": config.socket_CMD_judge_done,
                "id": post.get("id"),
                "decision": post.get("decision")
            }, -1)
        tool.send_msg(sock, data_string)
        data_string = tool.recv_msg(sock)
        return data_string

    if request.method == 'POST':
        operations_list = {
            'judge': judge,
        }
        op_method = request.POST.get('method')
        log.info(str(request.POST))
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.connect(config.socket_addr_judge)
        except socket_error as serr:
            return '{"status": "error", "code": serr.errno, "msg": str(serr.strerror)}'

        return HttpResponse(operations_list[op_method](request.POST,sock))
    else:
        return HttpResponse("ILLEGAL")


def rule_post2dict(post):
    d = dict(
        on=post.get('rule[on]'),
        description=post.get('rule[description]')
    )
    if post.get('rule[on]') == "content":
        d['scope'] = dict(
            sel=post.get('rule[scope[sel]]'),
            target=post.get('rule[scope[target]]')
        )
    if post.get('rule[match]') != "":
        d['match'] = post.get('rule[match]')

    d['substring'] = dict(
        after=post.get('rule[substring[after]]'),
        before=post.get('rule[substring[before]]')
    )

    d['actions'] = post.getlist('rule[actions][]')
    if len(d['actions'])==0:
        d['actions'] = post.getlist('rule[actions]')
    return d

def loadfile(request, type, filename):
    if type == "judge":
        path = config.path_judge_inbox
    elif type == "extract":
        path = config.path_extractor_inbox
    file = open(path + "/" + filename)
    return HttpResponse(file.read())
