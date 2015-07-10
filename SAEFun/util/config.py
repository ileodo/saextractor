__author__ = 'LeoDong'

import os
import logging

# db
db_host = "localhost"
db_name = "sae"
db_user = "sae"
db_pass = "sae"

# logger
logger_level = logging.INFO

# const
const_IS_TARGET_SIGNLE = 1
const_IS_TARGET_MULTIPLE = 2
const_IS_TARGET_UNKNOW = 0
const_IS_TARGET_NO = -1

const_RULE_UNKNOW = -1

const_CONFIDENCE_THRESHOLD = 70

# path
path_root = os.path.split(os.path.split(os.path.split(os.path.realpath(__file__))[0])[0])[0]
path_working = path_root + "/working"
path_knowledge = path_root + "/knowledge"

path_fe = path_knowledge + "/fe"
path_fe_space = path_fe + "/featurespace.xml"

path_judge_inbox = path_working + "/inbox_judge"
path_judge_dtree = path_knowledge+"/judge/dtree"
path_judge_list = path_working + "/judge_list"

path_extractor_inbox = path_working + "/inbox_extractor"
path_extract_onto = path_knowledge + "/extract"
path_extract_list = path_working + "/extract_list"

path_data = path_root+"/data"


# socket
socket_host = "localhost"
socket_addr_judge = (socket_host,10001)
socket_addr_extractor = (socket_host,10002)
socket_addr_rulegen = (socket_host,10003)

socket_CMD_judge_new = "0"
socket_CMD_judge_done = "1"
socket_CMD_judge_list = "2"
socket_CMD_judge_refresh = "3"

socket_CMD_extractor_new = "0"
socket_CMD_extractor_list = "1"
socket_CMD_extractor_maps = "2"
socket_CMD_extractor_preview = "3"
socket_CMD_extractor_rejudge_done = "4"
socket_CMD_extractor_test_rule = "5"
socket_CMD_extractor_add_rule = "6"
socket_CMD_extractor_test_extractor = "7"
socket_CMD_extractor_add_extract = "8"

socket_retry_seconds = 10
#
# retriever_start_urls = ["https://www.cs.ox.ac.uk/"]
retriever_start_urls = ["http://www.ox.ac.uk/"]

# retriever_allow_content_type = ["text/html", "text/xml", "text/calendar"]
retriever_allow_content_type = ["text/html"]
# retriever_allow_domains = ["cs.ox.ac.uk"]
retriever_allow_domains = ["ox.ac.uk"]
retriever_deny_domains = ["webauth.ox.ac.uk","weblearn.ox.ac.uk","facebook.com","linkedin.com"]
retriever_deny_extensions = [
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "gif", "ps", "tex", "bib", "zip",
    "tar", "gz", "tgz", "java", "cpp", "c", "scala", "msi", "exe", "sh", "com", "bin", "mp4", "avi"]
retriever_deny_regxs = [
    "(\.php|\.jsp|\.asp|\.do)\?",
    "\?"
    "(edit).*(\\.php|\\.jsp|\\.asp)",
    "roombooking",
    "&day=.*&month=.*&year=.*",
    "/search(/|\?)",
    "/login(/|\?)",
    "/rss(/|\?)",
    "/(N|n)ews(/|\?)",
    "/(P|p)eople(/|\?)",
    "/(P|p)roject(s)?(/|\?)",
    "/(P|p)ublication(s)?(/|\?)",
    "/examregs(/|\?)",
    "/(P|p)rofile(/|\?)",
    "/supervision(/|\?)",
    "/(S|s)upport(/|\?)",
    "/gazette(/|\?)",
    "/(S|s)taff(/|\?)",
    "/finance(/|\?)",
    "/(U|u)ser(/|\?)",
    "/sitemap(/|\?)",
    "/finance(/|\?)",
    "/statutes(/|\?)",
    "/(A|a)bout(/|\?)",
    "/admissions(/|\?)",
    "/tag(/|\?)",
    "/blog(/|\?)",
    "/personnel(/|\?)",
    "/supervision(/|\?)",
    "/supervisor(/|\?)",
    "/safety(/|\?)",
    "/handbook(/|\?)",
    "/fellow(s)?(/|\?)",
    "/archives.",
    "/share.",
    "/podcasts."
    "/learning/",
    "/teaching/",
    "/[a-z]*thesis/",
    "/past(-[a-zA-Z])?/",
]
retriever_max_url_length = 512
retriever_download_time_out = 2
retriever_depth_limit = 8

retriever_absolute_url_replace_pattern = {
    "link": "href",
    "script": "src",
    "img": "src",
}

layout_tag_remove = ["script", "map", "p", "link", "meta", "img", "br", "head", "span", "a", "h1", "h2", "h3", "h4",
                     "h5", "h6", "li"]
layout_tag_clear_attr = ["input"]
layout_attr_remove = ["name", "content", "src", "href", "id", "type", "action", "rel", "placeholder", "style", "for",
                      "data.*?"]
layout_attr_clear = ["onclick", "onmouseover", "alt", "title", "value", "onblur", "autocomplete", "maxlength",
                     "onfocus",
                     "usemap", "media", "itemscope"]

#dtree
dtree_param = {
    "criterion": "entropy", #"gini"
    "splitter": "best", #"best"
    "max_features": None, #None
    "max_depth": None, #None
    "min_samples_split": 20, #2
    "min_samples_leaf": 10, #1
    "min_weight_fraction_leaf": 0., #0.
    "max_leaf_nodes": None, #None
    "class_weight": None, #None
    "random_state": None #None
}