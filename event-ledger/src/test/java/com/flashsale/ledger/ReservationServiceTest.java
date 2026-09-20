package com.flashsale.ledger;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.flashsale.ledger.reservation.ReservationService;
import com.flashsale.ledger.reservation.ReserveRequest;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class ReservationServiceTest {
	
	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ReservationService reservationService;
	
	@Test
	void createReservation() {
		System.out.println("####START");
		try{
			test();
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		System.out.println("####END");
	}

	private Optional<String> test() {
		List<String> s = null;
		return Optional.ofNullable(s.get(0));
		
	}
}
