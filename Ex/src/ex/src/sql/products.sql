-- $Id: products.sql 885 2007-02-27 21:47:18Z labsky $
-- ============================================================
--   Database name:  CENTRUM                                   
--   DBMS name:      ANSI Level 2                              
--   Created on:     9/12/2005  10:20 AM                       
-- ============================================================

drop index REFS_PK on REFS;

drop index REFED_BY_FK on REFS;

drop index REFS_FK on REFS;

drop table REFS cascade;

drop index PRODUCT_PK on PRODUCT;

drop index DESCRIBED_IN_FK on PRODUCT;

drop index DEPICTED_IN_FK on PRODUCT;

drop table PRODUCT cascade;

drop index RESOURCE_PK on RESOURCE;

drop table RESOURCE cascade;

-- ============================================================
--   Table: RESOURCE                                           
-- ============================================================
create table RESOURCE
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
) CHARACTER SET utf-8;

-- ============================================================
--   Index: RESOURCE_PK                                        
-- ============================================================
create unique index RESOURCE_PK on RESOURCE (URL_ABS asc);

-- ============================================================
--   Table: PRODUCT                                            
-- ============================================================
create table PRODUCT
(
    ID              INTEGER               not null unique,
    DESCRIBED_IN    VARCHAR(250)          not null,
    DEPICTED_IN     VARCHAR(250)                  ,
    CATEGORY        VARCHAR(255)          not null,
    MANUFACTURER    VARCHAR(255)                  ,
    PRODUCT_NAME    VARCHAR(255)          not null,
    DESCRIPTION     TEXT                          ,
    PRICE_VAT       FLOAT                         ,
    VAT             FLOAT                         ,
    primary key (ID)
) CHARACTER SET utf-8;

-- ============================================================
--   Index: PRODUCT_PK                                         
-- ============================================================
create unique index PRODUCT_PK on PRODUCT (ID asc);

-- ============================================================
--   Index: DESCRIBED_IN_FK                                    
-- ============================================================
create index DESCRIBED_IN_FK on PRODUCT (DESCRIBED_IN asc);

-- ============================================================
--   Index: DEPICTED_IN_FK                                     
-- ============================================================
create index DEPICTED_IN_FK on PRODUCT (DEPICTED_IN asc);

-- ============================================================
--   Table: REFS                                               
-- ============================================================
create table REFS
(
    URL_SOURCE      VARCHAR(250)          not null,
    URL_TARGET      VARCHAR(250)          not null,
    primary key (URL_SOURCE, URL_TARGET)
) CHARACTER SET utf-8;

-- ============================================================
--   Index: REFS_PK                                            
-- ============================================================
create unique index REFS_PK on REFS (URL_SOURCE asc, URL_TARGET asc);

-- ============================================================
--   Index: REFED_BY_FK                                        
-- ============================================================
create index REFED_BY_FK on REFS (URL_SOURCE asc);

-- ============================================================
--   Index: REFS_FK                                            
-- ============================================================
create index REFS_FK on REFS (URL_TARGET asc);

alter table PRODUCT
    add foreign key  (DESCRIBED_IN)
       references RESOURCE (URL_ABS);

alter table PRODUCT
    add foreign key  (DEPICTED_IN)
       references RESOURCE (URL_ABS);

alter table REFS
    add foreign key  (URL_SOURCE)
       references RESOURCE (URL_ABS);

alter table REFS
    add foreign key  (URL_TARGET)
       references RESOURCE (URL_ABS);

