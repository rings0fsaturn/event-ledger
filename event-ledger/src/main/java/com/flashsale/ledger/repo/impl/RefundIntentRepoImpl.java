package com.flashsale.ledger.repo.impl;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.payment.RefundIntent;
import com.flashsale.ledger.repo.RefundIntentRepo;
import com.flashsale.ledger.repo.RepoQueries;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RefundIntentRepoImpl implements RefundIntentRepo {

	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	@Override
	public int createRefundIntent(RefundIntent refund) {	
		MapSqlParameterSource param = new MapSqlParameterSource()
				.addValue("reservation_id", refund.reservationId())
				.addValue("order_id", refund.orderId())
				.addValue("reason", refund.reason());
		return namedJdbcTemplate.update(RepoQueries.createRefundIntent, param);
	}

}
