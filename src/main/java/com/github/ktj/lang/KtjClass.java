package com.github.ktj.lang;

import java.util.HashMap;

public class KtjClass extends KtjInterface{

    private HashMap<String, KtjField> fields;

    public KtjClass(Modifier modifier) {
        super(modifier);
        fields = new HashMap<>();
    }

    public boolean addField(String name, KtjField method){
        if(fields.containsKey(name)) return true;

        fields.put(name, method);
        return false;
    }
}
