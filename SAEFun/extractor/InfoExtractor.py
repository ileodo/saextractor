__author__ = 'LeoDong'

import re

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
                = dict(name=attr['name'],
                       description=attr['description'],
                       db_col=attr['db-col'],
                       filename=attr.contents[0].string,
                       max_len=int(attr['max-len']))
            self.__load_rule_file(int(attr['id']))

        self.__map_target_part = dict()
        pass

    def __load_rule_file(self, attrid):
        rule_file = BeautifulSoup(open(
            self.filepath(attrid)
        ).read(), 'xml')
        self.__extractspace[attrid]['rules'] = dict()

        self.__map_rulesoup[attrid] = rule_file
        ruleset = rule_file.findAll('rule')
        for rule in ruleset:
            rule_id, rule_entity = InfoExtractor.__rule_xml2dict(rule)
            self.__extractspace[attrid]['rules'][rule_id] = rule_entity

    def filepath(self, attrid):
        return self.__path_rule_files + "/" + self.map(attrid)['filename']

    def num_attr(self):
        return len(self.__extractspace)

    # def rules(self,attrid=None):

    @staticmethod
    def __rule_xml2dict(soup_node):
        rule_id = int(soup_node['id'])
        d = dict(
            description=soup_node['description'],
            on=soup_node['on'],
        )
        if d['on'] == 'content':
            d['scope'] = dict(
                sel=soup_node.scope['sel'],
                target=soup_node.scope['target']
            )
        if soup_node.match:
            d['match'] = soup_node.match['reg']
        if soup_node.substring:
            d['substring'] = dict(
                after=soup_node.substring['after'],
                before=soup_node.substring['before']
            )

        d['actions'] = list()

        for act in soup_node.actions.findAll("action"):
            d['actions'].append(int(act['id']))

        return rule_id, d

    @staticmethod
    def __rule_dict2xml(d, new_id):
        gen = BeautifulSoup("", "xml")
        rule_tag = gen.new_tag("rule", id=new_id, description=d['description'], on=d['on'])

        if d['on'] == 'content':
            scope_tag = gen.new_tag(
                "scope",
                sel=d['scope']['sel'],
                target=d['scope']['target']
            )
            rule_tag.append(scope_tag)

        if 'match' in d.keys():
            match_tag = gen.new_tag("match", reg=d['match'])
            rule_tag.append(match_tag)

        if 'substring' in d.keys():
            substring_tag = gen.new_tag(
                "substring",
                after=d['substring']['after'],
                before=d['substring']['before']
            )
            rule_tag.append(substring_tag)

        actions_tag = gen.new_tag("actions")
        rule_tag.append(actions_tag)
        for action_id in d['actions']:
            actions_tag.append(gen.new_tag("action", id=action_id))
        return rule_tag

    def extract(self, item, extractor):
        result = dict()
        attr = 1  # counter
        for rule in extractor:
            if rule != 0:
                result[attr] = self.extract_attr(item, rule, attr)
            else:
                result[attr] = ""
            attr += 1
        return result

    def extract_attr(self, item, rule_id_or_dict, attr_id=None):
        if type(rule_id_or_dict) == int:
            rule = self.map(attr_id)['rules'][rule_id_or_dict]
        else:
            rule = rule_id_or_dict

        ## scope
        if rule['on'] == 'content':
            target = item.get_part('soup')
            if rule['scope']['sel'] == "":
                sel = 'html'
            else:
                sel = rule['scope']['sel']
            try:
                target = target.select(sel)

                if len(target) > 0:
                    if rule['scope']['target'] == 'html':
                        target = " ".join([str(x) for x in target])
                    elif rule['scope']['target'] == 'text':
                        target = " ".join([x.get_text() for x in target])
                else:
                    target = ""
            except:
                target=""
        else:
            target = item.get_part(rule['on'])

        ## match
        if 'match' in rule.keys():
            try:
                p = re.compile(rule['match'], re.I | re.S)
            except re.error:
                return ""

            target = " ".join(p.findall(target))

        ## substring
        if 'substring' in rule.keys():
            if rule['substring']['after'] == "":
                after = "^"
            else:
                after = rule['substring']['after']

            if rule['substring']['before'] == "":
                before = "$"
            else:
                before = rule['substring']['before']

            try:
                p = re.compile(
                    r"%s(?P<_TARGET_>.*?)%s" % (after, before),
                    re.S
                )
            except re.error:
                return ""

            match = p.search(target)
            if match is None:
                target = ""
            else:
                target = match.group('_TARGET_')

        ## action
        for act in rule['actions']:
            target = self.action(int(act))['do'](target)

        if isinstance(target, unicode):
            return target.encode('utf-8')
        else:
            if target is None:
                return ""
            else:
                return target

    def add_rule(self, attr_id, rule_dict):
        ## lookup max id in attr_id's rules
        if len(self.map(attr_id)['rules'].keys()) == 0:
            new_id = 1
        else:
            new_id = max(self.map(attr_id)['rules'].keys()) + 1
        self.map(attr_id)['rules'][new_id] = rule_dict
        rule_node = InfoExtractor.__rule_dict2xml(rule_dict, new_id)
        self.rulefile_map(attr_id).ruleset.append(rule_node)

        ## write soup_node to file
        filepath = self.filepath(attr_id)
        f = open(filepath, 'w')
        f.write(self.rulefile_map(attr_id).prettify())
        f.close()
        return new_id

    def map(self, attrid=None):
        if attrid in self.__extractspace.keys():
            return self.__extractspace[attrid]
        elif attrid is None:
            return self.__extractspace
        else:
            raise Exception('none exist attribute')

    @staticmethod
    def action(action_id=None):
        actions = {
            1: {
                'name': 'removeHTML',
                'do': InfoExtractor.__act_removeHTML
            },
            2: {
                'name': 'stripe',
                'do': InfoExtractor.__act_stripe
            },
        }
        if action_id in actions.keys():
            return actions[action_id]
        elif action_id is None:
            return actions
        else:
            raise Exception('none exist action')

    @staticmethod
    def action_map():
        map = InfoExtractor.action()
        for i in map.keys():
            map[i]['do'] = None
        return map

    @staticmethod
    def __act_removeHTML(str):
        str = re.sub("<.*?>", "", str)
        return str

    @staticmethod
    def __act_stripe(str):
        return str.strip()

    def name(self, attrid=None):
        if attrid in xrange(1,self.num_attr()+1):
            return self.__extractspace[attrid]['name']
        elif attrid is None:
            return self.__extractname
        else:
            raise Exception('none exist attribute')

    def db_col(self, attrid):
        if attrid in xrange(1,self.num_attr()+1):
            return self.__extractspace[attrid]['db_col']
        else:
            raise Exception('none exist attribute')

    def max_len(self, attrid):
        if attrid in xrange(1,self.num_attr()+1):
            return self.__extractspace[attrid]['max_len']
        else:
            raise Exception('none exist attribute')

    def rulefile_map(self, attrid):
        if attrid in self.__map_rulesoup.keys():
            return self.__map_rulesoup[attrid]
        elif attrid is None:
            return self.__map_rulesoup
        else:
            raise Exception('none exist attribute')
