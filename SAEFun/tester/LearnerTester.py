from util import config
from sklearn import cross_validation
from sklearn import tree
import DTreeLearner
from sklearn.externals.six import StringIO
import pydot
import dot_parser
# DTreeLearner.feature_extraction(
#     config.path_data+'/raw_data/data.csv',
#     config.path_data+'/raw_data/raw',
#     config.path_data+'/results2.csv',
#     config.path_knowledge+'/fe/featurespace2.xml')


X, Y = DTreeLearner.get_data_set(config.path_data + "/results2.csv")
skf = cross_validation.StratifiedKFold(Y, n_folds=10)

thetas = [
    {
        "criterion": "entropy", #"gini"
        "splitter": "best", #"best"
        "max_features": None, #None
        "max_depth": 6, #None
        "min_samples_split": 20, #2
        "min_samples_leaf": 1, #1
        "min_weight_fraction_leaf": 0., #0.
        "max_leaf_nodes": None, #None
        "class_weight": None, #None
        "random_state": None #None
    },
    {
        "criterion": "gini", #"gini"
        "splitter": "best", #"best"
        "max_features": None, #None
        "max_depth": 6, #None
        "min_samples_split": 20, #2
        "min_samples_leaf": 1, #1
        "min_weight_fraction_leaf": 0., #0.
        "max_leaf_nodes": None, #None
        "class_weight": None, #None
        "random_state": None #None
    },
    {
        "criterion": "gini", #"gini"
        "splitter": "best", #"best"
        "max_features": None, #None
        "max_depth": 12, #None
        "min_samples_split": 20, #2
        "min_samples_leaf": 1, #1
        "min_weight_fraction_leaf": 0., #0.
        "max_leaf_nodes": None, #None
        "class_weight": None, #None
        "random_state": None #None
    },
    {
        "criterion": "gini", #"gini"
        "splitter": "best", #"best"
        "max_features": None, #None
        "max_depth": 4, #None
        "min_samples_split": 20, #2
        "min_samples_leaf": 1, #1
        "min_weight_fraction_leaf": 0., #0.
        "max_leaf_nodes": None, #None
        "class_weight": None, #None
        "random_state": None #None
    },
    {
        "criterion": "gini", #"gini"
        "splitter": "best", #"best"
        "max_features": None, #None
        "max_depth": 8, #None
        "min_samples_split": 10, #2
        "min_samples_leaf": 1, #1
        "min_weight_fraction_leaf": 0., #0.
        "max_leaf_nodes": None, #None
        "class_weight": None, #None
        "random_state": None #None
    },

]
case_i = 0
result = [[],[],[],[],[]]
for train_index, test_index in skf:
    X_train, X_test = [X[i] for i in train_index ], [X[i] for i in test_index]
    Y_train, Y_test = [Y[i] for i in train_index ], [Y[i] for i in test_index]
    for t in xrange(0,len(thetas)):
        clf = tree.DecisionTreeClassifier(**thetas[t])
        clf = clf.fit(X_train, Y_train)
        count_false_neg = 0
        count_true_pos = 0
        count_false_pos = 0
        count_true_neg = 0
        for i in xrange(0,len(X_test)):
            fv = X_test[i]
            judge = clf.predict(fv)[0]
            prob = max(clf.predict_proba(fv)[0])
            if int(Y_test[i]) in [1, 2]:
                if int(judge) in [1, 2]:
                    count_true_pos += 1
                else:
                    count_false_neg += 1
            else:
                if int(judge) in [1, 2]:
                    count_false_pos += 1
                else:
                    count_true_neg += 1
        recall = float(count_true_pos) / (count_true_pos + count_false_neg)
        precision = float(count_true_pos) / (count_true_pos + count_false_pos)
        fmeasure = 2*precision*recall / (precision + recall)
        result[t].insert(case_i,{
            "recall":recall,
            "precision" : precision,
            "f-measure" : fmeasure
        })

        if t==len(thetas)-1 and case_i==0:
            dot_data = StringIO()
            tree.export_graphviz(clf, out_file=dot_data)
            graph = pydot.graph_from_dot_data(dot_data.getvalue())
            graph.write_pdf(config.path_data+'/dtree' + ".pdf")

    case_i+=1

for t in xrange(0,len(thetas)):
    print "\nWith theta %d:" %t
    print "-\t"+"\t".join(["folder %d"% c for c in xrange(0,len(result[t]))])+"\taverage"
    precision = [c['precision'] for c in result[t]]
    print "precision\t"+"\t".join([format(c, '.2%') for c in precision]) + "\t"+ format(sum(precision)/len(precision), '.2%')
    recall = [c['recall'] for c in result[t]]
    print "recall\t"+"\t".join([format(c, '.2%') for c in recall]) + "\t"+format(sum(recall)/len(recall), '.2%')
    fmeasure = [c['f-measure'] for c in result[t]]
    print "f-measure\t"+"\t".join([format(c, '.2%') for c in fmeasure]) + "\t"+format(sum(fmeasure)/len(fmeasure), '.2%')



# cross_validation.

# # for x in xrange(1,20):
# param = config.dtree_param.copy()
# # param['min_samples_leaf']=x
# # print "===== x:%d =====" %x
# DTreeLearner.learn_dtree(
#     config.path_root + "/data/dataset2/train.csv",
#     config.path_judge_dtree,
#     param
# )
#
# DTreeLearner.test_data(
#     config.path_judge_dtree,
#     config.path_root + "/data/dataset2/test.csv",
#     config.path_root + "/data/result.csv",
# )
