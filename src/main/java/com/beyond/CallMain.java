package com.beyond;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.beyond.Utils.*;

/**
 * @author chenshipeng
 * @date 2021/11/17
 */
public class CallMain {

    private final static Map<String, CompilationUnit> compilationUnitMap = new HashMap<>();

    public static void parseFuzzy(String root, Map<String, List<String>> target2CallerMap, Set<String> methodNames) throws FileNotFoundException {
        initCompilationUnits(root, "");

        methodNames = methodNames.stream().map(x -> StringUtils.substringBefore(x, "(")).collect(Collectors.toSet());

        List<String> methodFullyQualifiedNames = new ArrayList<>();
        for (String methodName : methodNames) {
            for (CompilationUnit compilationUnit : compilationUnitMap.values()) {
                MethodDeclaration methodDeclaration = compilationUnit.findFirst(MethodDeclaration.class, new Predicate<MethodDeclaration>() {
                    @Override
                    public boolean test(MethodDeclaration methodDeclaration) {
                        return StringUtils.equals(methodName, methodDeclaration.getNameAsString());
                    }
                }).orElse(null);
                if (methodDeclaration == null){
                    continue;
                }
                String methodFullyQualifiedName = getMethodFullyQualifiedName(methodDeclaration);
                methodFullyQualifiedNames.add(methodFullyQualifiedName);
            }
        }


        for (String methodFullyQualifiedName : methodFullyQualifiedNames) {
            System.out.println(methodFullyQualifiedName);
        }

        System.out.println();

        parse(root, target2CallerMap, methodFullyQualifiedNames);
    }

    public static void parse(String root, Map<String, List<String>> target2CallerMap, List<String> methodFullyQualifiedNames) throws FileNotFoundException {
        initCompilationUnits(root, "");
        StringBuilder stringBuilder = new StringBuilder();
        for (String methodFullyQualifiedName : methodFullyQualifiedNames) {
            List<String> targets = new ArrayList<>();
            targets.add(methodFullyQualifiedName);
            List<MethodChainElement> methodChainElements = upstreamR(root, targets, target2CallerMap);
            List<MethodChainElement> ends = new ArrayList<>();
            for (MethodChainElement chainRoot : methodChainElements) {
                findEnd(chainRoot, ends);
            }
            for (MethodChainElement chainRoot : methodChainElements) {
                List<MethodChainElement> upList = chainRoot.getUpList();
                for (MethodChainElement up : upList) {
                    up.getDownList().add(chainRoot);
                }
            }
            stringBuilder.append(toCsv(methodFullyQualifiedName, ends));
        }
        System.out.println(stringBuilder.toString());
    }


    private static StringBuilder toCsv(String toUpMethod, List<MethodChainElement> ends){
        StringBuilder sb = new StringBuilder();
        for (MethodChainElement end : ends) {
            if (end.getMethodDeclaration() == null){
                sb.append(simpleMethod(toUpMethod));
                sb.append(",");
                sb.append("-");
                sb.append(",");
                sb.append("-");
                sb.append("\n");
                continue;
            }
            Optional<AnnotationExpr> requestMapping = end.getMethodDeclaration().getAnnotationByName("RequestMapping");

            sb.append(simpleMethod(toUpMethod));
            sb.append(",");
            if (requestMapping.isPresent()){
                String path = requestMapping.get().asNormalAnnotationExpr().getPairs().get(0).getValue().asStringLiteralExpr().asString();
                if (path.startsWith("/")) {
                    sb.append(substringBetween(path, "/","/"));
                }else {
                    sb.append(StringUtils.substringBefore(path, "/"));
                }
            }else{
                sb.append("-");
            }

            sb.append(",");
            Optional<AnnotationExpr> apiOperation = end.getMethodDeclaration().getAnnotationByName("ApiOperation");
            if (apiOperation.isPresent()){
                sb.append(apiOperation.get().asNormalAnnotationExpr().getPairs().get(0).getValue().asStringLiteralExpr().asString());
            }else {
                sb.append("-");
            }

            sb.append("\n");
        }
        return sb;
    }


