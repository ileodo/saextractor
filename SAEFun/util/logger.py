__author__ = 'LeoDong'
import logging
import config

FORMAT = '%(asctime)s [%(module)s] %(levelname)s:  %(message)s'
logging.basicConfig(format=FORMAT)
log = logging.getLogger('logger')
log.setLevel(config.logger_level)