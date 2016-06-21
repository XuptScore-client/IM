package com.vivavideo.imkit.database;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT. Enable "keep" sections if you want to edit. 
/**
 * Entity mapped to table USER_INFOS.
 */
public class UserInfos {

    private Long id;
    /** Not-null value. */
    private String userid;
    /** Not-null value. */
    private String username;
    private String portrait;
    /** Not-null value. */
    private String status;

    public UserInfos() {
    }

    public UserInfos(Long id) {
        this.id = id;
    }

    public UserInfos(Long id, String userid, String username, String portrait, String status) {
        this.id = id;
        this.userid = userid;
        this.username = username;
        this.portrait = portrait;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** Not-null value. */
    public String getUserid() {
        return userid;
    }

    /** Not-null value; ensure this value is available before it is saved to the database. */
    public void setUserid(String userid) {
        this.userid = userid;
    }

    /** Not-null value. */
    public String getUsername() {
        return username;
    }

    /** Not-null value; ensure this value is available before it is saved to the database. */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPortrait() {
        return portrait;
    }

    public void setPortrait(String portrait) {
        this.portrait = portrait;
    }

    /** Not-null value. */
    public String getStatus() {
        return status;
    }

    /** Not-null value; ensure this value is available before it is saved to the database. */
    public void setStatus(String status) {
        this.status = status;
    }

}