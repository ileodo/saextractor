# -*- coding: utf-8 -*-

# Define here the models for your scraped items
#
# See documentation in:
# http://doc.scrapy.org/en/latest/topics/items.html

import re

import scrapy
from scrapy import log
from bs4 import BeautifulSoup

from util import db, config, tool


class UrlItem(scrapy.Item):
    # attribute in DB
    id = scrapy.Field()
    url = scrapy.Field()
    is_target = scrapy.Field()
    content_hash = scrapy.Field()
    layout_hash = scrapy.Field()
    rule_id = scrapy.Field()
    last_access_ts = scrapy.Field()
    last_extract_ts = scrapy.Field()
    title = scrapy.Field()
    content_type = scrapy.Field()

    # attribute from response/file
    content = scrapy.Field()  # str
    bs = scrapy.Field()  # str
    map_part = scrapy.Field()

    def save(self):
        db.general_update_url(self['id'], self['is_target'], self['content_hash'], self['layout_hash'], self['rule_id'],
                              self['title'], self['content_type'])

    @staticmethod
    def load(id=None, url=None, file_path=None, response=None):
        r = UrlItem.load_db_item(id=id, url=url)
        if r is None:
            return None
        if response is not None:
            r['content'] = response.body
            r['content_type'] = tool.get_content_type_for_response(response)
        elif file_path is not None:
            r['content'] = open(file_path + "/" + r.filename()).read()
        else:
            raise Exception('must provide file_path or response')

        # generate soup
        r['bs'] = BeautifulSoup(r['content'])
        import urlparse
        for k, v in config.retriever_absolute_url_replace_pattern.iteritems():
            tags = r.soup().findAll(k, {v: True})
            for tag in tags:
                tag[v] = urlparse.urljoin(r['url'], tag[v])
        return r

    @staticmethod
    def load_db_item(id=None, url=None):
        r = UrlItem()
        if id is not None:
            res = db.get_url_by_id(id)
        elif url is not None:
            res = db.get_url_by_url(url)
        else:
            raise Exception("must provide id or url")
        if res is None:
            return None
        r['id'] = res['id']
        r['url'] = res['url']
        r['is_target'] = res['is_target']
        r['content_hash'] = res['content_hash']
        r['layout_hash'] = res['layout_hash']
        r['rule_id'] = res['rule_id']
        r['last_access_ts'] = res['last_access_ts']
        r['last_extract_ts'] = res['last_extract_ts']
        r['title'] = res['title']
        r['content_type'] = res['content_type']
        return r



    def filename(self):
        ext = self['content_type'].split('/')[1]
        filename = "%s.%s" % (self['id'], ext)
        return filename


    """
    ''' from tree
    """

    def soup(self):
        return self['bs']

    def raw_content(self):
        return self['content']

    def get_part(self,part):
        if 'map_part' not in self.keys():
            self['map_part']=dict()

        if part in self['map_part'].keys():
            return self['map_part'][part]
        else:
            if part == "text":
                self['map_part'][part] = self.soup().get_text("\n")
            elif part == "html":
                self['map_part'][part] = str(self.soup())
            elif part == "tag":
                self['map_part'][part] = " ".join(re.findall("<.*?>", self.get_part("html")))
            elif part == "title":
                self['map_part'][part] = " ".join([x.text for x in self.soup().findAll('title')])
            elif part == "keyword":
                tags = self.soup().select('meta[name="Keywords"]') + self.soup().select('meta[name="keywords"]')
                self['map_part'][part] = " ".join([x['content'] for x in tags])
            elif part == "description":
                tags = self.soup().select('meta[name="Description"]') + self.soup().select(
                    'meta[name="description"]')
                self['map_part'][part] = " ".join([x['content'] for x in tags])
            elif part == "url":
                self['map_part'][part] = self['url']
            return self['map_part'][part]

    def get_short_title(self):
        title = self.get_part('title')
        if len(title) > 128:
            title = title[0:128]
        return title

    def get_layout(self):
        # copy soup
        soup = BeautifulSoup(str(self.soup()))
        # remove tags
        for tag in config.layout_tag_remove:
            for t in soup.select(tag):
                t.decompose()

        if soup.body is None:
            result = ""
        else:
            result = soup.body.prettify()

        # Comments
        r = re.compile(r"<!.*?>", re.S)
        result = r.sub("", result)

        # Content
        r = re.compile(r"(?<=>).*?(?=<)", re.S)
        result = r.sub("", result)

        # attributes (remove attributes)
        r = "|".join(
            ["(?<=<" + x + " ).*?(?=(/)?>)" for x in config.layout_tag_clear_attr] +
            [" " + x + "=\".*?\"" for x in config.layout_attr_remove] +
            ["(?<= " + x + "=\").*?(?=\")" for x in config.layout_attr_clear]
        )
        r = re.compile(r, re.S)
        result = r.sub("", result)

        soup = BeautifulSoup(result)
        log.msg(str(soup), level=log.DEBUG)
        return str(soup)