    private static void print(String toUpMethod, List<MethodChainElement> ends){
        System.out.println("### "+toUpMethod);
        for (MethodChainElement end : ends) {
            if (end.getMethodDeclaration() == null){
                System.out.println("not found, please check manual: "+end.getMethodFullName());
                System.out.println();
                continue;
            }
            Optional<AnnotationExpr> requestMapping = end.getMethodDeclaration().getAnnotationByName("RequestMapping");
            Optional<AnnotationExpr> apiOperation = end.getMethodDeclaration().getAnnotationByName("ApiOperation");
            System.out.println("    -"+end.getMethodFullName()+"\n    -"+ requestMapping + "\n    -"+ apiOperation);
            System.out.println();
        }
        System.out.println();
        System.out.println();
    }

    private static void findEnd(MethodChainElement chainRoot, List<MethodChainElement> ends) {
        if (chainRoot.isContinuedByParent() && CollectionUtils.isEmpty(chainRoot.getUpList())){
            return;
        }
        if (CollectionUtils.isEmpty(chainRoot.getUpList())){
            ends.add(chainRoot);
        }else{
            List<MethodChainElement> upList = chainRoot.getUpList();
            if (CollectionUtils.isNotEmpty(upList)){
                for (MethodChainElement methodChainElement : upList) {
                    findEnd(methodChainElement, ends);
                }
            }
        }
    }

    private static List<MethodChainElement> upstreamR(String root, List<String> targets, Map<String, List<String>> target2CallerMap) {

        Set<String> replacedByInterfaceOrParentClasses = new HashSet<>();
        for (String target : new ArrayList<>(targets)) {
            ClassOrInterfaceDeclaration targetClass = findClass(root, getFullClassNameFromMethod(target));
            if (targetClass == null){
//                throw new RuntimeException("class not found"); // fixme 内部类
                System.out.println("not found: "+target);
                continue;
            }
            CompilationUnit compilationUnit = findCompilationUnit(root, getFullClassNameFromMethod(target));
            NodeList<ImportDeclaration> imports = compilationUnit.getImports();
            Map<String, ImportDeclaration> importMap = imports.stream().filter(x->!x.isAsterisk()).collect(Collectors.toMap(x -> simpleClassName(x.getNameAsString()), x -> x));
            NodeList<ClassOrInterfaceType> implementedTypes = targetClass.getImplementedTypes();
            for (ClassOrInterfaceType implementedType : implementedTypes) {
                String parentClassFullName;
                ImportDeclaration importDeclaration = importMap.get(implementedType.getNameAsString());
                if (importDeclaration == null){
                    parentClassFullName = getFullClassNameFromMethod(target).replace(targetClass.getNameAsString(), implementedType.getNameAsString());
                }else{
                    parentClassFullName = importDeclaration.getNameAsString();
                }
                CompilationUnit parentCompilationUnit = findCompilationUnit(root, parentClassFullName);
                if (parentCompilationUnit != null){
                    String methodFullNameInParent = target.replace(getFullClassNameFromMethod(target), parentClassFullName);
                    MethodDeclaration method = findMethodIn(parentCompilationUnit, root, methodFullNameInParent);
                    if (method!=null){
                        targets.add(methodFullNameInParent);
                        replacedByInterfaceOrParentClasses.add(target);
                    }
                }

            }
            NodeList<ClassOrInterfaceType> extendedTypes = targetClass.getExtendedTypes();
            for (ClassOrInterfaceType extendedType : extendedTypes) {
                String parentClassFullName;
                ImportDeclaration importDeclaration = importMap.get(extendedType.getNameAsString());
                if (importDeclaration == null){
                    parentClassFullName = getFullClassNameFromMethod(target).replace(targetClass.getNameAsString(), extendedType.getNameAsString());
                }else{
                    parentClassFullName = importDeclaration.getNameAsString();
                }
                CompilationUnit parentCompilationUnit = findCompilationUnit(root, parentClassFullName);
                if (parentCompilationUnit != null) {
                    String methodFullNameInParent = target.replace(getFullClassNameFromMethod(target), parentClassFullName);
                    MethodDeclaration method = findMethodIn(parentCompilationUnit, root, methodFullNameInParent);
                    if (method != null) {
                        targets.add(methodFullNameInParent);
                        replacedByInterfaceOrParentClasses.add(target);
                    }
                }
            }
        }

        List<MethodChainElement> result = new ArrayList<>();
        for (String target : targets) {
            MethodChainElement chainRoot = new MethodChainElement();
            chainRoot.setMethodFullName(target);
            MethodDeclaration method = findMethod(root, target);
            chainRoot.setMethodDeclaration(method);
            if (replacedByInterfaceOrParentClasses.contains(target)){
                chainRoot.setContinuedByParent(true);
            }
            result.add(chainRoot);

            List<String> callers = target2CallerMap.get(target);
            if (callers == null){
                continue;
            }
            callers = new ArrayList<>(callers);
            if (callers.contains(target)){
                callers.removeIf(x -> StringUtils.equals(target, x));
                continue;
            }
            for (String caller : callers) {
                List<String> next = new ArrayList<>();
                next.add(caller);
                List<MethodChainElement> ups = upstreamR(root, next, target2CallerMap);
                if (CollectionUtils.isNotEmpty(ups)){
                    chainRoot.addAllUp(ups);
                }
            }
        }
        return result;
    }


