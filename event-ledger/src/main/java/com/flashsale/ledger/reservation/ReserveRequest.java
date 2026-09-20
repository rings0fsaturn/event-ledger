package com.flashsale.ledger.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Setter;

@Builder
public record ReserveRequest(
		@NotBlank(message = "Order Id cannot be empty")
		String orderId, 
		
		@NotBlank(message = "Account Id cannot be empty")
		String accountId, 

		@NotBlank(message = "Idempotency Key cannot be empty")
		String idempotencyKey,
		
		@NotBlank(message = "SKU cannot be empty")
		String sku, 
		
		@PositiveOrZero(message = "Quantity has to be >=0")
		Integer quantity 
		) {}
