__author__ = 'LeoDong'
import re
import os

from bs4 import BeautifulSoup

from util import config

_file_map = {}


def features_extract(item):
    global _part_map
    _part_map = {}
    feature_dict = dict()
    file = open(config.path_onto + "/featurespace.xml")
    onto_soup = BeautifulSoup(file.read())
    features = onto_soup.findAll("feature")
    for f in features:
        key, value = feature(item, f)
        feature_dict[key] = value
        pass
    print sorted(feature_dict.iteritems(), key=lambda d:d[0])
    return feature_dict


def helper_avg(array):
    return sum(array) / len(array)


def helper_prod(array):
    return sum(array) / len(array)


def helper_div(array):
    if len(array) <= 2:
        raise Exception("div for 1 element")
    d = helper_prod(array[1:])
    if d == 0:
        raise Exception("div by 0")
    return array[0] / d


operations = {
    "sum": sum,
    "max": max,
    "min": min,
    "avg": helper_avg,
    "prod": helper_prod,
    "div": helper_div
}


def feature(item, f):
    fid = f.get('id')
    fname = f.get('name')
    policy = f.get('policy', 'sum')
    value = []
    for r in f.children:
        if str(r) == "\n":
            continue
        value.append(rule(item, r))
    return fid+fname, operations[policy](value)


def rule(item, r):
    """
    reg, list
    :param item:
    :param r:
    :return:
    """
    tag = r.name
    sel = r.get('sel', 'text')
    weight = int(r.get("weight", "1"))

    if tag == "reg":
        policy = r.get('policy', "count")
        result = re.findall(r['exp'], get_part_for_item(item, sel))
        if policy == "count":
            value = len(result)
        elif policy == "exist":
            value = int(len(result) != 0)
    elif tag == "list":
        policy = r.get('policy', "sum")
        value_set = []
        for line in load_file_lines(r['src']):
            value_set.append(re.findall(line, get_part_for_item(item, sel)))
        value = operations[policy](value_set)
    else:
        raise Exception("Un-support")
    return weight * value


def load_file_lines(path):
    if path in _file_map.keys():
        return _file_map[path]
    else:
        if os.path.isfile(path):
            file_content = open(path).read()
            _file_map[path] = file_content.strip().split()
        else:
            _file_map[path] = ""
        return _file_map[path]


def get_part_for_item(item, part):
    if part in _part_map.keys():
        return _part_map[part]
    else:
        if part == "text":
            _part_map[part] = item.get_soup().get_text()
        elif part == "html":
            _part_map[part] = str(item.get_soup())
        elif part == "tag":
            _part_map[part] = " ".join(re.findall("<.*?>", get_part_for_item(item, "html")))
        elif part == "title":
            _part_map[part] = " ".join([x.text for x in item.get_soup().find('title')])
        elif part == "keyword":
            tags = item.get_soup.select('meta[name="Keywords"]') + item.get_soup.select('meta[name="keywords"]')
            _part_map[part] = " ".join([x['content'] for x in tags])
        elif part == "description":
            tags = item.get_soup.select('meta[name="Description"]') + item.get_soup.select('meta[name="description"]')
            _part_map[part] = " ".join([x['content'] for x in tags])
        elif part == "url":
            _part_map[part] = item['url']
        return _part_map[part]
