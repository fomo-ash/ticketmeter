INSERT INTO stations(code,name,city) VALUES
 ('NDLS','New Delhi','New Delhi'),('CNB','Kanpur Central','Kanpur'),
 ('LKO','Lucknow','Lucknow'),('BCT','Mumbai Central','Mumbai'),
 ('JP','Jaipur Junction','Jaipur')
ON CONFLICT (code) DO NOTHING;
INSERT INTO trains(number,name) VALUES
 ('D1001','Ganga Demo Express'),('D1002','Western Demo Express')
ON CONFLICT (number) DO NOTHING;
INSERT INTO train_stops(train_id,station_id,stop_order,arrival,departure,day_offset)
SELECT t.id,s.id,v.stop_order,v.arrival::time,v.departure::time,v.day_offset
FROM (VALUES
 ('D1001','NDLS',1,NULL,'06:00',0),('D1001','CNB',2,'11:00','11:10',0),('D1001','LKO',3,'13:30',NULL,0),
 ('D1002','NDLS',1,NULL,'08:00',0),('D1002','JP',2,'12:30','12:40',0),('D1002','BCT',3,'22:00',NULL,0)
) AS v(number,code,stop_order,arrival,departure,day_offset)
JOIN trains t ON t.number=v.number JOIN stations s ON s.code=v.code
ON CONFLICT (train_id,stop_order) DO NOTHING;
INSERT INTO train_runs(train_id,service_date,capacity,fare)
SELECT t.id,current_date + d.days,20,CASE WHEN t.number='D1001' THEN 650.00 ELSE 1250.00 END
FROM trains t CROSS JOIN generate_series(1,14) AS d(days)
WHERE t.number IN ('D1001','D1002')
ON CONFLICT (train_id,service_date) DO NOTHING;
