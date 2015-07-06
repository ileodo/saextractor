__author__ = 'LeoDong'
import re
import os

from bs4 import BeautifulSoup

from util import config


class FeatureExtract:
    def __init__(self, featurespace):
        file = open(featurespace)
        self.__featurespace = BeautifulSoup(file.read())
        self.__file_map = {}
        pass

    def extract_item(self, item):
        feature_dict = dict()
        features = self.__featurespace.findAll("feature")
        for f in features:
            key, value = self.__extract_feature(item, f)
            feature_dict[key] = value
            pass
        return feature_dict

    """private
    """

    def __extract_feature(self, item, f):
        fid = f.get('id')
        fname = f.get('name')
        policy = f.get('policy', 'sum')
        value = []
        for r in f.children:
            if str(r) == "\n":
                continue
            value.append(self.__extract_rule(item, r))
        return fid, self.__map_policy(policy)(value)

    def __extract_rule(self, item, r):
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
            p = re.compile(r.text, re.I)
            result = p.findall(item.get_part(sel))
            if policy == "count":
                value = len(result)
            elif policy == "exist":
                value = int(len(result) != 0)
        elif tag == "list":
            policy = r.get('policy', "sum")
            value_set = []
            lines = self.__load_file_lines(r.text)
            for line in lines:
                p = re.compile(line, re.I)
                tmp = p.findall(item.get_part(sel))
                value_set.append(len(tmp))
            value = self.__map_policy(policy)(value_set)
        else:
            raise Exception("Un-support")
        return weight * value

    def __load_file_lines(self, path):
        if path in self.__file_map.keys():
            return self.__file_map[path]
        else:
            if os.path.isfile(config.path_fe + "/" + path):
                file_content = open(config.path_fe + "/" + path).read()
                self.__file_map[path] = file_content.strip().split("\n")
            else:
                self.__file_map[path] = ""
            return self.__file_map[path]

    @staticmethod
    def __helper_avg(array):
        return sum(array) / len(array)

    @staticmethod
    def __helper_prod(array):
        return sum(array) / len(array)

    @staticmethod
    def __helper_div(array):
        if len(array) < 2:
            raise Exception("div for 1 element")
        d = FeatureExtract.__helper_prod(array[1:])
        if d == 0:
            return array[0]
            # raise Exception("div by 0")
        return array[0] / d

    @staticmethod
    def __map_policy(policy):
        map = {
            "sum": sum,
            "max": max,
            "min": min,
            "avg": FeatureExtract.__helper_avg,
            "prod": FeatureExtract.__helper_prod,
            "div": FeatureExtract.__helper_div
        }
        return map[policy]

    @staticmethod
    def str_feature(feature, spliter=','):
        # return feature
        return spliter.join(FeatureExtract.vector_feature(feature))

    @staticmethod
    def vector_feature(feature):
        return [str(x[1]) for x in sorted(feature.iteritems(), key=lambda d: d[0])]

    def print_featuremap(self):
        features = self.__featurespace.findAll("feature")
        for f in features:
            fid = f.get('id')
            fname = f.get('name')
            fdes = f.get('description', "n/a")
            policy = f.get('policy', 'sum')
            print "%s\t%s\t%s\n%s\n===========" % (fid, fname, policy, fdes)

    def str_featuremap_line(self,spliter=','):
        features = self.__featurespace.findAll("feature")
        line = []
        for f in features:
            line.append(f.get('id')+"|"+f.get('name'))
        return spliter.join(line)

    pass
