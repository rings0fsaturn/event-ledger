package com.flashsale.ledger.repo;

import java.time.OffsetDateTime;

public interface UtilityRepo {
	OffsetDateTime getDBClockTime();
}