    public static void upstreamForEnds(String root, List<String> targets, Map<String, List<String>> target2CallerMap, List<String> endCallers){

        Set<String> replacedByInterfaceOrParentClasses = new HashSet<>();
        for (String target : new ArrayList<>(targets)) {
            ClassOrInterfaceDeclaration targetClass = findClass(root, getFullClassNameFromMethod(target));
            if (targetClass == null){
//                throw new RuntimeException("class not found"); // fixme 内部类
                System.out.println("not found: "+target);
                continue;
            }
            CompilationUnit compilationUnit = findCompilationUnit(root, getFullClassNameFromMethod(target));
            NodeList<ImportDeclaration> imports = compilationUnit.getImports();
            Map<String, ImportDeclaration> importMap = imports.stream().filter(x->!x.isAsterisk()).collect(Collectors.toMap(x -> simpleClassName(x.getNameAsString()), x -> x));
            NodeList<ClassOrInterfaceType> implementedTypes = targetClass.getImplementedTypes();
            for (ClassOrInterfaceType implementedType : implementedTypes) {
                String parentClassFullName;
                ImportDeclaration importDeclaration = importMap.get(implementedType.getNameAsString());
                if (importDeclaration == null){
                    parentClassFullName = getFullClassNameFromMethod(target).replace(targetClass.getNameAsString(), implementedType.getNameAsString());
                }else{
                    parentClassFullName = importDeclaration.getNameAsString();
                }
                CompilationUnit parentCompilationUnit = findCompilationUnit(root, parentClassFullName);
                if (parentCompilationUnit != null){
                    String methodFullNameInParent = target.replace(getFullClassNameFromMethod(target), parentClassFullName);
                    MethodDeclaration method = findMethodIn(parentCompilationUnit, root, methodFullNameInParent);
                    if (method!=null){
                        targets.add(methodFullNameInParent);
                        replacedByInterfaceOrParentClasses.add(target);
                    }
                }

            }
            NodeList<ClassOrInterfaceType> extendedTypes = targetClass.getExtendedTypes();
            for (ClassOrInterfaceType extendedType : extendedTypes) {
                String parentClassFullName;
                ImportDeclaration importDeclaration = importMap.get(extendedType.getNameAsString());
                if (importDeclaration == null){
                    parentClassFullName = getFullClassNameFromMethod(target).replace(targetClass.getNameAsString(), extendedType.getNameAsString());
                }else{
                    parentClassFullName = importDeclaration.getNameAsString();
                }
                CompilationUnit parentCompilationUnit = findCompilationUnit(root, parentClassFullName);
                if (parentCompilationUnit != null) {
                    String methodFullNameInParent = target.replace(getFullClassNameFromMethod(target), parentClassFullName);
                    MethodDeclaration method = findMethodIn(parentCompilationUnit, root, methodFullNameInParent);
                    if (method != null) {
                        targets.add(methodFullNameInParent);
                        replacedByInterfaceOrParentClasses.add(target);
                    }
                }
            }
        }

        for (String target : targets) {
            List<String> callers = target2CallerMap.get(target);
            if (CollectionUtils.isEmpty(callers)){
                if (replacedByInterfaceOrParentClasses.contains(target)){
                    // 说明改用了接口去向上找
                    continue;
                }else{
                    endCallers.add(target);
                    continue;
                }
            }
            callers = new ArrayList<>(callers);
            if (callers.contains(target)){
                callers.removeIf(x -> StringUtils.equals(target, x));
                System.out.println("loop: "+target);
                continue;
            }
            upstreamForEnds(root, callers, target2CallerMap, endCallers);
        }
    }


