package in.railwaydemo;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses a disposable real PostgreSQL database and the packaged WAR, including JSP compilation. */
class ApplicationIT {
    static EmbeddedPostgres pg;
    static Tomcat tomcat;
    static String base;
    static Repository repo = new Repository();
    static long origin, destination, run;
    static LocalDate date;

    @BeforeAll static void start() throws Exception {
        pg = EmbeddedPostgres.builder().setPort(55439).setServerConfig("timezone", "Asia/Kolkata").start();
        try (Connection c = Db.open(); Statement s = c.createStatement()) {
            for (int i=0;i<2;i++) {
                s.execute(Files.readString(Path.of("src/main/resources/db/schema.sql")));
                s.execute(Files.readString(Path.of("src/main/resources/db/seed.sql")));
            }
            try (ResultSet r = s.executeQuery("SELECT (SELECT id FROM stations WHERE code='NDLS'),(SELECT id FROM stations WHERE code='LKO'),r.id,r.service_date FROM train_runs r JOIN trains t ON t.id=r.train_id WHERE t.number='D1001' ORDER BY service_date LIMIT 1")) {
                assertTrue(r.next());origin=r.getLong(1);destination=r.getLong(2);run=r.getLong(3);date=r.getObject(4,LocalDate.class);
            }
        }
        tomcat = new Tomcat();
        Path baseDir=Files.createTempDirectory("railway-tomcat-");
        Files.createDirectories(baseDir.resolve("webapps"));
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);tomcat.getConnector();
        tomcat.addWebapp("/tktmeter", Path.of("target/tktmeter.war").toAbsolutePath().toString()).addWelcomeFile("index.jsp");
        tomcat.start();
        base="http://localhost:"+tomcat.getConnector().getLocalPort()+"/tktmeter/app";
    }
    @AfterAll static void stop() throws Exception {
        try { if(tomcat!=null){tomcat.stop();tomcat.destroy();} } finally { if(pg!=null)pg.close(); }
    }

    static class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        HttpResponse<String> get(String path) throws Exception {
            var response=client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());
            Matcher m=Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(response.body());if(m.find())token=m.group(1);
            return response;
        }
        HttpResponse<String> post(String path, String... fields) throws Exception {
            StringJoiner form=new StringJoiner("&");
            form.add("csrf="+enc(token==null?"":token));
            for(int i=0;i<fields.length;i+=2)form.add(enc(fields[i])+"="+enc(fields[i+1]));
            return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(),HttpResponse.BodyHandlers.ofString());
        }
        static String enc(String v){return URLEncoder.encode(v,java.nio.charset.StandardCharsets.UTF_8);}
        void register(String email) throws Exception {
            assertEquals(200,get("/register").statusCode());
            assertEquals(302,post("/register","name","<script>alert(1)</script>","email",email,"password","  passenger-password  ").statusCode());
            assertEquals(200,get("/bookings").statusCode());
        }
    }

    @Test void passengerAndAdminHttpFlow() throws Exception {
        Browser b=new Browser();
        assertEquals(200,b.get("/").statusCode());
        String site=base.substring(0,base.length()-4);
        assertEquals(200,b.client.send(HttpRequest.newBuilder(URI.create(site+"/")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200,b.client.send(HttpRequest.newBuilder(URI.create(site+"/assets/style.css")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404,b.get("/missing-page").statusCode());
        assertEquals(200,b.get("/search?origin="+origin+"&destination="+destination+"&date="+date).statusCode());
        assertEquals(400,b.get("/search?origin="+origin+"&destination="+origin+"&date="+date).statusCode());
        assertEquals(400,b.get("/search?date=invalid").statusCode());
        assertEquals(400,b.get("/search?origin="+origin+"&destination="+destination+"&date="+date.minusDays(2)).statusCode());
        assertFalse(b.get("/search?origin="+destination+"&destination="+origin+"&date="+date).body().contains("Book seat"));
        assertEquals(302,b.get("/bookings").statusCode());
        b.register("passenger@example.test");
        assertEquals(403,b.get("/admin").statusCode());
        assertEquals(403,b.post("/admin/train","number","BAD","name","Unauthorized").statusCode());
        String token=b.token;b.token="wrong";assertEquals(403,b.post("/cancel","booking","1").statusCode());b.token=token;
        assertEquals(400,b.post("/book","run","bad","origin","bad","destination","bad","passenger","Test","age","20").statusCode());
        assertEquals(400,b.post("/book","run","99999999","origin",""+origin,"destination",""+destination,"passenger","Test","age","20").statusCode());
        assertEquals(200,b.get("/book?run="+run+"&origin="+origin+"&destination="+destination).statusCode());
        assertEquals(302,b.post("/book","run",""+run,"origin",""+origin,"destination",""+destination,"passenger","<script>test</script>","age","25").statusCode());
        String bookings=b.get("/bookings").body();
        assertTrue(bookings.contains("&lt;script&gt;test&lt;/script&gt;"));assertFalse(bookings.contains("<script>test</script>"));
        var user=repo.login("passenger@example.test","  passenger-password  ").orElseThrow();
        assertTrue(repo.login("passenger@example.test","passenger-password").isEmpty());
        var ticket=repo.bookings(user.id(),false).get(0);
        assertTrue(ticket.pnr().startsWith("DEMO-"));assertEquals(19,repo.search(origin,destination,date).get(0).available());
        Browser other=new Browser();other.register("other@example.test");
        assertFalse(other.get("/bookings").body().contains(ticket.pnr()));
        assertEquals(302,other.post("/cancel","booking",""+ticket.id()).statusCode());
        assertEquals("CONFIRMED",repo.bookings(user.id(),false).get(0).status());
        assertEquals(400,other.post("/complaints","pnr",ticket.pnr(),"category","BOOKING","description","Someone else's ticket").statusCode());
        assertEquals(302,b.post("/complaints","pnr",ticket.pnr(),"category","BOOKING","description","Please check my ticket").statusCode());
        Browser admin=new Browser();admin.get("/login");
        assertEquals(302,admin.post("/login","email","admin@example.test","password","integration-admin-password").statusCode());
        assertEquals(200,admin.get("/admin").statusCode());
        long complaint=repo.complaints(user.id(),false).get(0).id();
        assertEquals(302,admin.post("/admin/complaint","id",""+complaint,"status","RESOLVED","response","Checked and resolved").statusCode());
        assertTrue(b.get("/complaints").body().contains("Checked and resolved"));
        assertEquals(302,admin.post("/admin/run","number","D1001","date",date.plusDays(25).toString(),"capacity","3","fare","123.45").statusCode());
        assertEquals(3,repo.search(origin,destination,date.plusDays(25)).get(0).available());
        assertEquals(302,admin.post("/admin/station","code","TEST","name","Test station","city","Test city").statusCode());
        assertEquals(302,admin.post("/admin/train","number","TEST1","name","Test train").statusCode());
        assertEquals(302,admin.post("/admin/stop","number","TEST1","station",""+origin,"order","1","arrival","","departure","10:00","dayOffset","0").statusCode());
        assertEquals(302,b.post("/cancel","booking",""+ticket.id()).statusCode());
        assertFalse(repo.cancel(ticket.id(),user.id()));assertEquals(20,repo.search(origin,destination,date).get(0).available());
        assertEquals(405,b.get("/logout").statusCode());assertEquals(302,b.post("/logout").statusCode());
        assertEquals(302,b.get("/bookings").statusCode());
        b.get("/login");assertEquals(400,b.post("/login","email","passenger@example.test","password","wrong password").statusCode());
        b.get("/register");assertEquals(400,b.post("/register","name","Duplicate","email","passenger@example.test","password","long-enough-password").statusCode());
    }

    @Test void lastSeatIsAtomicAndInvalidBookingsRollback() throws Exception {
        var u=repo.register("Concurrency","race@example.test","concurrency-password");
        LocalDate raceDate=date.plusDays(30);repo.addRun("D1001",raceDate,1,new java.math.BigDecimal("10.00"));
        long raceRun=repo.search(origin,destination,raceDate).get(0).id();
        BookingService service=new BookingService();
        assertThrows(IllegalArgumentException.class,()->service.book(u.id(),raceRun,destination,origin,"Passenger",30));
        assertThrows(IllegalArgumentException.class,()->service.book(u.id(),raceRun,origin,destination,"Passenger",0));
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task=()->{gate.await();try {service.book(u.id(),raceRun,origin,destination,"Passenger",30);return true;}catch(IllegalArgumentException soldOut){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(0,repo.search(origin,destination,raceDate).get(0).available());
        assertThrows(IllegalArgumentException.class,()->service.book(u.id(),raceRun,origin,destination,"Passenger",30));
        var ticket=repo.bookings(u.id(),false).get(0);assertTrue(repo.cancel(ticket.id(),u.id()));
        String newPnr=service.book(u.id(),raceRun,origin,destination,"Passenger",30);assertNotEquals(ticket.pnr(),newPnr);
        assertEquals(1,repo.bookings(u.id(),false).stream().filter(x->x.status().equals("CONFIRMED")).count());
    }
    @Test void capacityAndFareValidationPreserveExistingTickets() throws Exception {
        var user=repo.register("Capacity test","capacity@example.test","capacity-password");
        LocalDate day=date.plusDays(35);repo.addRun("D1001",day,2,new java.math.BigDecimal("12.50"));
        long id=repo.search(origin,destination,day).get(0).id();
        BookingService service=new BookingService();
        service.book(user.id(),id,origin,destination,"First",20);
        service.book(user.id(),id,origin,destination,"Second",20);
        assertThrows(IllegalArgumentException.class,()->repo.addRun("D1001",day,1,new java.math.BigDecimal("99.00")));
        assertEquals(new java.math.BigDecimal("12.50"),repo.bookings(user.id(),false).get(0).fare());
        assertThrows(IllegalArgumentException.class,()->repo.addRun("D1001",day,2,new java.math.BigDecimal("1.234")));
        assertThrows(IllegalArgumentException.class,()->repo.addRun("D1001",day,2,new java.math.BigDecimal("100000000")));
        assertThrows(IllegalArgumentException.class,()->repo.addRun("D1001",day,0,new java.math.BigDecimal("1")));
        assertThrows(IllegalArgumentException.class,()->repo.addRun("MISSING",day,2,new java.math.BigDecimal("1")));
        repo.addRun("D1001",day,3,new java.math.BigDecimal("99.00"));
        assertEquals(1,repo.search(origin,destination,day).get(0).available());
        assertTrue(repo.bookings(user.id(),false).stream().allMatch(x->x.fare().compareTo(new java.math.BigDecimal("12.50"))==0));
    }

    @Test void databaseRejectsDuplicateSeatsAndPastOrInactiveRunsCannotBeBooked() throws Exception {
        var user=repo.register("Rules test","rules@example.test","rules-test-password");
        LocalDate day=date.plusDays(40);repo.addRun("D1001",day,2,new java.math.BigDecimal("10"));
        long id=repo.search(origin,destination,day).get(0).id();
        BookingService service=new BookingService();
        service.book(user.id(),id,origin,destination,"Passenger",30);
        try(Connection c=Db.open();Statement st=c.createStatement()) {
            SQLException duplicate=assertThrows(SQLException.class,()->st.executeUpdate("INSERT INTO bookings(pnr,user_id,run_id,origin_id,destination_id,passenger_name,passenger_age,seat_number,fare,status) SELECT 'DEMO-DUPLICATE',user_id,run_id,origin_id,destination_id,passenger_name,passenger_age,seat_number,fare,status FROM bookings WHERE run_id="+id));
            assertEquals("23505",duplicate.getSQLState());
            st.executeUpdate("UPDATE train_runs SET active=false WHERE id="+id);
            assertThrows(IllegalArgumentException.class,()->service.book(user.id(),id,origin,destination,"Passenger",30));
            st.executeUpdate("UPDATE train_runs SET active=true,service_date=current_date-1 WHERE id="+id);
            assertThrows(IllegalArgumentException.class,()->service.book(user.id(),id,origin,destination,"Passenger",30));
            assertFalse(repo.cancel(repo.bookings(user.id(),false).get(0).id(),user.id()));
        }
    }

    @Test void sameDayDepartedTrainCannotBeBookedOrCancelled() throws Exception {
        var user=repo.register("Departed","departed@example.test","departed-password");
        repo.addTrain("DEPARTED","Departed demo");
        repo.addStop("DEPARTED",origin,1,null,"00:00",0);
        repo.addStop("DEPARTED",destination,2,"01:00",null,0);
        LocalDate future=date.plusDays(45);
        repo.addRun("DEPARTED",future,2,new java.math.BigDecimal("10"));
        long id=repo.search(origin,destination,future).get(0).id();
        BookingService service=new BookingService();service.book(user.id(),id,origin,destination,"Passenger",30);
        var ticket=repo.bookings(user.id(),false).get(0);
        try(Connection c=Db.open();Statement st=c.createStatement()) { st.executeUpdate("UPDATE train_runs SET service_date=current_date WHERE id="+id); }
        assertThrows(IllegalArgumentException.class,()->service.book(user.id(),id,origin,destination,"Passenger",30));
        assertFalse(repo.cancel(ticket.id(),user.id()));
        assertTrue(repo.run(id,origin,destination).isEmpty());
    }

    @Test void capacityEditWaitsForBookingCommitBeforeCheckingOccupiedSeats() throws Exception {
        var user=repo.register("Capacity race","capacity-race@example.test","capacity-race-password");
        LocalDate day=date.plusDays(50);repo.addRun("D1001",day,2,new java.math.BigDecimal("10"));
        long id=repo.search(origin,destination,day).get(0).id();
        new BookingService().book(user.id(),id,origin,destination,"First",30);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try(Connection c=Db.open();Statement st=c.createStatement()) {
            c.setAutoCommit(false);
            st.executeQuery("SELECT id FROM train_runs WHERE id="+id+" FOR UPDATE").close();
            st.executeUpdate("INSERT INTO bookings(pnr,user_id,run_id,origin_id,destination_id,passenger_name,passenger_age,seat_number,fare,status) SELECT 'DEMO-CAPACITY-RACE',user_id,run_id,origin_id,destination_id,passenger_name,passenger_age,2,fare,status FROM bookings WHERE run_id="+id);
            Future<Boolean> edit=pool.submit(()->{try {repo.addRun("D1001",day,1,new java.math.BigDecimal("10"));return true;}catch(IllegalArgumentException rejected){return false;}});
            boolean waiting=false;
            try(Connection observer=Db.open();Statement check=observer.createStatement()) {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(System.nanoTime()<deadline) {
                    try(ResultSet r=check.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%train_runs%' AND pid<>pg_backend_pid()")) {r.next();waiting=r.getInt(1)>0;}
                    if(waiting)break;
                    Thread.sleep(20);
                }
            }
            assertTrue(waiting,"Capacity update should wait for the booking transaction's run lock");
            c.commit();
            assertFalse(edit.get(10,TimeUnit.SECONDS),"Capacity must not shrink below the newly committed seat");
            assertEquals(0,repo.search(origin,destination,day).get(0).available());
        } finally {pool.shutdownNow();}
    }

}
