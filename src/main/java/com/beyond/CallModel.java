package com.beyond;

/**
 * @author chenshipeng
 * @date 2021/11/17
 */
public class CallModel {

    private String caller;
    private String target;

    public CallModel() {
    }

    public CallModel(String caller, String target) {
        this.caller = caller;
        this.target = target;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "CallModel{" +
                "caller='" + caller + '\'' +
                ", target='" + target + '\'' +
                '}';
    }
}
