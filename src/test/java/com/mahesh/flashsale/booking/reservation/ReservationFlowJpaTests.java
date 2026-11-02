package com.mahesh.flashsale.booking.reservation;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "ticketing.inventory.backend=jpa")
class ReservationFlowJpaTests extends ReservationFlowSupport {
}