    public static ClassOrInterfaceDeclaration findClass(String root, String targetClassFullName){
        CompilationUnit compilationUnit = findCompilationUnit(root, targetClassFullName);
        if (compilationUnit == null){
            return null;
        }

        ClassOrInterfaceDeclaration cls = compilationUnit.getClassByName(simpleClassName(targetClassFullName)).orElse(null);
        if (cls == null){
            cls = compilationUnit.getInterfaceByName(simpleClassName(targetClassFullName)).orElse(null);
        }
        if (cls == null){

            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
            cls = classOrInterfaceDeclaration.findFirst(ClassOrInterfaceDeclaration.class, new Predicate<ClassOrInterfaceDeclaration>() {
                @Override
                public boolean test(ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
                    return StringUtils.equals(simpleClassName(targetClassFullName), classOrInterfaceDeclaration.getNameAsString());
                }
            }).orElse(null);

        }
        return cls;
    }

    public static CompilationUnit findCompilationUnit(String root, String targetClassFullName){
        CompilationUnit compilationUnit = compilationUnitMap.get(package2Path(root, targetClassFullName));
        if (compilationUnit == null && StringUtils.contains(targetClassFullName, "$")){
            String targetClassFullNameProcessed = StringUtils.substringBeforeLast(targetClassFullName, "$");
            compilationUnit = findCompilationUnit(root, targetClassFullNameProcessed);
        }
        return compilationUnit;
    }

    public static String simpleClassName(String fullClassName){
        if (StringUtils.isAllLowerCase(fullClassName)){
            return fullClassName;
        }
        if (fullClassName.contains("$")){
            return StringUtils.substringAfterLast(fullClassName, "$");
        }
        return StringUtils.substringAfterLast(fullClassName, ".");
    }

    private static MethodDeclaration findMethodWithoutParam(String root, String toFindQualifiedName) {
        CompilationUnit compilationUnit = compilationUnitMap.get(package2Path(root, getClassNameFromMethodFullName(toFindQualifiedName)));
        return compilationUnit.findFirst(MethodDeclaration.class, new Predicate<MethodDeclaration>() {
            @Override
            public boolean test(MethodDeclaration methodDeclaration) {
                String methodFullyQualifiedName = getMethodFullyQualifiedNameWithoutParam(methodDeclaration);
                return StringUtils.equals(methodFullyQualifiedName, toFindQualifiedName);
            }
        }).orElse(null);
    }

    public static MethodDeclaration findMethodIn( CompilationUnit compilationUnit, String root, String toFindQualifiedName){
        return compilationUnit.findFirst(MethodDeclaration.class, new Predicate<MethodDeclaration>() {
            @Override
            public boolean test(MethodDeclaration methodDeclaration) {
                String methodFullyQualifiedName = getMethodFullyQualifiedName(methodDeclaration);
                return StringUtils.equals(methodFullyQualifiedName, toFindQualifiedName);
            }
        }).orElse(null);
    }

    public static MethodDeclaration findMethod(String root, String toFindQualifiedName){
        CompilationUnit compilationUnit = compilationUnitMap.get(package2Path(root, getClassNameFromMethodFullName(toFindQualifiedName)));
        if (compilationUnit == null){
            return null;
        }
        return findMethodIn(compilationUnit, root, toFindQualifiedName);
    }

