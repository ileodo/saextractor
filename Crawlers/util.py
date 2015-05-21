__author__ = 'LeoDong'

import hashlib
import os
import shutil
import re

from scrapy import log
from bs4 import BeautifulSoup

import config


def initial_data_folder(src):
    '''delete files and folders'''
    shutil.rmtree(src)
    os.makedirs(src)


def getLayout(response):
    soup = BeautifulSoup(response.body)

    # remove tags
    for tag in config.layout_tag_remove:
        for t in soup.select(tag):
            t.decompose()

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


def getHashedLayout(response):
    md = hashlib.md5(getLayout(response)).hexdigest()
    log.msg(md, level=log.DEBUG)
    return md


def getContent(response):
    return response.body


def getHashedContent(response):
    return hashlib.md5(getContent(response)).hexdigest()


def getHtmlTitle(response):
    str = "".join(response.xpath('//title/text()').extract());
    if len(str) > 128:
        str = str[0:128]
    return str


def getContentType(response):
    return response.headers['content-type'].split(";")[0]


def getUrl(response):
    return response.url
