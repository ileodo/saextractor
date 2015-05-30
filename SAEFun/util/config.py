__author__ = 'LeoDong'

import os
import db

# db
db_host = "localhost"
db_name = "sae"
db_user = "sae"
db_pass = "sae"

# const
const_IS_TARGET_SIGNLE = 1
const_IS_TARGET_MULTIPLE = 2
const_IS_TARGET_UNKNOW = 0
const_IS_TARGET_NO = -1

const_RULE_UNKNOW = -1

# path
path_root = os.path.split(os.path.split(os.path.split(os.path.realpath(__file__))[0])[0])[0]
path_working = path_root + "/working"
path_inbox_judge = path_working + "/inbox_judge"
path_inbox_extractor = path_working + "/inbox_extractor"
path_onto = path_root + "/onto"
path_judge = path_onto + "/judge.xml"

# socket
socket_host = "localhost"
socket_addr_judge = (socket_host,10001)
socket_addr_extractor = (socket_host,10002)
socket_addr_rulegen = (socket_host,10003)

socket_CMD_judge_new = "0"
socket_CMD_judge_done = "1"
socket_CMD_judge_list = "2"

#
retriever_start_urls = ["https://www.cs.ox.ac.uk/"] + db.get_all_urls()
retriever_allow_content_type = ["text/html", "text/xml", "text/calendar"]
retriever_allow_domains = ["cs.ox.ac.uk"]
retriever_deny_domains = ["webauth.ox.ac.uk"]
retriever_deny_extensions = [
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "gif", "ps", "tex", "bib", "zip",
    "tar", "gz", "tgz", "java", "cpp", "c", "scala", "msi", "exe", "sh", "com", "bin"]
retriever_deny_regxs = [
    "(edit).*(\\.php|\\.jsp|\\.asp)",
    "roombooking",
    "&day=.*&month=.*&year=.*",
    "/search/",
    "/login/",
    "/rss/",
    "/news/",
    "/people/",
    "/project(s)?/",
    "/publication(s)?/"
]
retriever_max_url_length = 512
retriever_download_time_out = 1
retriever_depth_limit = 3

layout_tag_remove = ["script", "map", "p", "link", "meta", "img", "br", "head", "span", "a", "h1", "h2", "h3", "h4",
                     "h5", "h6", "li"]
layout_tag_clear_attr = ["input"]
layout_attr_remove = ["name", "content", "src", "href", "id", "type", "action", "rel", "placeholder", "style", "for",
                      "data.*?"]
layout_attr_clear = ["onclick", "onmouseover", "alt", "title", "value", "onblur", "autocomplete", "maxlength",
                     "onfocus",
                     "usemap", "media", "itemscope"]
