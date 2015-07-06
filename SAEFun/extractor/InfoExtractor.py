__author__ = 'LeoDong'

from bs4 import BeautifulSoup


class InfoExtractor:
    def __init__(self, extract_space_file_path, rule_files_path):
        soup = BeautifulSoup(open(extract_space_file_path).read(), 'xml')
        attrlist = soup.findAll('attribute')

        self.__extractspace = dict()
        self.__extractname = soup.entity['name']
        self.__path_rule_files = rule_files_path
        self.__map_rulesoup = dict()

        for attr in attrlist:
            self.__extractspace[int(attr['id'])] \
                = dict(name=attr['name'], description=attr['description'], filename=attr.contents[0].string)
            self.__load_rule_file(int(attr['id']))

        self.__map_target_part = dict()
        pass

    def __load_rule_file(self, attrid):
        rule_file = BeautifulSoup(open(
            self.__path_rule_files + "/" + self.map(attrid)['filename']
        ).read(), 'xml')
        self.__extractspace[attrid]['rules'] = dict()

        self.__map_rulesoup[attrid] = rule_file
        ruleset = rule_file.findAll('rule')
        for rule in ruleset:
            self.__extractspace[attrid]['rules'] \
                = dict(self.__extractspace[attrid]['rules'], **InfoExtractor.__rule_xml2dict(rule))

    def num_attr(self):
        return len(self.__extractspace)

    # def rules(self,attrid=None):

    @staticmethod
    def __rule_xml2dict(soup_node):
        d = dict()
        rule_id = soup_node['id']
        d[rule_id] = dict(
            description=soup_node['description'],
            select=soup_node['on'],
            policy=soup_node.find("extract")['policy'],
        )
        if d[rule_id]['policy'] == 'xpath':
            d[rule_id]['xpath'] = soup_node.find("extract").string.strip()
        if d[rule_id]['policy'] == 'string':
            d[rule_id]['after'] = soup_node.find("after").string
            d[rule_id]['before'] = soup_node.find("before").string

        d[rule_id]['action'] = list()

        for act in soup_node.findAll("action"):
            d[rule_id]['action'].append(int(act['id']))

        return d

    @staticmethod
    def __rule_dict2xml(d):
        pass

    def map(self, attrid=None):
        if attrid in self.__extractspace.keys():
            return self.__extractspace[attrid]
        elif attrid is None:
            return self.__extractspace
        else:
            raise Exception('none exist attribute')

    @staticmethod
    def action(action_id=None):
        action = {
            1: {
                'name': 'removeHTML',
                'do': InfoExtractor.__act_removeHTML
            },
            2: {
                'name': 'stripe',
                'do': InfoExtractor.__act_stripe
            },
        }
        if action_id in action.keys():
            return action[action_id]
        elif action_id is None:
            return action
        else:
            raise Exception('none exist action')

    @staticmethod
    def action_map(action_id=None):
        map = InfoExtractor.action()
        for i in map.keys():
            map[i]['do']=None
        return map

    @staticmethod
    def __act_removeHTML():
        pass

    @staticmethod
    def __act_stripe():
        pass

    def name(self):
        return self.__extractname

    def rulefile_map(self):
        return self.__map_rulesoup
