package com.hlf.rpc.test.model;

public class OrderDTO {

    private String orderNo;
    private Double orderAmt;
    private String subject;
    private Integer payStatus;
    private Integer orderStatus;
    private String userNo;

    public OrderDTO(String orderNo, Double orderAmt, String subject, Integer payStatus, Integer OrderStatus, String userNo) {
        this.orderNo = orderNo;
        this.orderAmt = orderAmt;
        this.subject = subject;
        this.payStatus = payStatus;
        this.orderStatus = OrderStatus;
        this.userNo = userNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Double getOrderAmt() {
        return orderAmt;
    }

    public void setOrderAmt(Double orderAmt) {
        this.orderAmt = orderAmt;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Integer getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(Integer payStatus) {
        this.payStatus = payStatus;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getUserNo() {
        return userNo;
    }

    public void setUserNo(String userNo) {
        this.userNo = userNo;
    }
}
