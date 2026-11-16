package in.railwaydemo;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

public final class BookingService {
    public String book(long userId,long runId,long originId,long destinationId,String passenger,int age) throws SQLException {
        if(passenger==null || passenger.isBlank() || passenger.length()>100 || age<1 || age>120) throw new IllegalArgumentException("Enter a valid passenger name and age.");
        try(Connection c=Db.open()) {
            c.setAutoCommit(false);
            try {
                int capacity;
                java.math.BigDecimal fare;
                String lockSql="SELECT r.capacity,r.fare FROM train_runs r JOIN trains t ON t.id=r.train_id AND t.active WHERE r.id=? AND r.active AND r.service_date>=current_date FOR UPDATE OF r";
                try(PreparedStatement p=c.prepareStatement(lockSql)) {
                    p.setLong(1,runId);
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next())throw new IllegalArgumentException("This train run is no longer available.");
                        capacity=r.getInt(1);fare=r.getBigDecimal(2);
                    }
                }
                String routeSql="SELECT 1 FROM train_runs r JOIN train_stops a ON a.train_id=r.train_id AND a.station_id=? JOIN train_stops b ON b.train_id=r.train_id AND b.station_id=? AND b.stop_order>a.stop_order WHERE r.id=?";
                try(PreparedStatement p=c.prepareStatement(routeSql)) {
                    p.setLong(1,originId);p.setLong(2,destinationId);p.setLong(3,runId);
                    try(ResultSet r=p.executeQuery()) { if(!r.next())throw new IllegalArgumentException("Invalid route for this train."); }
                }
                int seat;
                String seatSql="SELECT s FROM generate_series(1,?) s WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.run_id=? AND b.seat_number=s AND b.status='CONFIRMED') ORDER BY s LIMIT 1";
                try(PreparedStatement p=c.prepareStatement(seatSql)) {
                    p.setInt(1,capacity);p.setLong(2,runId);
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next())throw new IllegalArgumentException("This train is sold out.");
                        seat=r.getInt(1);
                    }
                }
                String pnr="DEMO-"+UUID.randomUUID().toString().substring(0,12).toUpperCase();
                try(PreparedStatement p=c.prepareStatement("INSERT INTO bookings(pnr,user_id,run_id,origin_id,destination_id,passenger_name,passenger_age,seat_number,fare,status) VALUES (?,?,?,?,?,?,?,?,?,'CONFIRMED')")) {
                    p.setString(1,pnr);p.setLong(2,userId);p.setLong(3,runId);p.setLong(4,originId);p.setLong(5,destinationId);p.setString(6,passenger.trim());p.setInt(7,age);p.setInt(8,seat);p.setBigDecimal(9,fare);p.executeUpdate();
                }
                c.commit();return pnr;
            } catch(SQLException | RuntimeException e) { c.rollback();throw e; }
            finally { c.setAutoCommit(true); }
        }
    }
}
