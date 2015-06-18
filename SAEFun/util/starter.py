__author__ = 'LeoDong'

from scrapy import log
import db
import tool
import config


def init_working_path():
    log.msg("Start cleaning working path")
    # tool.initial_folder(config.path_working)
    tool.initial_folder(config.path_inbox_extractor)
    tool.initial_folder(config.path_inbox_judge)
    log.msg("Finish cleaning working path")
    pass

def init_database():
    log.msg("Start cleaning DB")
    db.reset_db()
    log.msg("Finish cleaning DB")
    pass
