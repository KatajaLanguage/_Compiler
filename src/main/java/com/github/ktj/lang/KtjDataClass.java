package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class KtjDataClass extends Compilable{

    public final LinkedHashMap<String, KtjField> fields;

    public KtjDataClass(Modifier modifier, HashMap<String, String> uses){
        super(modifier, uses);
        fields = new LinkedHashMap<>();
    }

    @Override
    public void validateTypes() {
        for(KtjField field:fields.values()) field.validateTypes();
    }

    public boolean addField(String type, String name){
        if(fields.containsKey(name)) return true;

        fields.put(name, new KtjField(new Modifier(AccessFlag.ACC_PUBLIC), type, uses));
        return false;
    }
}
