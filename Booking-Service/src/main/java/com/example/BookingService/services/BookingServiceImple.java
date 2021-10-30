package com.example.BookingService.services;

import com.example.BookingService.dao.BookingDAO;
import com.example.BookingService.dtos.BookingRequestDTO;
import com.example.BookingService.dtos.BookingResponseDTO;
import com.example.BookingService.dtos.PaymentRequestDTO;
import com.example.BookingService.entities.BookingInfoEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class BookingServiceImple implements BookingService {

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    private BookingDAO bookingDAO;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${apiGateway.baseUrl}")
    String apiGatewayBaseUrl;

    @Override
    public BookingResponseDTO postBookingInfo(BookingRequestDTO bookingRequestDTO) {
        /* Getting number of rooms */
        int numOfRooms = bookingRequestDTO.getNumOfRooms();

        /* Getting number of days */
        Date toDate = bookingRequestDTO.getToDate();
        Date fromDate = bookingRequestDTO.getFromDate();
        long diffInMilliSeconds = Math.abs(toDate.getTime() - fromDate.getTime());
        long numOfDays = TimeUnit.DAYS.convert(diffInMilliSeconds, TimeUnit.MILLISECONDS);

        /* Some edge cases while booking no of rooms and selecting from-to dates */
        if (numOfRooms <= 0)  throw new RuntimeException("Number of rooms requested must be greater than 0.");
        if (numOfDays<=0) throw  new RuntimeException("To-date must be greater than From-date");

        /* constructing string of room numbers from list of room numbers */
        String roomNumbers = "";
        ArrayList<String> bookedRooms = getRandomNumbers(numOfRooms);
        for(int i = 1; i <= bookedRooms.size();i++) {
            roomNumbers += bookedRooms.get(i-1);
            if(i!=bookedRooms.size())
                roomNumbers+=",";
        }
        /* Calculating room price */
        double roomPrice = (double)(1000 * numOfRooms * numOfDays);

        /* Setting response DTO values */
        BookingResponseDTO bookingResponseDTO = new BookingResponseDTO();
        bookingResponseDTO.setFromDate(fromDate);
        bookingResponseDTO.setToDate(toDate);
        bookingResponseDTO.setAadharNumber(bookingRequestDTO.getAadharNumber());
        bookingResponseDTO.setNumOfRooms(numOfRooms);
        bookingResponseDTO.setRoomNumbers(roomNumbers);
        bookingResponseDTO.setRoomPrice(roomPrice);
        bookingResponseDTO.setBookedOn(new Date());

        /* Mapping response DTO to booking entity and saving in db, and returning response DTO */
        BookingInfoEntity bookingInfoEntity = modelMapper.map(bookingResponseDTO,BookingInfoEntity.class);
        BookingInfoEntity savedBookingInfoEntity = bookingDAO.save(bookingInfoEntity);
        return modelMapper.map(savedBookingInfoEntity, BookingResponseDTO.class);
    }

    private ArrayList<String> getRandomNumbers(int count){
        Random rand = new Random();
        int upperBound = 100;
        ArrayList<String>numberList = new ArrayList<>();

        for (int i=0; i<count; i++) numberList.add(String.valueOf(rand.nextInt(upperBound)));
        return numberList;
    }
    @Override
    public BookingResponseDTO postPaymentInfo(int bookingId, PaymentRequestDTO paymentRequestDTO) {
        //AOP approach to handle invalid ID and payment mode done before rest of method logic in aspect package.

        //Calling payment-service for retrieving transaction id:
        String paymentServiceUrl = apiGatewayBaseUrl + "/payment/transaction";
        int transactionId = restTemplate.postForObject(paymentServiceUrl, paymentRequestDTO, Integer.class);

        //updating transaction id on the booking
        BookingInfoEntity bookingInfoEntity = bookingDAO.getById(bookingId);
        bookingInfoEntity.setTransactionId(transactionId);
        BookingInfoEntity savedBookingInfoEntity = bookingDAO.save(bookingInfoEntity);

        // Printing booking confirmation
        String message = "Booking confirmed for user with aadhaar number: "
                + savedBookingInfoEntity.getAadharNumber()
                +    "    |    "
                + "Here are the booking details:    " + savedBookingInfoEntity.toString();
        System.out.println(message);


        BookingResponseDTO bookingResponseDTO = modelMapper.map(savedBookingInfoEntity, BookingResponseDTO.class);
        return bookingResponseDTO;
    }

}
