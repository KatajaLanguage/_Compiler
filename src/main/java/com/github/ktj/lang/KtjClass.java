package com.github.ktj.lang;

import java.util.HashMap;

public class KtjClass extends KtjInterface{

    public final HashMap<String, KtjField> fields;

    public KtjClass(Modifier modifier, HashMap<String, String> uses) {
        super(modifier, uses);
        fields = new HashMap<>();
    }

    public boolean addField(String name, KtjField method){
        if(fields.containsKey(name)) return true;

        fields.put(name, method);
        return false;
    }

    public boolean isEmpty(){
        return fields.isEmpty() && methods.isEmpty();
    }
}
