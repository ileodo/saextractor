__author__ = 'LeoDong'

import db,os

#db
db_host="localhost"
db_name="sae"
db_user="sae"
db_pass="sae"

#path
path_root=os.path.split(os.path.split(os.path.realpath(__file__))[0])[0]
path_todo=path_root+"/working/todo"

#
retriever_start_urls = ["https://www.cs.ox.ac.uk/"]+db.get_all_urls()
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
    "/search/"
]
retriever_max_url_length = 512
retriever_download_time_out = 1
retriever_depth_limit= 3

layout_tag_remove = ["script","map","p","link","meta","img","br","head","span","a","h1","h2","h3","h4","h5","h6","li"]
layout_tag_clear_attr = ["input"]
layout_attr_remove = ["name", "content", "src", "href", "id", "type", "action", "rel", "placeholder", "style", "for","data.*?"]
layout_attr_clear = ["onclick", "onmouseover", "alt", "title", "value", "onblur", "autocomplete", "maxlength", "onfocus",
                     "usemap", "media","itemscope"]