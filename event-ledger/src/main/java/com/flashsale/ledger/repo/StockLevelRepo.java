package com.flashsale.ledger.repo;

import com.flashsale.ledger.reservation.StockLevel;

public interface StockLevelRepo {

	 StockLevel lockStockRow(String sku);
	 Integer incrementReservedCountInStockLevel(String sku, Integer quantity);
	 Integer decrementReservedCountInStockLevel(String sku, Integer quantity);
	 Integer decrementReservedIncrementSoldInStockLevelByQuantity(String sku, Integer quantity);
}
