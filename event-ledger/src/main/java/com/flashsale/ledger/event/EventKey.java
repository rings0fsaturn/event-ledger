package com.flashsale.ledger.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventKey {
	CAPTURED(":captured"), EXPIRED(":expired");
	private final String value;
}
