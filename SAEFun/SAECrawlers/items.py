# -*- coding: utf-8 -*-

# Define here the models for your scraped items
#
# See documentation in:
# http://doc.scrapy.org/en/latest/topics/items.html

import re

import scrapy
from scrapy import log
from bs4 import BeautifulSoup

from util import db, config


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
    content = scrapy.Field()

    # attribute from response
    raw_content = scrapy.Field()  # str
    soup = scrapy.Field()  # str
    content_type = scrapy.Field()  # str

    def save(self):
        db.general_update_url(self['id'], self['is_target'], self['content_hash'], self['layout_hash'], self['rule_id'],
                              self['title'])

    def get_soup(self):
        if 'soup' in self.keys():
            return self['soup']
        else:
            if 'content' in self.keys():
                self['soup'] = BeautifulSoup(self['content'])
                import urlparse

                for k, v in config.retriever_absolute_url_replace_pattern.iteritems():
                    tags = self['soup'].findAll(k, {v:True})
                    for tag in tags:
                        tag[v] = urlparse.urljoin(self['url'], tag[v])
                return self['soup']
            else:
                raise Exception("sss")

    @staticmethod
    def s_load_id(id):
        r = UrlItem()
        r.load_id(id)
        return r

    @staticmethod
    def s_load_url(url):
        r = UrlItem()
        r.load_url(url)
        return r

    def load_dict(self, res):
        self['id'] = res['id']
        self['url'] = res['url']
        self['is_target'] = res['is_target']
        self['content_hash'] = res['content_hash']
        self['layout_hash'] = res['layout_hash']
        self['rule_id'] = res['rule_id']
        self['last_access_ts'] = res['last_access_ts']
        self['last_extract_ts'] = res['last_extract_ts']
        self['title'] = res['title']

    def load_id(self, id):
        res = db.get_url_by_id(id)
        self.load_dict(res)

    def load_url(self, url):
        res = db.get_url_by_url(url)
        self.load_dict(res)

    def filename(self):
        ext = self['content_type'].split('/')[1]
        filename = "%s.%s" % (self['id'], ext)
        return filename

    """
    ''' from tree
    """

    def title_of_tree(self):
        title = self.get_soup().find("title")
        if title is None:
            title = ""
        else:
            title = title.text
        if len(title) > 128:
            title = title[0:128]
        return title

    def layout_of_tree(self):
        # copy soup
        soup = BeautifulSoup(str(self.get_soup()))
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
