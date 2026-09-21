package com.flashsale.ledger.repo.impl;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.repo.RepoQueries;
import com.flashsale.ledger.repo.UtilityRepo;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UtilityRepoImpl implements UtilityRepo {

	private final JdbcTemplate jdbcTemplate;
	
	@Override
	public OffsetDateTime getDBClockTime() {
		return jdbcTemplate.queryForObject(RepoQueries.getDBClockNow,OffsetDateTime.class);
	}

}
