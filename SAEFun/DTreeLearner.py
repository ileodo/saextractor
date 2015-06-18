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
from util import db,config

fe = FeatureExtract(config.path_featurespace)
fe.print_featuremap()

#feature extraction
out = open(config.path_working+"/featureext.csv","w")

sql = """SELECT id FROM url_lib WHERE rule_id != %s """
c = db.db_connect().cursor()
c.execute(sql, (config.const_IS_TARGET_UNKNOW,))
res = c.fetchall()
c.close()
list = [x['id'] for x in res]

print "%s,%s,%s,%s\n" %('id','url','label',fe.str_featuremap_line())
out.write("%s,%s,%s,%s\n" %('id','url','label',fe.str_featuremap_line()))
out.flush()
for x in list:
    item = UrlretriverItem.s_load_id(x)
    content = requests.get(item['url'],verify=False).content
    item['raw_content'] = content
    item['content'] = content
    item['title'] = item.title_of_tree()
    item['content'] = str(item.get_soup())

    f = fe.extract_item(item)
    print "%s,%s,%s,%s" %(item['id'],item['url'],item['rule_id'], FeatureExtract.str_feature(f))
    out.write("%s,%s,%s,%s\n" %(item['id'],item['url'],item['rule_id'], FeatureExtract.str_feature(f)))
    out.flush()
out.close()

from sklearn import tree
import csv
from sklearn.externals.six import StringIO
import pydot

X=[]
Y=[]
header = True
with open(config.path_working+"/featureext.csv",'rb') as csvfile:
    reader = csv.reader(csvfile,delimiter=',')
    for row in reader:
        if not header:
            X.append(row[3:])
            Y.append(row[2])
        else:
            header = False

    clf = tree.DecisionTreeClassifier()
    clf = clf.fit(X, Y)

    dot_data = StringIO()
    tree.export_graphviz(clf, out_file=dot_data)
    graph = pydot.graph_from_dot_data(dot_data.getvalue())
    graph.write_pdf(config.path_working+"/tree.pdf")

    out = open(config.path_working+"/dtree_ox.ac.uk","w")
    out.write(pickle.dumps({"tree":clf,"X":X,"Y":Y}, -1))
    out.close()
