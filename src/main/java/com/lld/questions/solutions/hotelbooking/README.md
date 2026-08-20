```mermaid

classDiagram

    class HotelBookingSystem {
        - List~Hotel~ hotels
        - List~User~ users
        
        + searchHotel(String city, String pincode, String state) List~Hotel~
    }
    
    class Hotel {
        - String hotelId
        - Location location
        - List~Room~ rooms

        + searchRoom(RoomType type, RoomStatus roomStatus, Date startDate, Date endDate) List~Room~
        + bookRoom(Room room)
    }

    class BookingStatus {
        <<enumeration>>
        BOOKED
        CANCELLED
    }

    class Booking {
        - String bookingId
        - BookingStatus status
        - Date startDate
        - Date endDate
        - User user
        - Room room
        - Payment payment
        
        + updateBooking(Room room) void
        + cancelBooking(Room room) void
    }

    class Gender {
        <<enumeration>>
        MALE
        FEMALE
        OTHER
    }

    class User {
        - String userId
        - String email
        - String username
        - int age
        - Gender gender
        - List~Booking~ bookings

        + updateProfile(User user) void
    }

    class RoomType {
        <<enumeration>>
        STANDARD
        DELUXE
        SUITE
    }

    class RoomStatus {
        <<enumeration>>
        BOOKED
        AVAILABLE
        UNDER_MAINTENANCE
    }

    class Room {
        - String roomId
        - RoomType roomType
        - RoomStatus status 
        - String description
        - Hotel hotel
        - int capacity
        - double price
        - List~Booking~ bookings

        + updateRoomStatus(Room room) void
    }

    class Location {
        - String locationId
        - String city
        - String state
        - String pinCode
        - String addressLine1
        - String addressLine2
        - double longitude
        - double latitude
    }

    class PaymentStatus {
        <<enumeration>>
        INIT
        PENDING
        PAID
        CANCELLED
        REFUNDED
    }

    class PaymentType {
        <<enumeration>>
        UPI
        CASH
        CARD
    }

    class Payment {
        - String paymentId
        - PaymentType type
        - PaymentStatus paymentStatus

        + makePayment()
        + cancelPayment()
        + refundPayment()
    }

    %% Relationships
    HotelBookingSystem "1" o-- "0..*" Hotel
    HotelBookingSystem "1" o-- "0..*" User
    Hotel "1" o-- "0..*" Room
    Hotel "1" o-- "1" Location
    User "1" o-- "0..*" Booking
    Booking "1" o-- "1" Payment
    
    %% Enum relationships
    Booking --> BookingStatus : status
    User --> Gender : gender
    Room --> RoomType : roomType
    Room --> RoomStatus : status
    Payment --> PaymentType : type
    Payment --> PaymentStatus : paymentStatus

```
- A hotel system where user can search hotels based on city, area, country
- A user can see the available rooms of hotel
- A user can book the room with available date (standard or deluxe rooms)
- A user can do payment
- A user can receive notification on room availability