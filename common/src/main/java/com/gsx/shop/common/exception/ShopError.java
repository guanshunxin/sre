package com.gsx.shop.common.exception;
/** 稳定业务错误及边界映射，不能把依赖故障返回成正常结果。 */
public enum ShopError {
    NOT_FOUND(404, "记录不存在"),
    INVALID(400, "参数不正确"),
    STOCK_EMPTY(409, "库存不足"),
    IDEMPOTENCY_CONFLICT(409, "同一幂等键对应不同订单"),
    CANNOT_CANCEL(409, "只有待备货订单可以取消"),
    DEPENDENCY_UNAVAILABLE(503, "依赖服务不可用"),
    DELIVERY_TIMEOUT(504, "运费服务响应超时"),
    DATABASE_UNAVAILABLE(503, "数据库操作失败"),
    LAB_DISABLED(404, "演练入口未启用"),
    LAB_BUSY(409, "已有资源演练正在执行"),
    INJECTED_FAILURE(503, "受控 HTTP 故障");
    public final int status;
    public final String message;

    ShopError(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
