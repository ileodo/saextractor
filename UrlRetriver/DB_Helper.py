__author__ = 'LeoDong'
import MySQLdb
import MySQLdb.cursors


db_con = None

def dbconnect():
    global db_con
    if db_con is None:
        db_con = MySQLdb.connect(host="localhost",
                                 user="sae",
                                 passwd="sae",
                                 db="sae",
                                 cursorclass=MySQLdb.cursors.DictCursor)
        db_con.autocommit(on=True);
    return db_con

def dbclose():
    global db_con
    db_con.close();

sql_getUrlsAll = """SELECT url FROM url_lib"""
def getUrlsAll():
    c = dbconnect().cursor()
    c.execute(sql_getUrlsAll)
    res = c.fetchall()
    c.close()
    return [x['url'] for x in res ]

sql_getUrlByID = """SELECT * FROM url_lib WHERE id = %s"""
def getUrlByID(id):
    c = dbconnect().cursor()
    c.execute(sql_getUrlByID, (id,))
    res = c.fetchone()
    c.close()
    return res


sql_getUrlByUrl = """SELECT * FROM url_lib WHERE url = %s"""
def getUrlByUrl(url):
    c = dbconnect().cursor()
    c.execute(sql_getUrlByUrl, (url,))
    res = c.fetchone()
    c.close()
    return res

sql_createUrlNew = """INSERT INTO url_lib (url, title, is_target, content_hash, layout_hash, last_access_ts)
      VALUES (%s, %s,0, %s, %s, current_timestamp())"""
def createUrlNew(url, title, content_hash, layout_hash):
    c = dbconnect().cursor()
    c.execute(sql_createUrlNew, (url,title,content_hash,layout_hash))
    lid = c.lastrowid
    c.close()
    return lid


# for an exist url(updated), update its title, is_target, content_hash, layout_hash, last_access_ts
sql_updateUrlExist = """UPDATE url_lib SET title=%s, content_hash=%s, layout_hash =%s, last_access_ts = current_timestamp() WHERE id=%s """
def updateUrlExist(id, title, content_hash, layout_hash):
    c = dbconnect().cursor()
    c.execute(sql_updateUrlExist, (title, content_hash,layout_hash,id))
    c.close()

# for an exist url(no update), update its last_access_ts
sql_updateUrlLastAccessTS = """UPDATE url_lib SET last_access_ts=current_timestamp() WHERE id=%s"""
def updateUrlLastAccessTS(id):
    c = dbconnect().cursor()
    c.execute(sql_updateUrlLastAccessTS, (id,))
    c.close()


# for an exist url(no update), update its isTarget
sql_updateUrlisTargetTrue = """UPDATE url_lib SET is_target=1 WHERE id=%s"""
def updateUrlisTargetTrue(id):
    c = dbconnect().cursor()
    c.execute(sql_updateUrlisTargetTrue, (id,))
    c.close()

# for an exist url(no update), update its isTarget
sql_updateUrlisTargetFalse = """UPDATE url_lib SET is_target=-1 WHERE id=%s"""
def updateUrlisTargetFalse(id):
    c = dbconnect().cursor()
    c.execute(sql_updateUrlisTargetFalse, (id,))
    c.close()


sql_deleteSemWithUrlID = """DELETE FROM sem_info WHERE url_id=%s"""
def deleteSemWithUrlID(id):
    c = dbconnect().cursor()
    c.execute(sql_deleteSemWithUrlID, (id,))
    c.close()


sql_resetdb = """TRUNCATE TABLE url_lib"""
def resetdb():
    c = dbconnect().cursor()
    c.execute(sql_resetdb)
    c.close()
