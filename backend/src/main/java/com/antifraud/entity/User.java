package com.antifraud.entity;

import jakarta.persistence.*;

/**
 * 用户画像实体（对接运营商「用户数据」静态表）
 * 16 个字段对齐《需要对接的运营商数据.xlsx》的「用户数据」Sheet
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_bill_no", columnList = "billNo")
})
public class User {
    @Id
    @Column(length = 64)
    private String userId;        // 用户 ID（与 log_events.user_id 关联）

    private String billNo;        // 主计费号（手机号）
    private Integer custState;    // 客户状态 1潜在/2在网/3离网/4销户
    private Integer custLvl;      // 客户级别 1钻石/2金/3银/4普通/0无
    private Integer age;          // 客户年龄
    private Integer sex;          // 客户性别
    private Integer occupation;   // 所属行业
    private Integer marryState;   // 婚姻状况 0未知/1已婚/2未婚
    private Integer realNameFlag; // 实名认证标记
    private String cityId;        // 城市编码
    private String countyId;      // 县区编码
    private Integer userState;    // 用户状态
    private Integer userCreditId; // 用户信用度分档
    private Long userCreditValue; // 用户当前信用额度（分）
    private Integer innetDur;     // 用户在网时长（天）
    private String termMdl;       // 终端机型

    // Getters & Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public Integer getCustState() { return custState; }
    public void setCustState(Integer custState) { this.custState = custState; }
    public Integer getCustLvl() { return custLvl; }
    public void setCustLvl(Integer custLvl) { this.custLvl = custLvl; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public Integer getSex() { return sex; }
    public void setSex(Integer sex) { this.sex = sex; }
    public Integer getOccupation() { return occupation; }
    public void setOccupation(Integer occupation) { this.occupation = occupation; }
    public Integer getMarryState() { return marryState; }
    public void setMarryState(Integer marryState) { this.marryState = marryState; }
    public Integer getRealNameFlag() { return realNameFlag; }
    public void setRealNameFlag(Integer realNameFlag) { this.realNameFlag = realNameFlag; }
    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }
    public String getCountyId() { return countyId; }
    public void setCountyId(String countyId) { this.countyId = countyId; }
    public Integer getUserState() { return userState; }
    public void setUserState(Integer userState) { this.userState = userState; }
    public Integer getUserCreditId() { return userCreditId; }
    public void setUserCreditId(Integer userCreditId) { this.userCreditId = userCreditId; }
    public Long getUserCreditValue() { return userCreditValue; }
    public void setUserCreditValue(Long userCreditValue) { this.userCreditValue = userCreditValue; }
    public Integer getInnetDur() { return innetDur; }
    public void setInnetDur(Integer innetDur) { this.innetDur = innetDur; }
    public String getTermMdl() { return termMdl; }
    public void setTermMdl(String termMdl) { this.termMdl = termMdl; }
}
