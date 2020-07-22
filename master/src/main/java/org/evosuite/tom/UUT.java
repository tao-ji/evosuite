package org.evosuite.tom;

public class UUT {
    private String qualifiedName;
    private int depth;
    public UUT(String qualifiedName, int depth){
        this.qualifiedName=qualifiedName;
        this.depth=depth;
    }
    public String getQualifiedName(){
        return this.qualifiedName;
    }
    public int getDepth(){
        return this.depth;
    }
}
