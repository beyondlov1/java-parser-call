package com.beyond;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author chenshipeng
 * @date 2021/11/17
 */
public class CallFileParser {

    public static Map<String, List<String>> parseForCallMap(String filePath) throws IOException {
        List<CallModel> list = parse(filePath);
        Map<String, List<String>> map = new HashMap<>();
        for (CallModel callModel : list) {
            map.computeIfAbsent(callModel.getCaller(), k -> new ArrayList<>());
            map.get(callModel.getCaller()).add(callModel.getTarget());
        }
        return map;
    }

    public static Map<String, List<String>> parseForTargetMap(String filePath) throws IOException {
        List<CallModel> list = parse(filePath);
        Map<String, List<String>> map = new HashMap<>();
        for (CallModel callModel : list) {
            map.computeIfAbsent(callModel.getTarget(), k -> new ArrayList<>());
            map.get(callModel.getTarget()).add(callModel.getCaller());
        }
        return map;
    }

    private static List<CallModel> parse(String filePath) throws IOException {
        List<CallModel> result = new ArrayList<>();
        List<String> lines = FileUtils.readLines(new File(filePath), StandardCharsets.UTF_8);
        lines.removeIf(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return !s.startsWith("M");
            }
        });
        for (String line : lines) {
            String[] callPair = StringUtils.split(line, " ");
            String callerRaw = callPair[0];
            String targetRaw = callPair[1];

            String callerStr = StringUtils.substringAfter(callerRaw, ":");
            String targetStr = StringUtils.substringAfter(targetRaw, ")");

            if (StringUtils.isNotBlank(callerStr) && StringUtils.isNotBlank(targetStr)){
                String caller = parseItem(callerStr);
                String target = parseItem(targetStr);
//                System.out.println(caller+":"+target);
                result.add(new CallModel(caller, target));
            }
        }

        Map<String, List<String>> map = new HashMap<>();
        for (CallModel callModel : result) {
            String callerNoParamMethodFullName = getMethodFullyQualifiedNameWithoutParam2(callModel.getCaller());
            map.computeIfAbsent(callerNoParamMethodFullName, k -> new ArrayList<>());
            map.get(callerNoParamMethodFullName).add(callModel.getCaller());

            String targetNoParamMethodFullName = getMethodFullyQualifiedNameWithoutParam2(callModel.getTarget());
            map.computeIfAbsent(targetNoParamMethodFullName, k -> new ArrayList<>());
            map.get(targetNoParamMethodFullName).add(callModel.getTarget());
        }

        for (CallModel callModel : result) {
            if (callModel.getCaller().contains("$")){
                String toFindCaller = getMethodFullyQualifiedNameWithoutParam2(callModel.getCaller());
                List<String> founds = map.get(toFindCaller);
                if (CollectionUtils.isNotEmpty(founds)){
                    String s = founds.get(0);
                    callModel.setCaller(s);
                }
            }

            if (callModel.getTarget().contains("$")){
                String toFind = getMethodFullyQualifiedNameWithoutParam2(callModel.getTarget());
                List<String> founds = map.get(toFind);
                if (CollectionUtils.isNotEmpty(founds)){
                    String s = founds.get(0);
                    callModel.setTarget(s);
                }
            }
        }
        return result;
    }


    public static String getMethodFullyQualifiedNameWithoutParam2(String methodFullName) {
        String className = Utils.getFullClassNameFromMethod(methodFullName);
        String methodName = Utils.simpleMethod(methodFullName);
        if (methodFullName.contains("$")){
            methodName = Utils.substringBetween(methodFullName, "$", "$");
        }
        return String.format("%s#%s()", className, methodName);
    }

    public static String getMethodFullyQualifiedNameWithoutParam(String classFullyQualifiedName, String methodName) {
        return String.format("%s#%s()", classFullyQualifiedName, methodName);
    }


    public static String parseItem(String callerRaw){
        String[] split = StringUtils.split(callerRaw, ":");
        String className = split[0];
        String other = split[1];
        String methodName = StringUtils.substringBefore(other, "(");
        List<String> paramClassStrList = new ArrayList<>();
        String paramClassStrs = StringUtils.substringBefore(StringUtils.substringAfter(other, "("), ")");
        String[] paramClassStrArr = StringUtils.split(paramClassStrs, ",");
        for (String paramClassStr : paramClassStrArr) {
            paramClassStrList.add(simpleClassName(paramClassStr));
        }
        return getMethodFullyQualifiedName(className, methodName, paramClassStrList);
    }

    public static String getMethodFullyQualifiedName(String classFullyQualifiedName, String methodName, List<String> paramTypes) {
        return String.format("%s#%s(%s)", classFullyQualifiedName, methodName, StringUtils.replace(String.join(",", paramTypes), " ",""));
    }

    public static String simpleClassName(String fullClassName){
        if (StringUtils.isAllLowerCase(fullClassName)){
            return fullClassName;
        }
        return StringUtils.substringAfterLast(fullClassName, ".");
    }


}
