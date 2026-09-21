package com.flashsale.ledger.repo.impl;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flashsale.ledger.exception.UnknownSkuException;
import com.flashsale.ledger.repo.RepoQueries;
import com.flashsale.ledger.repo.StockLevelRepo;
import com.flashsale.ledger.reservation.StockLevel;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StockLevelRepoImpl implements StockLevelRepo {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedJdbcTemplate;
	
	public StockLevel lockStockRow(String sku) {
		
		try {			
			return jdbcTemplate.queryForObject(
					RepoQueries.getStockLevelBySkuLockedForUpdate, 
					(rs,rowNum)-> StockLevel.builder()
					.total(rs.getInt("total"))
					.reserved(rs.getInt("reserved"))
					.sold(rs.getInt("sold"))
					.reservationTTLSeconds(rs.getInt("reservation_ttl_seconds"))
					.build(),
					sku
					);
		}catch(EmptyResultDataAccessException e) {
			throw new UnknownSkuException(sku);
		}
	}

	@Override
	public Integer incrementReservedCountInStockLevel(String sku, Integer quantity) {
		MapSqlParameterSource param = new MapSqlParameterSource().addValue("sku", sku).addValue("quantity", quantity);
		return namedJdbcTemplate.update(RepoQueries.incrementReservedStockLevelByQuantity, param);	
	}
	
	@Override
	public Integer decrementReservedCountInStockLevel(String sku, Integer quantity) {
		MapSqlParameterSource param = new MapSqlParameterSource().addValue("sku", sku).addValue("quantity", quantity);
		return namedJdbcTemplate.update(RepoQueries.decrementReservedStockLevelByQuantity, param);	
	}

}
