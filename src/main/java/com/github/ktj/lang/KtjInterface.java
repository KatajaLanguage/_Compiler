package com.github.ktj.lang;

import java.util.HashMap;

public class KtjInterface extends Compilable{

    private HashMap<String, String> methods;

    public KtjInterface(Modifier modifier) {
        super(modifier);
        methods = new HashMap<>();
    }

    public boolean addMethod(String desc, String method){
        if(methods.containsKey(desc)) return true;

        methods.put(desc, method);
        return false;
    }
}
