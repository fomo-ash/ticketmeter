package in.railwaydemo;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Models {
    private Models() {}
    public record User(long id, String name, String email, String role) {
        public long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;} public String getRole(){return role;}
    }
    public record Station(long id, String code, String name) {
        public long getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
    }
    public record Run(long id, long trainId, String trainNumber, String trainName, LocalDate date,
                      int capacity, int available, BigDecimal fare, String origin, String destination,
                      String departure, String arrival) {
        public long getId(){return id;} public String getTrainNumber(){return trainNumber;} public String getTrainName(){return trainName;}
        public LocalDate getDate(){return date;} public int getAvailable(){return available;} public BigDecimal getFare(){return fare;}
        public String getOrigin(){return origin;} public String getDestination(){return destination;}
        public String getDeparture(){return departure;} public String getArrival(){return arrival;}
    }
    public record Booking(long id, String pnr, String trainNumber, String trainName, LocalDate date,
                          String origin, String destination, String passengerName, int age,
                          int seat, BigDecimal fare, String status) {
        public long getId(){return id;} public String getPnr(){return pnr;} public String getTrainNumber(){return trainNumber;}
        public String getTrainName(){return trainName;} public LocalDate getDate(){return date;} public String getOrigin(){return origin;}
        public String getDestination(){return destination;} public String getPassengerName(){return passengerName;}
        public int getAge(){return age;} public int getSeat(){return seat;} public BigDecimal getFare(){return fare;}
        public String getStatus(){return status;}
    }
    public record Complaint(long id, String category, String description, String status, String response,
                            String pnr, String createdAt) {
        public long getId(){return id;} public String getCategory(){return category;}
        public String getDescription(){return description;} public String getStatus(){return status;}
        public String getResponse(){return response;} public String getPnr(){return pnr;}
        public String getCreatedAt(){return createdAt;}
    }
}
