import csv
import cPickle as pickle

from sklearn import tree
from sklearn.externals.six import StringIO
import pydot
import dot_parser
import requests

from judge.FeatueExtract import FeatureExtract
from SAECrawlers.items import UrlItem
from util import db, config


def csv_for_db(sql, sqlparam, resultfile, datapath):
    c = db.db_connect().cursor()
    c.execute(sql, sqlparam)
    res = c.fetchall()
    c.close()
    out = open(resultfile, "w")

    line = "%s,%s,%s" % ('id', 'url', 'label')
    # print line
    out.write(line+"\n")
    out.flush()

    for r in res:
        line = "%s,\"%s\",%s" % (r['id'], r['url'], r['rule_id'])
        # print line
        out.write(line+"\n")
        out.flush()
        fout = open(datapath + "/" + str(r['id']), "w")
        fout.write(requests.get(r['url'], verify=False).content)
        fout.close()

    out.close()


def feature_extraction(csvfile, datapath, resultcsv,fe_path=config.path_fe_space):
    fe = FeatureExtract(fe_path)
    fe.print_featuremap()

    # feature extraction
    out = open(resultcsv, "w")
    line = "%s,%s,%s" % ('id', 'label', fe.str_featuremap_line())
    # print line
    out.write(line+"\n")
    out.flush()

    with open(csvfile, 'rb') as csvfile:
        reader = csv.reader(csvfile, delimiter=',')
        header = True
        for row in reader:
            if not header:
                item = UrlItem()
                ct = open(datapath + "/" + row[0], "r").read()
                item['url'] = row[1]
                item['content'] = ct
                item['title'] = item.get_short_title()
                f = fe.extract_item(item)
                line = "%s,%s,%s" % (row[0], row[2], FeatureExtract.str_feature(f))
                print line
                out.write(line+"\n")
                out.flush()
            else:
                header = False
        out.close()


def test_data(dtreefile, testdata, outputpath):
    clf = pickle.loads(open(dtreefile).read())['tree']
    fe = FeatureExtract(config.path_fe_space)
    output = open(outputpath, "w")
    line = "%s,%s,%s,%s,%s" % ("id", "label", "judge", "prob", fe.str_featuremap_line())
    # print line
    output.write(line+"\n")
    output.flush()

    count_false_neg = 0
    count_true_pos= 0
    count_false_pos = 0
    count_true_neg = 0

    with open(testdata, 'rb') as csvfile:
        reader = csv.reader(csvfile, delimiter=',')
        header = True
        for row in reader:
            if not header:
                fv = row[2:]
                judge = clf.predict(fv)[0]
                prob = max(clf.predict_proba(fv)[0])
                line = "%s,%s,%s,%f,%s" % (row[0], row[1], judge, float(prob), ",".join(row[2:]))
                # print line
                output.write(line+"\n")
                output.flush()
                if int(row[1]) in [1, 2]:
                    if int(judge) in [1, 2]:
                        count_true_pos += 1
                    else:
                        count_false_neg += 1
                else:
                    if int(judge) in [1, 2]:
                        count_false_pos += 1
                    else:
                        count_true_neg += 1
            else:
                header = False
    output.close()
    recall = float(count_true_pos) / (count_true_pos + count_false_neg)
    precision = float(count_true_pos) / (count_true_pos + count_false_pos)
    fmeasure = 2*precision*recall / (precision + recall)
    print "Recall: %f" % recall
    print "Precision: %f" % precision
    print "F-Measure: %f" % fmeasure
    return (count_true_pos,count_true_neg,count_false_pos,count_false_neg)



def learn_dtree(featurecsv, dtreefile, param):
    F = []
    L = []
    clf = tree.DecisionTreeClassifier(**param)
    with open(featurecsv, 'rb') as csvfile:
        reader = csv.reader(csvfile, delimiter=',')
        header = True
        for row in reader:
            if not header:
                F.append(row[2:])
                L.append(row[1])
            else:
                header = False

    clf = clf.fit(F, L)

    dot_data = StringIO()
    tree.export_graphviz(clf, out_file=dot_data)
    graph = pydot.graph_from_dot_data(dot_data.getvalue())
    graph.write_pdf(dtreefile + ".pdf")

    out = open(dtreefile, "w")
    out.write(pickle.dumps({"tree": clf, "F": F, "L": L}, -1))
    out.close()


def get_data_set(featurecsv):
    F = []
    L = []
    with open(featurecsv, 'rb') as csvfile:
        reader = csv.reader(csvfile, delimiter=',')
        header = True
        for row in reader:
            if not header:
                F.append([int(x) for x in row[2:]])
                L.append(int(row[1]))
            else:
                header = False
    return F,L