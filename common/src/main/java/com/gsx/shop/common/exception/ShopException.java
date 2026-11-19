package com.gsx.shop.common.exception;
/** 仅携带稳定错误，不保存外部响应正文或密钥。 */
public class ShopException extends RuntimeException {
    public final ShopError error;

    public ShopException(ShopError error) {
        super(error.message);
        this.error = error;
    }
}
