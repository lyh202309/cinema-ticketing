-- ============================================
-- 电影院订票系统 建表脚本
-- 数据库：cinema_ticketing
-- 对应 docs/设计文档.md "八、数据库表结构设计"
-- ============================================
DROP DATABASE IF EXISTS cinema_ticketing;
CREATE DATABASE cinema_ticketing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cinema_ticketing;

-- 用户
CREATE TABLE tb_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(11)  NOT NULL COMMENT '手机号',
    password    VARCHAR(100) DEFAULT NULL COMMENT '密码，可空(验证码登录用户)',
    nick_name   VARCHAR(32)  DEFAULT '' COMMENT '昵称',
    icon        VARCHAR(255) DEFAULT '' COMMENT '头像',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB COMMENT '用户';

-- 影院
CREATE TABLE tb_cinema (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50)   NOT NULL COMMENT '影院名称',
    region      VARCHAR(20)   DEFAULT NULL COMMENT '所在区',
    address     VARCHAR(100)  DEFAULT NULL COMMENT '地址',
    rating      DECIMAL(2, 1) DEFAULT 0 COMMENT '评分 0.0~9.9',
    phone       VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '影院';

-- 影厅（定义座位布局 rows x cols，座位不建独立表）
CREATE TABLE tb_hall (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    cinema_id   BIGINT      NOT NULL COMMENT '所属影院',
    name        VARCHAR(20) NOT NULL COMMENT '厅名，如 1号厅',
    row_count   INT         NOT NULL COMMENT '行数',
    col_count   INT         NOT NULL COMMENT '列数',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_cinema (cinema_id)
) ENGINE = InnoDB COMMENT '影厅';

-- 电影
CREATE TABLE tb_movie (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(50)   NOT NULL COMMENT '片名',
    genre        VARCHAR(20)   DEFAULT NULL COMMENT '类型',
    duration     INT           DEFAULT NULL COMMENT '时长(分钟)',
    director     VARCHAR(30)   DEFAULT NULL COMMENT '导演',
    rating       DECIMAL(2, 1) DEFAULT 0 COMMENT '评分',
    poster       VARCHAR(255)  DEFAULT NULL COMMENT '海报',
    release_date DATE          DEFAULT NULL COMMENT '上映日期',
    description  TEXT COMMENT '简介',
    status       TINYINT       DEFAULT 1 COMMENT '0未上映/1上映中/2下映',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '电影';

-- 场次（购票核心对象）
CREATE TABLE tb_session (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    movie_id        BIGINT         NOT NULL COMMENT '电影',
    cinema_id       BIGINT         NOT NULL COMMENT '影院(冗余,按影院查)',
    hall_id         BIGINT         NOT NULL COMMENT '影厅(决定座位布局)',
    start_time      DATETIME       NOT NULL COMMENT '开场时间',
    end_time        DATETIME       NOT NULL COMMENT '散场时间',
    price           DECIMAL(10, 2) NOT NULL COMMENT '统一票价',
    is_hot          TINYINT        DEFAULT 0 COMMENT '热门场次(手动标记),1走抢资格',
    sale_start_time DATETIME       DEFAULT NULL COMMENT '开售时间(热门=抢资格开启;普通可空=发布即可买)',
    sale_end_time   DATETIME       DEFAULT NULL COMMENT '截止售票(过它不再产生新订单)',
    status          TINYINT        DEFAULT 1 COMMENT '0未开售/1售票中/2已结束',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_movie (movie_id),
    KEY idx_cinema (cinema_id),
    KEY idx_start (start_time)
) ENGINE = InnoDB COMMENT '场次';

-- 订单（DB 真源）
CREATE TABLE tb_order (
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)    NOT NULL COMMENT '订单号',
    user_id     BIGINT         NOT NULL COMMENT '下单用户',
    session_id  BIGINT         NOT NULL COMMENT '场次',
    total_price DECIMAL(10, 2) NOT NULL COMMENT '总价',
    status      TINYINT        NOT NULL DEFAULT 0 COMMENT '0待支付/1已支付/2已取消/3已退款',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(5min计时起点)',
    pay_time    DATETIME       DEFAULT NULL COMMENT '支付时间',
    cancel_time DATETIME       DEFAULT NULL COMMENT '取消时间',
    refund_time DATETIME       DEFAULT NULL COMMENT '退款时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id),
    KEY idx_session (session_id)
) ENGINE = InnoDB COMMENT '订单';

-- 订单座位（Redis 座位状态的对账依据）
CREATE TABLE tb_order_seat (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT   NOT NULL COMMENT '订单',
    session_id  BIGINT   NOT NULL COMMENT '场次(冗余,按场次重建Redis用)',
    seat_row    INT      NOT NULL COMMENT '座位行',
    seat_col    INT      NOT NULL COMMENT '座位列',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order (order_id),
    KEY idx_session (session_id)
) ENGINE = InnoDB COMMENT '订单座位';

-- AI 会话
CREATE TABLE tb_chat_conversation (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL COMMENT '归属用户',
    title       VARCHAR(50) DEFAULT '' COMMENT '首条消息摘要',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT 'AI会话';

-- AI 消息（永久存档）
CREATE TABLE tb_chat_message (
    id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id BIGINT   NOT NULL COMMENT '所属会话',
    role            TINYINT  NOT NULL COMMENT '0用户/1助手',
    content         TEXT COMMENT '消息内容',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id)
) ENGINE = InnoDB COMMENT 'AI消息';
