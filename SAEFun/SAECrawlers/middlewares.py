__author__ = 'LeoDong'
from scrapy import log
from scrapy.utils.httpobj import urlparse_cached
from scrapy.exceptions import IgnoreRequest

from util import config, tool, db


class CustomDownloaderMiddleware(object):
    def process_response(self, request, response, spider):
        # url length
        if len(response.url) > config.retriever_max_url_length:
            log.msg("%s # Url is too long" % response.url[:int(0.2 * config.retriever_max_url_length)])
            raise IgnoreRequest

        # deny domain an unknown content-type check
        content_type = tool.get_content_type_for_response(response)
        if (content_type is None) or (content_type not in config.retriever_allow_content_type):
            log.msg("%s # Unknown Content-Type:[%s] " % (response.url, content_type))
            raise IgnoreRequest

        for de in config.retriever_deny_domains:
            if response.url.find(de) != -1:
                log.msg("%s # Deny Domain" % (response.url,))
                raise IgnoreRequest

        # is this url in URL_LIB
        urldb_item = db.get_url_by_url(response.url)

        if response.status == 404:  # not found
            if urldb_item is not None:  # already in DB
                log.msg("%s # Already in DB, but 404 now" % request.url, level=log.DEBUG)
                db.delete_sem_with_urlid(urldb_item['id'])
                raise IgnoreRequest
            else:  # new 404
                log.msg("%s # New 404 found" % response.url, level=log.DEBUG)
                raise IgnoreRequest

        if urldb_item is not None:
            # already in URL_LIB
            if tool.hash_for_text(response.body) == urldb_item['content_hash']:
                # content has not changed, update last_access_ts
                log.msg("%s # Already in DB, Content has not change" % response.url, level=log.DEBUG)
                db.update_url_lastextractts(urldb_item['id'])
                raise IgnoreRequest
            else:
                log.msg("%s # Already in DB, Content changed" % response.url, level=log.DEBUG)
                # delete corresponding seminar info in SEM_LIB
                db.delete_sem_with_urlid(urldb_item['id'])
        else:
            log.msg(response.url + " # New url", level=log.DEBUG)
            db.new_url_insert(response.url)

        return response
