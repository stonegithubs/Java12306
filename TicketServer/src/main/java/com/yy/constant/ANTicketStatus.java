package com.yy.constant;

public enum ANTicketStatus {

    WAIT_PAY("待支付", "候补成功，请前往12306完成候补订单待支付，为防止候补失败，我们会继续为您抢票！"),
    CANCELED("已取消", "您的候补订单已取消"),
    PAY_TIMEOUT("支付超时", "很可惜！您未在可支付时间内付款，候补订单自动取消。"),

    WAIT_COMPLY("待兑现", "支付完成，您待候补订单已进入12306候补队列"),
    EXPIRED("兑现失败","您的候补订单已到抢票截止日期，候补失败"),
    SUCCESS("兑现成功","恭喜您！您待候补订单已兑现！");

    private String status;
    private String desc;


    ANTicketStatus(String status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
