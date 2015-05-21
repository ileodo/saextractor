__author__ = 'LeoDong'
from scrapy import log
from scrapy.utils.httpobj import urlparse_cached
from scrapy.exceptions import IgnoreRequest

import DB_Helper
import Util
import Config


class CustomDownloaderMiddleware(object):
    def process_response(self, request, response, spider):
        # url length
        if len(response.url)>Config.retriever_max_url_length:
            log.msg("Url is too long: %s"%response.url)
            raise IgnoreRequest

        # deny domain an unknown content-type check
        content_type = Util.getContentType(response)
        if (content_type is None) or (content_type not in Config.retriever_allow_content_type):
            log.msg("Unknown Content-Type:[%s] in %s "%(Util.getContentType(response),response.url))
            raise IgnoreRequest

        htname = urlparse_cached(response).hostname
        if htname in Config.retriever_deny_domains:
            log.msg("Deny Domain in: " + htname)
            raise IgnoreRequest

        # is this url in URL_LIB
        urlInDB = DB_Helper.getUrlByUrl(Util.getUrl(response))

        if response.status == 404:  # not found
            if urlInDB is not None: # already in DB
                log.msg("%s already in DB, but 404 now" % request.url,level=log.DEBUG)
                DB_Helper.deleteSemWithUrlID(urlInDB['id'])
                raise IgnoreRequest
            else: # new 404
                log.msg("new 404 found: %s" % response.url,level=log.DEBUG)
                raise IgnoreRequest

        if urlInDB is not None:
            # already in URL_LIB
            if Util.getHashedContent(response) == urlInDB['content_hash']:
                # content has not changed, update last_access_ts
                log.msg(response.url + " # Already in DB, Content has not change", level=log.DEBUG)
                DB_Helper.updateUrlLastAccessTS(urlInDB['id'])
                raise IgnoreRequest
            else:
                log.msg(response.url + " # Already in DB, Content changed", level=log.DEBUG)
                # update URL_LIB with new (title, content_hash, layout_hash)
                # delete corresponding seminar info in SEM_LIB
                DB_Helper.deleteSemWithUrlID(urlInDB['id'])
                DB_Helper.updateUrlExist(urlInDB['id'],
                                         Util.getHtmlTitle(response),
                                         Util.getHashedContent(response),
                                         Util.getHashedLayout(response))
        else:
            log.msg(response.url + " # New url", level=log.DEBUG)
            DB_Helper.createUrlNew(Util.getUrl(response),
                                   Util.getHtmlTitle(response),
                                   Util.getHashedContent(response),
                                   Util.getHashedLayout(response))

        return response
