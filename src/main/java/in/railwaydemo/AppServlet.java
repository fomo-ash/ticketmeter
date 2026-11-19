package in.railwaydemo;

import in.railwaydemo.Models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

@WebServlet("/app/*")
public final class AppServlet extends HttpServlet {
    private final Repository repo=new Repository();
    private final BookingService bookingService=new BookingService();

    @Override public void init() throws ServletException {
        try { repo.stations();repo.createAdminIfConfigured(); }
        catch(SQLException | IllegalArgumentException e) { throw new ServletException("Check database connection, schema, and admin configuration",e); }
    }

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        req.setCharacterEncoding("UTF-8");
        String path=path(req);
        try {
            if(path.equals("/logout")) { resp.sendError(405);return; }
            User user=user(req);
            if(path.equals("/bookings") && user==null) { redirect(req,resp,"/login");return; }
            if(path.equals("/complaints") && user==null) { redirect(req,resp,"/login");return; }
            if(path.equals("/admin") && !admin(user)) { resp.sendError(403);return; }
            req.setAttribute("user",user);
            req.setAttribute("page",path);
            req.setAttribute("stations",repo.stations());
            req.setAttribute("csrf",csrf(req));
            if(path.equals("/bookings"))req.setAttribute("bookings",repo.bookings(user.id(),false));
            if(path.equals("/complaints"))req.setAttribute("complaints",repo.complaints(user.id(),false));
            if(path.equals("/admin")) { req.setAttribute("bookings",repo.bookings(user.id(),true));req.setAttribute("complaints",repo.complaints(user.id(),true));req.setAttribute("trains",repo.trains()); }
            if(path.equals("/search")) {
                long origin=number(req.getParameter("origin")),destination=number(req.getParameter("destination"));
                LocalDate date=LocalDate.parse(req.getParameter("date"));
                if(origin==destination || date.isBefore(LocalDate.now()))throw new IllegalArgumentException("Choose different stations and a future travel date.");
                req.setAttribute("runs",repo.search(origin,destination,date));
                req.setAttribute("originId",origin);req.setAttribute("destinationId",destination);req.setAttribute("travelDate",date);
            }
            if(path.equals("/book")) {
                if(user==null) { redirect(req,resp,"/login");return; }
                long run=number(req.getParameter("run")),origin=number(req.getParameter("origin")),destination=number(req.getParameter("destination"));
                req.setAttribute("run",repo.run(run,origin,destination).orElseThrow(()->new IllegalArgumentException("Train run not found.")));
                req.setAttribute("originId",origin);req.setAttribute("destinationId",destination);
            }
            if(!path.equals("/")&&!path.equals("/search")&&!path.equals("/login")&&!path.equals("/register")&&!path.equals("/book")&&!path.equals("/bookings")&&!path.equals("/complaints")&&!path.equals("/admin")) { resp.sendError(404);return; }
            req.getRequestDispatcher("/WEB-INF/jsp/app.jsp").forward(req,resp);
        } catch(SQLException e) { throw new ServletException(e); }
        catch(RuntimeException e) { resp.setStatus(400);req.setAttribute("csrf",csrf(req));req.setAttribute("error","Enter valid stations, date, and train details.");req.setAttribute("page","/");try { req.setAttribute("stations",repo.stations()); } catch(SQLException ex) { throw new ServletException(ex); }req.getRequestDispatcher("/WEB-INF/jsp/app.jsp").forward(req,resp); }
    }

    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        req.setCharacterEncoding("UTF-8");
        if(!csrf(req).equals(req.getParameter("csrf"))) { resp.sendError(403,"Invalid form token");return; }
        String path=path(req);
        try {
            switch(path) {
                case "/logout" -> { req.getSession().invalidate();redirect(req,resp,"/"); }
                case "/register" -> {
                    String name=required(req,"name",100),email=required(req,"email",255),password=password(req);
                    if(!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || password.length()<12)throw new IllegalArgumentException("Enter a valid email and a password of at least 12 characters.");
                    loginSession(req,repo.register(name,email,password));redirect(req,resp,"/bookings");
                }
                case "/login" -> {
                    User u=repo.login(required(req,"email",255),password(req)).orElseThrow(()->new IllegalArgumentException("Invalid email or password."));
                    loginSession(req,u);redirect(req,resp,admin(u)?"/admin":"/bookings");
                }
                case "/book" -> {
                    User u=requireUser(req,resp);if(u==null)return;
                    String pnr=bookingService.book(u.id(),number(req.getParameter("run")),number(req.getParameter("origin")),number(req.getParameter("destination")),required(req,"passenger",100),Integer.parseInt(req.getParameter("age")));
                    req.getSession().setAttribute("flash","Booking confirmed. Demo PNR: "+pnr);redirect(req,resp,"/bookings");
                }
                case "/cancel" -> {
                    User u=requireUser(req,resp);if(u==null)return;
                    boolean cancelled=repo.cancel(number(req.getParameter("booking")),u.id());
                    req.getSession().setAttribute("flash",cancelled?"Booking cancelled.":"Booking cannot be cancelled.");redirect(req,resp,"/bookings");
                }
                case "/admin/run" -> {
                    if(!admin(user(req))) { resp.sendError(403);return; }
                    LocalDate date=LocalDate.parse(req.getParameter("date"));int capacity=Integer.parseInt(req.getParameter("capacity"));BigDecimal fare=new BigDecimal(req.getParameter("fare"));
                    if(!date.isAfter(LocalDate.now()) || capacity<1 || capacity>1000 || fare.signum()<0)throw new IllegalArgumentException("Enter a future date, capacity 1–1000, and nonnegative fare.");
                    repo.addRun(required(req,"number",10),date,capacity,fare);req.getSession().setAttribute("flash","Train run saved.");redirect(req,resp,"/admin");
                }
                case "/admin/station" -> {
                    if(!admin(user(req))) { resp.sendError(403);return; }
                    repo.addStation(required(req,"code",10),required(req,"name",100),required(req,"city",100));
                    req.getSession().setAttribute("flash","Station added.");redirect(req,resp,"/admin");
                }
                case "/admin/train" -> {
                    if(!admin(user(req))) { resp.sendError(403);return; }
                    repo.addTrain(required(req,"number",10),required(req,"name",100));
                    req.getSession().setAttribute("flash","Train added. Add its route stops next.");redirect(req,resp,"/admin");
                }
                case "/admin/stop" -> {
                    if(!admin(user(req))) { resp.sendError(403);return; }
                    int order=Integer.parseInt(req.getParameter("order")),offset=Integer.parseInt(req.getParameter("dayOffset"));
                    if(order<1 || offset<0 || offset>7)throw new IllegalArgumentException("Enter a valid stop order and day offset.");
                    String arrival=req.getParameter("arrival"),departure=req.getParameter("departure");
                    if((arrival==null || arrival.isBlank()) && (departure==null || departure.isBlank()))throw new IllegalArgumentException("Enter at least an arrival or departure time.");
                    repo.addStop(required(req,"number",10),number(req.getParameter("station")),order,arrival==null||arrival.isBlank()?null:arrival,departure==null||departure.isBlank()?null:departure,offset);
                    req.getSession().setAttribute("flash","Route stop saved.");redirect(req,resp,"/admin");
                }
                case "/complaints" -> {
                    User u=requireUser(req,resp);if(u==null)return;
                    String category=required(req,"category",40),description=required(req,"description",2000);
                    if(!category.matches("BOOKING|CANCELLATION|SCHEDULE|OTHER"))throw new IllegalArgumentException("Choose a valid complaint category.");
                    String pnr=req.getParameter("pnr");repo.addComplaint(u.id(),pnr==null?"":pnr.trim(),category,description);
                    req.getSession().setAttribute("flash","Complaint submitted.");redirect(req,resp,"/complaints");
                }
                case "/admin/complaint" -> {
                    if(!admin(user(req))) { resp.sendError(403);return; }
                    String status=required(req,"status",20),response=required(req,"response",2000);
                    if(!status.matches("OPEN|IN_PROGRESS|RESOLVED"))throw new IllegalArgumentException("Choose a valid complaint status.");
                    if(!repo.respondComplaint(number(req.getParameter("id")),status,response))throw new IllegalArgumentException("Complaint not found.");
                    req.getSession().setAttribute("flash","Complaint updated.");redirect(req,resp,"/admin");
                }
                default -> resp.sendError(404);
            }
        } catch(SQLException e) {
            if("23505".equals(e.getSQLState())) showError(req,resp,path,"That account or schedule already exists.");
            else throw new ServletException(e);
        } catch(RuntimeException e) { showError(req,resp,path,e instanceof java.time.DateTimeException?"Enter a valid date or time.":e instanceof NumberFormatException?"Enter valid numbers.":e.getMessage()); }
    }

    private void showError(HttpServletRequest req,HttpServletResponse resp,String path,String error) throws ServletException,IOException {
        req.setAttribute("error",error);req.setAttribute("page",path.startsWith("/admin/")?"/admin":path.equals("/cancel")?"/bookings":path);
        req.setAttribute("user",user(req));req.setAttribute("csrf",csrf(req));
        try { req.setAttribute("stations",repo.stations());if(path.startsWith("/admin/")){req.setAttribute("trains",repo.trains());req.setAttribute("bookings",repo.bookings(user(req).id(),true));req.setAttribute("complaints",repo.complaints(user(req).id(),true));}if(path.equals("/cancel"))req.setAttribute("bookings",repo.bookings(user(req).id(),false));if(path.equals("/complaints"))req.setAttribute("complaints",repo.complaints(user(req).id(),false));if(path.equals("/book")){try { long run=number(req.getParameter("run")),origin=number(req.getParameter("origin")),destination=number(req.getParameter("destination"));req.setAttribute("run",repo.run(run,origin,destination).orElse(null));if(req.getAttribute("run")==null)req.setAttribute("page","/");req.setAttribute("originId",origin);req.setAttribute("destinationId",destination); } catch (NumberFormatException invalid) { req.setAttribute("page","/"); }} }
        catch(SQLException e) { throw new ServletException(e); }
        resp.setStatus(400);req.getRequestDispatcher("/WEB-INF/jsp/app.jsp").forward(req,resp);
    }
    private static String path(HttpServletRequest r) { return r.getPathInfo()==null?"/":r.getPathInfo(); }
    private static long number(String value) { return Long.parseLong(value); }
    private static String password(HttpServletRequest r) { String v=r.getParameter("password");if(v==null || v.isBlank() || v.length()>128)throw new IllegalArgumentException("Enter a valid password (maximum 128 characters).");return v; }
    private static String required(HttpServletRequest r,String field,int max) { String v=r.getParameter(field);if(v==null || v.isBlank() || v.trim().length()>max)throw new IllegalArgumentException("Enter a valid "+field+".");return v.trim(); }
    private static User user(HttpServletRequest r) { return (User)r.getSession().getAttribute("user"); }
    private static boolean admin(User u) { return u!=null && "ADMIN".equals(u.role()); }
    private static User requireUser(HttpServletRequest req,HttpServletResponse resp) throws IOException { User u=user(req);if(u==null)resp.sendError(401);return u; }
    private static String csrf(HttpServletRequest r) { HttpSession s=r.getSession();String token=(String)s.getAttribute("csrf");if(token==null){token=UUID.randomUUID().toString();s.setAttribute("csrf",token);}return token; }
    private static void loginSession(HttpServletRequest r,User user) { r.getSession().invalidate();HttpSession s=r.getSession(true);s.setAttribute("user",user);s.setAttribute("csrf",UUID.randomUUID().toString()); }
    private static void redirect(HttpServletRequest req,HttpServletResponse resp,String path) throws IOException { resp.sendRedirect(req.getContextPath()+"/app"+path); }
}
