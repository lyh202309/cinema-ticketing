-- ============================================
-- 电影院订票系统 种子数据
-- 前提：先执行 schema.sql
-- 测试用户：13800138000 / 密码 123456
-- 场次时间相对 NOW() 生成，任何时候执行都保证"未来可购"的场次
-- 注意：INTERVAL 只接受单单位，跨单位用连续加减：NOW() + INTERVAL 1 DAY + INTERVAL 5 HOUR
-- ============================================
USE cinema_ticketing;

-- ---- 用户 ----
INSERT INTO tb_user (id, phone, password, nick_name, icon) VALUES
(1, '13800138000', '$2a$10$DxfdHvgxDGDrIFdZQI9qReIITZk52bYg8RCPsES2n/j4lStm0t9qq', '测试用户', '');

-- ---- 影院 ----
INSERT INTO tb_cinema (id, name, region, address, rating, phone) VALUES
(1, '万达影城(天河店)', '天河区', '天河路385号太古汇3楼', 8.5, '020-88000001'),
(2, '华影国际影城(越秀店)', '越秀区', '北京路168号', 8.9, '020-88000002'),
(3, '博纳国际影城(番禺店)', '番禺区', '番禺大道北万象汇5楼', 8.2, '020-88000003');

-- ---- 影厅（row_count × col_count 定义座位布局）----
INSERT INTO tb_hall (id, cinema_id, name, row_count, col_count) VALUES
(1, 1, '1号厅',  10, 18),
(2, 1, '2号厅(IMAX)', 8, 16),
(3, 2, '1号厅',  9, 16),
(4, 2, '2号厅',  7, 14),
(5, 3, '1号厅',  8, 15);

-- ---- 电影 ----
INSERT INTO tb_movie (id, title, genre, duration, director, rating, poster, release_date, description, status) VALUES
(1, '流浪地球3',  '科幻', 142, '郭帆', 8.8, '/img/movie1.webp', '2026-07-20', '太阳危机之后，人类启程寻找新家园。', 1),
(2, '千与千寻',   '动画', 125, '宫崎骏', 9.4, '/img/movie2.webp', '2026-06-01', '少女误入神灵世界的奇幻冒险。', 1),
(3, '沙丘3',      '科幻', 168, '丹尼斯', 8.1, '/img/movie3.jpg', '2026-08-15', '厄拉科斯的沙与血，命运之战。', 1),
(4, '热辣滚烫',   '喜剧', 120, '贾玲', 8.0, '/img/movie4.jpg', '2026-07-10', '人生的拳台，为自己赢一次。', 1);

-- 即将上映（status=0，供首页"即将上映"区块）
INSERT INTO tb_movie (id, title, genre, duration, director, rating, poster, release_date, description, status) VALUES
(5, '封神第二部', '神话', 180, '乌尔善', 0.0, '/img/movie5.jpg', '2026-10-01', '昆仑山下的仙魔之战，姜子牙率众神共赴大劫。', 0),
(6, '碟中谍8：最终清算', '动作', 150, '克里斯托弗·麦奎里', 0.0, '/img/movie6.webp', '2026-12-18', '伊森·亨特的终极任务，生死一线。', 0);

-- 场次（场1/场7 为热门场次，已开售）
INSERT INTO tb_session (id, movie_id, cinema_id, hall_id, start_time, end_time, price, is_hot, sale_start_time, sale_end_time, status) VALUES
    (1, 1, 1, 1, NOW() + INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY + INTERVAL 152 MINUTE, 55.00, 1,
     NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY - INTERVAL 30 MINUTE, 1);

INSERT INTO tb_session (id, movie_id, cinema_id, hall_id, start_time, end_time, price, is_hot, sale_start_time, sale_end_time, status) VALUES
                                                                                                                                           (2, 2, 2, 3, NOW() + INTERVAL 1 DAY + INTERVAL 5 HOUR,  NOW() + INTERVAL 1 DAY + INTERVAL 5 HOUR + INTERVAL 135 MINUTE, 45.00, 0, NULL, NOW() + INTERVAL 1 DAY + INTERVAL 5 HOUR - INTERVAL 30 MINUTE, 1),
                                                                                                                                           (3, 3, 2, 3, NOW() + INTERVAL 1 DAY + INTERVAL 9 HOUR,  NOW() + INTERVAL 1 DAY + INTERVAL 9 HOUR + INTERVAL 178 MINUTE, 60.00, 0, NULL, NOW() + INTERVAL 1 DAY + INTERVAL 9 HOUR - INTERVAL 30 MINUTE, 1),
                                                                                                                                           (4, 4, 1, 2, NOW() + INTERVAL 1 DAY + INTERVAL 12 HOUR, NOW() + INTERVAL 1 DAY + INTERVAL 12 HOUR + INTERVAL 130 MINUTE, 50.00, 0, NULL, NOW() + INTERVAL 1 DAY + INTERVAL 12 HOUR - INTERVAL 30 MINUTE, 1),
                                                                                                                                           (5, 2, 3, 5, NOW() + INTERVAL 1 DAY + INTERVAL 7 HOUR,  NOW() + INTERVAL 1 DAY + INTERVAL 7 HOUR + INTERVAL 135 MINUTE, 42.00, 0, NULL, NOW() + INTERVAL 1 DAY + INTERVAL 7 HOUR - INTERVAL 30 MINUTE, 1),
                                                                                                                                           (6, 1, 2, 4, NOW() + INTERVAL 2 DAY, NOW() + INTERVAL 2 DAY + INTERVAL 152 MINUTE, 52.00, 0, NULL, NOW() + INTERVAL 2 DAY - INTERVAL 30 MINUTE, 1),
                                                                                                                                           (7, 2, 3, 5, NOW() + INTERVAL 2 DAY + INTERVAL 8 HOUR, NOW() + INTERVAL 2 DAY + INTERVAL 8 HOUR + INTERVAL 135 MINUTE, 58.00, 1,
                                                                                                                                            NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 2 DAY + INTERVAL 8 HOUR - INTERVAL 30 MINUTE, 1),
                                                                                                                                           (8, 3, 3, 5, NOW() + INTERVAL 2 DAY + INTERVAL 13 HOUR, NOW() + INTERVAL 2 DAY + INTERVAL 13 HOUR + INTERVAL 178 MINUTE, 58.00, 0, NULL, NOW() + INTERVAL 2 DAY + INTERVAL 13 HOUR - INTERVAL 30 MINUTE, 1);
