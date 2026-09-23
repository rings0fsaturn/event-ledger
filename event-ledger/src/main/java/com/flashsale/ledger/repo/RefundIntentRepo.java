package com.flashsale.ledger.repo;

import com.flashsale.ledger.payment.RefundIntent;

public interface RefundIntentRepo {
	int createRefundIntent(RefundIntent refund);
}
