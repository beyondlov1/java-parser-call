package com.beyond;

import org.apache.commons.lang3.StringUtils;

/**
 * @author chenshipeng
 * @date 2021/11/17
 */
public class Utils {

    public static String getFullClassNameFromMethod(String methodFullName){
        return StringUtils.substringBefore(methodFullName, "#");
    }

    public static String substringBetween(String target, String start,String last){
        return StringUtils.substringBeforeLast(StringUtils.substringAfter(target, start), last);
    }


    public static String simpleMethod(String methodFullName){
        return substringBetween(methodFullName, "#", "(");
    }
}
