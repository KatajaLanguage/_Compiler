package com.github.ktj.lang;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class KtjDataClass extends Compilable{

    public final LinkedHashMap<String, KtjField> fields;

    public KtjDataClass(Modifier modifier, HashMap<String, String> uses){
        super(modifier, uses);
        fields = new LinkedHashMap<>();
    }

    public boolean addField(String name, KtjField method){
        if(fields.containsKey(name)) return true;

        fields.put(name, method);
        return false;
    }
}
