package com.flashsale.ledger.sweep;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flashsale.ledger.repo.ReservationRepo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Getter
@RequiredArgsConstructor
@Slf4j
public class ExpirySweeper {
	
	private final ReservationExpiryService reservationExpiryService;
	private final ReservationRepo reservationRepo;
	
	@Value("${sweeper.batch-size:50}")
	private Integer batchSize;
	
	@Scheduled(fixedDelayString = "${sweeper.fixed-delay-ms:5000}", 
			initialDelayString = "${sweeper.initial-delay-ms:5000}")
	public void sweep() {
		/**
		 * Get unresolved 
		 * 
		 */
		List<String> unresolvedResolutionIds = reservationRepo.getUnresolvedExpiringReservationIds(getBatchSize());
		if(unresolvedResolutionIds == null || unresolvedResolutionIds.isEmpty()) {
			log.info("No unresolved Expiring Reservation Ids at the moment");
			return;
		}
		
		int expired=0, lost=0, resolved=0;
		log.info("Starting reservationExpiry for {} reservations",unresolvedResolutionIds.size());
		for(String id: unresolvedResolutionIds) {
			try {
				switch(reservationExpiryService.expireOne(id)) {
					case ALREADY_RESOLVED -> resolved++;
					case EXPIRED -> expired++;
					case LOST_RACE-> lost++;
				}				
			}catch(Exception e) {
				log.error("Something went wrong expiring reservationId: "+id, e);
			}
		}
		
		log.info("Batch Complete Expired: {}, Lost Race: {}, Already Resolved: {}",expired, lost, resolved);
		
	}
}
