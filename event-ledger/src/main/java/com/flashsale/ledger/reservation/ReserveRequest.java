package com.flashsale.ledger.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

@Builder
public record ReserveRequest(
		@NotBlank(message = "Order Id cannot be empty")
		String orderId, 
		
		@NotBlank(message = "Account Id cannot be empty")
		String accountId, 

		String idempotencyKey,
		
		@NotBlank(message = "SKU cannot be empty")
		String sku, 
		
		@PositiveOrZero(message = "Quantity has to be >=0")
		Integer quantity 
		) {}
