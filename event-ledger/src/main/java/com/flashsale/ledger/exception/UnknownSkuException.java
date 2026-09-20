package com.flashsale.ledger.exception;

public class UnknownSkuException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public UnknownSkuException(String msg) {
		super("Unknown SKU:"+msg);
	}
	
	public UnknownSkuException(String msg, Throwable cause) {
		super(msg,cause);
	}

}
