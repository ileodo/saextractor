from util import config

import DTreeLearner

# for x in xrange(1,20):
param = config.dtree_param.copy()
# param['min_samples_leaf']=x
# print "===== x:%d =====" %x
DTreeLearner.learn_dtree(
    config.path_root + "/data/dataset2/train.csv",
    config.path_judge_dtree,
    param
)

DTreeLearner.test_data(
    config.path_judge_dtree,
    config.path_root + "/data/dataset2/test.csv",
    config.path_root + "/data/result.csv",
)
