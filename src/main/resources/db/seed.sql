INSERT INTO stations(code,name,city) VALUES
 ('NDLS','New Delhi','New Delhi'),('CNB','Kanpur Central','Kanpur'),
 ('LKO','Lucknow','Lucknow'),('BCT','Mumbai Central','Mumbai'),
 ('JP','Jaipur Junction','Jaipur'),('BBS','Bhubaneswar','Bhubaneswar'),
 ('PRYJ','Prayagraj Junction','Prayagraj'),('HWH','Howrah Junction','Kolkata'),
 ('CTC','Cuttack','Cuttack')
ON CONFLICT (code) DO NOTHING;
INSERT INTO trains(number,name) VALUES
 ('D1001','Ganga Demo Express'),('D1002','Western Demo Express'),
 ('D1003','Awadh Morning Express'),('D1004','Desert Coast Express'),
 ('D1005','Kalinga Demo Express'),('D1006','Utkal Night Express'),
 ('D1007','Coastal Link Express'),('D1008','Eastern Return Express')
ON CONFLICT (number) DO NOTHING;
INSERT INTO train_stops(train_id,station_id,stop_order,arrival,departure,day_offset)
SELECT t.id,s.id,v.stop_order,v.arrival::time,v.departure::time,v.day_offset
FROM (VALUES
 ('D1001','NDLS',1,NULL,'06:00',0),('D1001','CNB',2,'11:00','11:10',0),('D1001','LKO',3,'13:30',NULL,0),
 ('D1002','NDLS',1,NULL,'08:00',0),('D1002','JP',2,'12:30','12:40',0),('D1002','BCT',3,'22:00',NULL,0),
 ('D1003','NDLS',1,NULL,'07:20',0),('D1003','CNB',2,'12:10','12:20',0),('D1003','LKO',3,'14:45',NULL,0),
 ('D1004','NDLS',1,NULL,'15:30',0),('D1004','JP',2,'20:05','20:15',0),('D1004','BCT',3,'07:40',NULL,1),
 ('D1005','NDLS',1,NULL,'09:10',0),('D1005','PRYJ',2,'17:25','17:40',0),('D1005','BBS',3,'08:15',NULL,1),
 ('D1006','NDLS',1,NULL,'18:15',0),('D1006','CNB',2,'23:30','23:45',0),('D1006','BBS',3,'16:20',NULL,1),
 ('D1007','BBS',1,NULL,'06:40',0),('D1007','CTC',2,'07:10','07:15',0),('D1007','HWH',3,'13:10',NULL,0),
 ('D1008','BBS',1,NULL,'16:10',0),('D1008','PRYJ',2,'07:00','07:15',1),('D1008','NDLS',3,'15:50',NULL,1)
) AS v(number,code,stop_order,arrival,departure,day_offset)
JOIN trains t ON t.number=v.number JOIN stations s ON s.code=v.code
ON CONFLICT (train_id,stop_order) DO NOTHING;
INSERT INTO train_runs(train_id,service_date,capacity,fare)
SELECT t.id,current_date + d.days,20,CASE t.number
 WHEN 'D1001' THEN 650.00 WHEN 'D1002' THEN 1250.00
 WHEN 'D1003' THEN 720.00 WHEN 'D1004' THEN 1380.00
 WHEN 'D1005' THEN 1540.00 WHEN 'D1006' THEN 1680.00
 WHEN 'D1007' THEN 540.00 ELSE 1490.00 END
FROM trains t CROSS JOIN generate_series(1,14) AS d(days)
WHERE t.number IN ('D1001','D1002','D1003','D1004','D1005','D1006','D1007','D1008')
ON CONFLICT (train_id,service_date) DO NOTHING;