    private static String getClassNameFromMethodFullName(String s){
        return StringUtils.substringBeforeLast(s, "#");
    }


    public static MethodDeclaration findMethod(String root, String className, String methodName, List<String> paramTypes){
        String toFindQualifiedName = getMethodFullyQualifiedName(className, methodName, paramTypes);
        CompilationUnit compilationUnit = compilationUnitMap.get(package2Path(root, className));
        return compilationUnit.findFirst(MethodDeclaration.class, new Predicate<MethodDeclaration>() {
            @Override
            public boolean test(MethodDeclaration methodDeclaration) {

                String methodFullyQualifiedName = getMethodFullyQualifiedName(methodDeclaration);
                return StringUtils.equals(methodFullyQualifiedName, toFindQualifiedName);
            }
        }).orElse(null);
    }


    public static String getMethodFullyQualifiedName(String classFullyQualifiedName, String methodName, List<String> paramTypes) {
        return String.format("%s#%s(%s)", classFullyQualifiedName, methodName, StringUtils.replace(String.join(",", paramTypes), " ",""));
    }

    public static String getMethodFullyQualifiedName(MethodDeclaration method) {
        ClassOrInterfaceDeclaration firstParent = findFirstParent(method, ClassOrInterfaceDeclaration.class);
        if (firstParent != null && firstParent.getFullyQualifiedName().isPresent()) {
            String params = method.getParameters().stream().map(NodeWithType::getTypeAsString).map(x->{
                if (x.contains("<") && x.contains(">")){
                    // 抹除泛型
                    return StringUtils.replace(x,"<"+Utils.substringBetween(x, "<", ">")+">", "");
                }else{
                    return x;
                }
            }).collect(Collectors.joining(","));
            return String.format("%s#%s(%s)", firstParent.getFullyQualifiedName().get(), method.getNameAsString(), params);
        }
        return null;
    }

    public static String getMethodFullyQualifiedNameWithoutParam(String classFullyQualifiedName, String methodName) {
        return String.format("%s#%s()", classFullyQualifiedName, methodName);
    }


    public static String getMethodFullyQualifiedNameWithoutParam(MethodDeclaration method) {
        ClassOrInterfaceDeclaration firstParent = findFirstParent(method, ClassOrInterfaceDeclaration.class);
        if (firstParent != null && firstParent.getFullyQualifiedName().isPresent()) {
            return String.format("%s#%s()", firstParent.getFullyQualifiedName().get(), method.getNameAsString());
        }
        return null;
    }


    public static <T extends Node> T findFirstParent(Node node, Class<T> c) {
        if (node.getParentNode().isPresent()) {
            Node parent = node.getParentNode().get();
            if (c.isAssignableFrom(parent.getClass())) {
                return c.cast(parent);
            } else {
                return findFirstParent(parent, c);
            }
        }
        return null;
    }

    public static void initCompilationUnits(String root, String packageName) throws FileNotFoundException {
        File directory = new File(package2Path(root, packageName));
        Collection<File> files;
        if (directory.isDirectory()) {
            files = FileUtils.listFiles(directory, new String[]{"java"}, true);
        } else {
            files = Collections.singletonList(directory);
        }
        for (File file : files) {
            CompilationUnit unit = compilationUnitMap.get(file.getAbsolutePath());
            if (unit == null && file.exists()) {
                unit = StaticJavaParser.parse(file);
                compilationUnitMap.put(file.getAbsolutePath(), unit);
            }
        }
    }

    public static String package2Path(String root, String packageName) {
        StringBuilder javaPath = new StringBuilder();
        String[] split = StringUtils.split(packageName, "\\.");
        for (String s : split) {
            if (StringUtils.isAllLowerCase(s)) {
                javaPath.append(s);
                javaPath.append(File.separator);
            } else {
                javaPath.append(s);
                javaPath.append(".java");
                javaPath.append(File.separator);
                break;
            }
        }
        if (StringUtils.isNotBlank(javaPath)) {
            String javaFilePath = javaPath.substring(0, javaPath.length() - 1);
            return Paths.get(root, javaFilePath).toString();
        } else {
            return root;
        }
    }
}
