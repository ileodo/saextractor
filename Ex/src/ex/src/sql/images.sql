-- $Id: images.sql 885 2007-02-27 21:47:18Z labsky $
-- ============================================================
--   Database name:  CENTRUM                                   
--   DBMS name:      ANSI Level 2                              
--   Created on:     9/12/2005  10:20 AM                       
-- ============================================================

drop index IMG_REFS_PK on IMG_REFS;

drop index IMG_REFED_BY_FK on IMG_REFS;

drop index IMG_REFS_FK on IMG_REFS;

drop table IMG_REFS cascade;

drop index IMG_RESOURCE_PK on IMG_RESOURCE;

drop table IMG_RESOURCE cascade;

-- ============================================================
--   Table: IMG_RESOURCE                                           
-- ============================================================
create table IMG_RESOURCE
(
    URL_ABS         VARCHAR(250)          not null,
    URL_REF         VARCHAR(250)                  ,
    URL_CACHED      VARCHAR(250)                  ,
    CONTENT_TYPE    VARCHAR(100)                  ,
    ENCODING        VARCHAR(100)                  ,
    CONTENT_LENGTH  INTEGER                       ,
    LAST_MODIFIED   INTEGER                       ,
    URL_SERVER      VARCHAR(250)                  ,
    primary key (URL_ABS)
);

-- ============================================================
--   Index: IMG_RESOURCE_PK                                        
-- ============================================================
create unique index IMG_RESOURCE_PK on IMG_RESOURCE (URL_ABS asc);

-- ============================================================
--   Table: IMG_REFS                                               
-- ============================================================
create table IMG_REFS
(
    URL_SOURCE      VARCHAR(250)          not null,
    URL_TARGET      VARCHAR(250)          not null,
    primary key (URL_SOURCE, URL_TARGET)
);

-- ============================================================
--   Index: IMG_REFS_PK                                            
-- ============================================================
create unique index IMG_REFS_PK on IMG_REFS (URL_SOURCE asc, URL_TARGET asc);

-- ============================================================
--   Index: IMG_REFED_BY_FK                                        
-- ============================================================
create index IMG_REFED_BY_FK on IMG_REFS (URL_SOURCE asc);

-- ============================================================
--   Index: IMG_REFS_FK                                            
-- ============================================================
create index IMG_REFS_FK on IMG_REFS (URL_TARGET asc);

alter table IMG_REFS
    add foreign key  (URL_SOURCE)
       references IMG_RESOURCE (URL_ABS);

alter table IMG_REFS
    add foreign key  (URL_TARGET)
       references IMG_RESOURCE (URL_ABS);

