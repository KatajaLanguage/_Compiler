package com.github.ktj.lang;

import com.github.ktj.bytecode.AccessFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class KtjDataClass extends Compilable{

    public final LinkedHashMap<String, KtjField> fields;

    public KtjDataClass(Modifier modifier, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, uses, statics, file, line);
        fields = new LinkedHashMap<>();
    }

    @Override
    public void validateTypes() {
        for(KtjField field:fields.values()) field.validateTypes();
    }

    public boolean addField(boolean constant, String type, String name, int line){
        if(fields.containsKey(name)) return true;

        Modifier mod = new Modifier(AccessFlag.ACC_PUBLIC);
        mod.constant = constant;

        fields.put(name, new KtjField(mod, type, null, uses, statics, file, line));
        return false;
    }
}
