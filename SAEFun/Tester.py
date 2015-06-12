import re

from bs4 import BeautifulSoup
import hashlib
import copy
from SAECrawlers.items import UrlretriverItem
import requests
from bs4 import BeautifulSoup
from lxml import etree
from lxml.html.clean import Cleaner
from io import StringIO
from lxml.html.soupparser import fromstring
import lxml

import cPickle as pickle
from SAEJudge.FeatueExtract import FeatureExtract
from SAECrawlers.items import UrlretriverItem
from util import db

fe = FeatureExtract("featurespace.xml")
# fe.print_featuremap()

import re
from util import config

# file_content = open(config.path_onto + "/location.wd").read()
# lines = file_content.strip().split("\n")
# for line in lines:
#     p = re.compile("starts(\s+)time(\s*):|from(\s*):?", re.I)
#     print line
#     m = p.findall("starts  time:")
#     print "! ", len(m), m


# list = db.get_all_urls()
# print "%s,%s,%s" %('id','url',fe.str_featuremap_line())
# out = open(config.path_working+"/fe.csv","a")
#
#
# for x in list:
#     item = UrlretriverItem.s_load_url(x)
#     content = requests.get(item['url']).content
#     item['raw_content'] = content
#     item['content'] = content
#     item['title'] = item.title_of_tree()
#     item['content'] = str(item.get_soup())
#     f = fe.extract_item(item)
#     print "%s,%s,[%s],%s" %(item['id'],item['url'],item['is_target'], FeatureExtract.str_feature(f))
#     out.write("%s,%s,[%s],%s\n" %(item['id'],item['url'],item['is_target'], FeatureExtract.str_feature(f)))
#     out.flush()

from sklearn import tree
import csv
X=[]
Y=[]
header = True
with open('/Users/LeoDong/Dropbox/Academic/OX-TT/fe.csv','rb') as csvfile:
    reader = csv.reader(csvfile,delimiter='\t')
    for row in reader:
        if not header:
            X.append(row[3:])
            Y.append(row[2])
        else:
            header = False

    clf = tree.DecisionTreeClassifier()
    clf = clf.fit(X, Y)
    Yp = clf.predict_proba(X)
    print Y
    print Yp

    # from sklearn.externals.six import StringIO
    # with open(config.path_working+"/tree.dot", 'w') as f:
    #     f = tree.export_graphviz(clf, out_file=f)
    # import os
    # os.unlink(config.path_working+'/tree.dot')

    from sklearn.externals.six import StringIO
    import pydot
    dot_data = StringIO()
    tree.export_graphviz(clf, out_file=dot_data)
    graph = pydot.graph_from_dot_data(dot_data.getvalue())
    graph.write_pdf(config.path_working+"/tree.pdf")

