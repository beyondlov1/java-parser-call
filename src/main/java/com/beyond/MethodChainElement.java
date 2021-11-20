package com.beyond;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author chenshipeng
 * @date 2021/11/18
 */
public class MethodChainElement {

    private String methodFullName;
    private MethodDeclaration methodDeclaration;
    private List<MethodChainElement> upList = new ArrayList<>();
    private List<MethodChainElement> downList = new ArrayList<>();
    private boolean continuedByParent;

    public void addUp(MethodChainElement up){
        upList.add(up);
    }

    public void addAllUp(List<MethodChainElement> ups){
        upList.addAll(ups);
    }

    public String getMethodFullName() {
        return methodFullName;
    }

    public void setMethodFullName(String methodFullName) {
        this.methodFullName = methodFullName;
    }

    public MethodDeclaration getMethodDeclaration() {
        return methodDeclaration;
    }

    public void setMethodDeclaration(MethodDeclaration methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
    }

    public List<MethodChainElement> getUpList() {
        return upList;
    }

    public void setUpList(List<MethodChainElement> upList) {
        this.upList = upList;
    }

    public List<MethodChainElement> getDownList() {
        return downList;
    }

    public void setDownList(List<MethodChainElement> downList) {
        this.downList = downList;
    }

    public boolean isContinuedByParent() {
        return continuedByParent;
    }

    public void setContinuedByParent(boolean continuedByParent) {
        this.continuedByParent = continuedByParent;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodChainElement that = (MethodChainElement) o;

        return methodFullName != null ? methodFullName.equals(that.methodFullName) : that.methodFullName == null;
    }

    @Override
    public int hashCode() {
        return methodFullName != null ? methodFullName.hashCode() : 0;
    }
}
