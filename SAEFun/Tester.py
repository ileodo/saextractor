from util import config

import DTreeLearner

# DTreeLearner.csv_for_db(
#     """SELECT * FROM url_lib WHERE rule_id != %s """,
#     (config.const_IS_TARGET_UNKNOW,),
#     config.path_root+"/data/data.csv",
#     config.path_root+"/data/raw"
#     )

# DTreeLearner.feature_extraction(
#     config.path_root+"/data/data.csv",
#     config.path_root+"/data/raw",
#     config.path_root+"/data/feature.csv"
#     )


for x in xrange(1,20):
    param = config.dtree_param.copy()
    param['min_samples_leaf']=x
    print "===== x:%d =====" %x
    DTreeLearner.learn_dtree(
        config.path_root + "/data/dataset2/train.csv",
        config.path_root + "/data/dtree",
        param
    )

    DTreeLearner.test_data(
        config.path_root + "/data/dtree",
        config.path_root + "/data/dataset2/test.csv",
        config.path_root + "/data/result.csv",
    )
