package in.railwaydemo;

import in.railwaydemo.Models.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Repository {
    public List<Station> stations() throws SQLException {
        try (Connection c=Db.open(); PreparedStatement p=c.prepareStatement("SELECT id,code,name FROM stations ORDER BY name"); ResultSet r=p.executeQuery()) {
            List<Station> out=new ArrayList<>();
            while(r.next()) out.add(new Station(r.getLong(1),r.getString(2),r.getString(3)));
            return out;
        }
    }

    public Optional<User> login(String email, String password) throws SQLException {
        try (Connection c=Db.open(); PreparedStatement p=c.prepareStatement("SELECT id,name,email,role,password_hash FROM users WHERE email=?")) {
            p.setString(1,email.toLowerCase(java.util.Locale.ROOT));
            try(ResultSet r=p.executeQuery()) {
                if(r.next() && Passwords.verify(password,r.getString(5))) return Optional.of(new User(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)));
                return Optional.empty();
            }
        }
    }

    public User register(String name,String email,String password) throws SQLException {
        try(Connection c=Db.open(); PreparedStatement p=c.prepareStatement("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,'PASSENGER') RETURNING id")) {
            p.setString(1,name); p.setString(2,email.toLowerCase(java.util.Locale.ROOT)); p.setString(3,Passwords.hash(password));
            try(ResultSet r=p.executeQuery()) { r.next(); return new User(r.getLong(1),name,email.toLowerCase(java.util.Locale.ROOT),"PASSENGER"); }
        }
    }

    public void createAdminIfConfigured() throws SQLException {
        String email=System.getenv("RAILWAY_ADMIN_EMAIL"), password=System.getenv("RAILWAY_ADMIN_PASSWORD");
        if((email==null || email.isBlank()) && (password==null || password.isBlank())) return;
        if(email==null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || password==null || password.length()<12 || password.length()>128)
            throw new IllegalArgumentException("Set a valid RAILWAY_ADMIN_EMAIL and RAILWAY_ADMIN_PASSWORD of 12–128 characters, or omit both.");
        try(Connection c=Db.open(); PreparedStatement p=c.prepareStatement("INSERT INTO users(name,email,password_hash,role) VALUES ('Demo Admin',?,?,'ADMIN') ON CONFLICT (email) DO NOTHING")) {
            p.setString(1,email.trim().toLowerCase(java.util.Locale.ROOT)); p.setString(2,Passwords.hash(password)); p.executeUpdate();
        }
    }

    public List<Run> search(long origin,long destination,LocalDate date) throws SQLException {
        String sql="""
            SELECT r.id,t.id,t.number,t.name,r.service_date,r.capacity,
              r.capacity-(SELECT count(*) FROM bookings b WHERE b.run_id=r.id AND b.status='CONFIRMED') AS available,
              r.fare,so.name,sd.name,to_char(os.departure,'HH24:MI'),to_char(ds.arrival,'HH24:MI')
            FROM train_runs r JOIN trains t ON t.id=r.train_id AND t.active
            JOIN train_stops os ON os.train_id=t.id AND os.station_id=?
            JOIN train_stops ds ON ds.train_id=t.id AND ds.station_id=? AND ds.stop_order>os.stop_order
            JOIN stations so ON so.id=os.station_id JOIN stations sd ON sd.id=ds.station_id
            WHERE r.service_date=? AND r.active AND r.service_date+os.day_offset+os.departure>localtimestamp ORDER BY os.departure,t.number
            """;
        try(Connection c=Db.open(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,origin);p.setLong(2,destination);p.setObject(3,date);
            try(ResultSet r=p.executeQuery()) { List<Run> out=new ArrayList<>(); while(r.next()) out.add(run(r)); return out; }
        }
    }

    public Optional<Run> run(long runId,long origin,long destination) throws SQLException {
        String sql="""
            SELECT r.id,t.id,t.number,t.name,r.service_date,r.capacity,
              r.capacity-(SELECT count(*) FROM bookings b WHERE b.run_id=r.id AND b.status='CONFIRMED') AS available,
              r.fare,so.name,sd.name,to_char(os.departure,'HH24:MI'),to_char(ds.arrival,'HH24:MI')
            FROM train_runs r JOIN trains t ON t.id=r.train_id AND t.active
            JOIN train_stops os ON os.train_id=t.id AND os.station_id=?
            JOIN train_stops ds ON ds.train_id=t.id AND ds.station_id=? AND ds.stop_order>os.stop_order
            JOIN stations so ON so.id=os.station_id JOIN stations sd ON sd.id=ds.station_id
            WHERE r.id=? AND r.service_date>=current_date AND r.active AND r.service_date+os.day_offset+os.departure>localtimestamp
            """;
        try(Connection c=Db.open(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,origin);p.setLong(2,destination);p.setLong(3,runId);
            try(ResultSet r=p.executeQuery()) { return r.next()?Optional.of(run(r)):Optional.empty(); }
        }
    }

    private Run run(ResultSet r) throws SQLException {
        return new Run(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getInt(6),r.getInt(7),r.getBigDecimal(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12));
    }

    public List<Booking> bookings(long userId,boolean admin) throws SQLException {
        String sql="""
            SELECT b.id,b.pnr,t.number,t.name,r.service_date,so.name,sd.name,b.passenger_name,
              b.passenger_age,b.seat_number,b.fare,b.status
            FROM bookings b JOIN train_runs r ON r.id=b.run_id JOIN trains t ON t.id=r.train_id
              JOIN stations so ON so.id=b.origin_id JOIN stations sd ON sd.id=b.destination_id
            """+(admin?"":" WHERE b.user_id=?")+" ORDER BY b.booked_at DESC LIMIT 200";
        try(Connection c=Db.open(); PreparedStatement p=c.prepareStatement(sql)) {
            if(!admin)p.setLong(1,userId);
            try(ResultSet r=p.executeQuery()) { List<Booking> out=new ArrayList<>(); while(r.next()) out.add(new Booking(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getString(6),r.getString(7),r.getString(8),r.getInt(9),r.getInt(10),r.getBigDecimal(11),r.getString(12))); return out; }
        }
    }

    public boolean cancel(long bookingId,long userId) throws SQLException {
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement("UPDATE bookings b SET status='CANCELLED',cancelled_at=now() FROM train_runs r,train_stops s WHERE b.run_id=r.id AND s.train_id=r.train_id AND s.station_id=b.origin_id AND r.service_date+s.day_offset+s.departure>localtimestamp AND b.id=? AND b.user_id=? AND b.status='CONFIRMED' AND r.service_date>=current_date")) {
            p.setLong(1,bookingId);p.setLong(2,userId);return p.executeUpdate()==1;
        }
    }

    public List<String[]> trains() throws SQLException {
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement("SELECT number,name FROM trains ORDER BY number");ResultSet r=p.executeQuery()) {
            List<String[]> out=new ArrayList<>();while(r.next())out.add(new String[]{r.getString(1),r.getString(2)});return out;
        }
    }

    public void addRun(String number,LocalDate date,int capacity,java.math.BigDecimal fare) throws SQLException {
        if(date==null || !date.isAfter(LocalDate.now()) || capacity<1 || capacity>1000 || fare==null || fare.signum()<0 || fare.scale()>2 || fare.compareTo(new java.math.BigDecimal("99999999.99"))>0)
            throw new IllegalArgumentException("Enter a future date, capacity 1–1000, and fare with at most two decimal places (maximum 99999999.99).");
        try(Connection c=Db.open()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO train_runs(train_id,service_date,capacity,fare) SELECT id,?,?,? FROM trains WHERE number=? AND active ON CONFLICT (train_id,service_date) DO NOTHING")) {
                    p.setObject(1,date);p.setInt(2,capacity);p.setBigDecimal(3,fare);p.setString(4,number);p.executeUpdate();
                }
                long runId;
                // Serialize capacity edits with seat assignment, then read bookings in a fresh statement.
                try(PreparedStatement p=c.prepareStatement("SELECT r.id FROM train_runs r JOIN trains t ON t.id=r.train_id WHERE t.number=? AND t.active AND r.service_date=? FOR UPDATE OF r")) {
                    p.setString(1,number);p.setObject(2,date);
                    try(ResultSet r=p.executeQuery()) { if(!r.next())throw new IllegalArgumentException("Train not found.");runId=r.getLong(1); }
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE train_runs SET capacity=?,fare=? WHERE id=? AND ? >= COALESCE((SELECT max(seat_number) FROM bookings WHERE run_id=? AND status='CONFIRMED'),0)")) {
                    p.setInt(1,capacity);p.setBigDecimal(2,fare);p.setLong(3,runId);p.setInt(4,capacity);p.setLong(5,runId);
                    if(p.executeUpdate()!=1)throw new IllegalArgumentException("Capacity is below an occupied seat number.");
                }
                c.commit();
            } catch(SQLException | RuntimeException e) { c.rollback();throw e; }
        }
    }

    public void addStation(String code,String name,String city) throws SQLException {
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement("INSERT INTO stations(code,name,city) VALUES (?,?,?)")) {
            p.setString(1,code.toUpperCase(java.util.Locale.ROOT));p.setString(2,name);p.setString(3,city);p.executeUpdate();
        }
    }

    public void addTrain(String number,String name) throws SQLException {
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement("INSERT INTO trains(number,name) VALUES (?,?)")) {
            p.setString(1,number.toUpperCase(java.util.Locale.ROOT));p.setString(2,name);p.executeUpdate();
        }
    }

    public void addStop(String number,long station,int order,String arrival,String departure,int dayOffset) throws SQLException {
        String sql="""
            INSERT INTO train_stops(train_id,station_id,stop_order,arrival,departure,day_offset)
            SELECT id,?,?,?,?,? FROM trains WHERE number=?
            ON CONFLICT (train_id,stop_order) DO UPDATE SET station_id=EXCLUDED.station_id,
              arrival=EXCLUDED.arrival,departure=EXCLUDED.departure,day_offset=EXCLUDED.day_offset
            """;
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,station);p.setInt(2,order);p.setObject(3,arrival==null?null:java.time.LocalTime.parse(arrival));
            p.setObject(4,departure==null?null:java.time.LocalTime.parse(departure));p.setInt(5,dayOffset);p.setString(6,number);
            if(p.executeUpdate()!=1)throw new SQLException("Train not found");
        }
    }

    public List<Complaint> complaints(long userId,boolean admin) throws SQLException {
        String sql="""
            SELECT c.id,c.category,c.description,c.status,c.admin_response,b.pnr,
                   to_char(c.created_at,'YYYY-MM-DD HH24:MI')
            FROM complaints c LEFT JOIN bookings b ON b.id=c.booking_id
            """+(admin?"":" WHERE c.user_id=?")+" ORDER BY c.created_at DESC LIMIT 200";
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement(sql)) {
            if(!admin)p.setLong(1,userId);
            try(ResultSet r=p.executeQuery()) {
                List<Complaint> out=new ArrayList<>();
                while(r.next())out.add(new Complaint(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)));
                return out;
            }
        }
    }

    public void addComplaint(long userId,String pnr,String category,String description) throws SQLException {
        String sql=pnr.isBlank()
            ? "INSERT INTO complaints(user_id,category,description) VALUES (?,?,?)"
            : "INSERT INTO complaints(user_id,booking_id,category,description) SELECT ?,b.id,?,? FROM bookings b WHERE b.pnr=? AND b.user_id=?";
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setLong(1,userId);p.setString(2,category);p.setString(3,description);
            if(!pnr.isBlank()){p.setString(4,pnr);p.setLong(5,userId);}
            if(p.executeUpdate()!=1)throw new IllegalArgumentException("That demo PNR does not belong to your account.");
        }
    }

    public boolean respondComplaint(long id,String status,String response) throws SQLException {
        try(Connection c=Db.open();PreparedStatement p=c.prepareStatement("UPDATE complaints SET status=?,admin_response=?,updated_at=now() WHERE id=?")) {
            p.setString(1,status);p.setString(2,response);p.setLong(3,id);return p.executeUpdate()==1;
        }
    }
}
