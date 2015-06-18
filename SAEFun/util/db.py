__author__ = 'LeoDong'
import MySQLdb
import MySQLdb.cursors

import config

db_con = None


def db_connect():
    """
    connect DB
    :return: db connection
    """
    global db_con
    if db_con is None:
        db_con = MySQLdb.connect(host=config.db_host,
                                 user=config.db_user,
                                 passwd=config.db_pass,
                                 db=config.db_name,
                                 cursorclass=MySQLdb.cursors.DictCursor)
        db_con.autocommit(on=True)
    return db_con


def db_close():
    """
    close the db connection
    :return:
    """
    global db_con
    db_con.close()


def get_all_urls():
    """
    get all urls in url_lib
    :return:
    """
    sql = """SELECT url FROM url_lib"""
    c = db_connect().cursor()
    c.execute(sql)
    res = c.fetchall()
    c.close()
    return [x['url'] for x in res]

def get_all_ids_known():
    """
    get all targeted urls in url_lib
    :return:
    """
    sql = """SELECT id FROM url_lib WHERE is_target != %s """
    c = db_connect().cursor()
    c.execute(sql, (config.const_IS_TARGET_UNKNOW,))
    res = c.fetchall()
    c.close()
    return [x['id'] for x in res]

def get_all_ids_istarget():
    """
    get all targeted urls in url_lib
    :return:
    """
    sql = """SELECT id FROM url_lib WHERE is_target IN (%s) """
    c = db_connect().cursor()
    c.execute(sql, "%s,%s"%(config.const_IS_TARGET_UNKNOW,config.const_IS_TARGET_NO))
    res = c.fetchall()
    c.close()
    return [x['id'] for x in res]


def get_url_by_id(id):
    """
    get a url associated with an id
    :param id:
    :return:
    """
    sql = """SELECT * FROM url_lib WHERE id = %s"""
    c = db_connect().cursor()
    c.execute(sql, (id,))
    res = c.fetchone()
    c.close()
    return res


def get_url_by_url(url):
    """
    get a url item associated with an url
    :param url:
    :return:
    """
    sql = """SELECT * FROM url_lib WHERE url = %s"""
    c = db_connect().cursor()
    c.execute(sql, (url,))
    res = c.fetchone()
    c.close()
    return res


def general_update_url(id, is_target, content_hash, layout_hash, rule_id, title):
    sql = """
    UPDATE url_lib SET is_target=%s, content_hash=%s, layout_hash =%s, rule_id =%s, title=%s WHERE id=%s
    """
    c = db_connect().cursor()
    c.execute(sql, (is_target, content_hash, layout_hash, rule_id, title, id,))
    c.close()


def general_insert_url(url, is_target, content_hash, layout_hash, rule_id, title):
    sql = """
    INSERT INTO url_lib (url, is_target, content_hash, layout_hash, rule_id, title)
      VALUES (%s, %s,%s, %s, %s, %s)
    """
    c = db_connect().cursor()
    c.execute(sql, (url, is_target, content_hash, layout_hash, rule_id, title))
    lid = c.lastrowid
    c.close()
    return lid

def new_url_insert(url):
    """
    create a new url item
    :param url:
    :param title:
    :param content_hash:
    :param layout_hash:
    :return:
    """
    return general_insert_url(url, config.const_IS_TARGET_UNKNOW, "", "", config.const_RULE_UNKNOW, "")



def exist_url_content_update(id, title, content_hash, layout_hash):
    """
    update an url item `title` `content_hash` `layout_hash` `last_access_ts`
    :param id:
    :param title:
    :param content_hash:
    :param layout_hash:
    :return:
    """
    sql = """UPDATE url_lib SET title=%s, content_hash=%s, layout_hash =%s WHERE id=%s """
    c = db_connect().cursor()
    c.execute(sql, (title, content_hash, layout_hash, id))
    c.close()


def update_url_lastaccessts(id):
    """
    update an url item "last_access_ts" only
    :param id:
    :return:
    """
    sql = """UPDATE url_lib SET last_access_ts=current_timestamp() WHERE id=%s"""
    c = db_connect().cursor()
    c.execute(sql, (id,))
    c.close()


def update_url_lastextractts(id):
    """
    update an url item "last_access_ts" only
    :param id:
    :return:
    """
    sql = """UPDATE url_lib SET last_extract_ts=current_timestamp() WHERE id=%s"""
    c = db_connect().cursor()
    c.execute(sql, (id,))
    c.close()


def delete_sem_with_urlid(id):
    """
    delete seminar information with a specific url id
    :param id:
    :return:
    """
    sql = """DELETE FROM sem_info WHERE url_id=%s"""
    c = db_connect().cursor()
    c.execute(sql, (id,))
    c.close()


def reset_db():
    """
    reset db:
    clear and reset auto-increment of url_lib
    :return:
    """
    sql = """TRUNCATE TABLE url_lib"""
    c = db_connect().cursor()
    c.execute(sql)
    c.close()
