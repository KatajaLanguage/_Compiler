package com.github.ktj.lang;

import java.util.HashMap;

public class KtjField extends Compilable{

    public String type, initValue;

    public KtjField(Modifier modifier, String type, String initValue, HashMap<String, String> uses, String file, int line){
        super(modifier, uses, file, line);
        this.type = type;
        this.initValue = initValue;
    }

    @Override
    public void validateTypes() {
        type = validateType(type, true);
    }
}
