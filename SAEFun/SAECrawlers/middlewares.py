__author__ = 'LeoDong'

from scrapy.utils.httpobj import urlparse_cached
from scrapy.exceptions import IgnoreRequest
from items import UrlItem
from util import config, tool, db
import logging


class CustomDownloaderMiddleware(object):
    def process_response(self, request, response, spider):
        # url length
        if len(response.url) > config.retriever_max_url_length:
            logging.debug("%s # Url is too long" % response.url[:int(0.2 * config.retriever_max_url_length)])
            raise IgnoreRequest

        # deny domain an unknown content-type check
        content_type = tool.get_content_type_for_response(response)
        if (content_type is None) or (content_type not in config.retriever_allow_content_type):
            logging.debug("%s # Unknown Content-Type:[%s] " % (response.url, content_type))
            raise IgnoreRequest

        for de in config.retriever_deny_domains:
            if response.url.find(de) != -1:
                logging.debug("%s # Deny Domain" % (response.url,))
                raise IgnoreRequest

        # is this url in URL_LIB
        urldb_item = UrlItem.load(url=response.url)

        if response.status == 404:  # not found
            if urldb_item is not None:  # already in DB
                logging.debug("%s # Already in DB, but 404 now" % request.url)
                db.delete_sem_with_urlid(urldb_item['id'])
                raise IgnoreRequest
            else:  # new 404
                logging.debug("%s # New 404 found" % response.url)
                raise IgnoreRequest

        if urldb_item is not None:
            # already in URL_LIB
            if tool.hash_for_text(response.body) == urldb_item['content_hash']:
                # content has not changed, update last_access_ts
                logging.debug("%s # Already in DB, Content has not change" % response.url)
                db.update_url_lastextractts(urldb_item['id'])
                raise IgnoreRequest
            else:
                logging.debug("%s # Already in DB, Content changed" % response.url)
                # delete corresponding seminar info in SEM_LIB
                db.delete_sem_with_urlid(urldb_item['id'])
        else:
            logging.debug(response.url + " # New url")
            db.new_url_insert(response.url)

        return response
