package com.github.ktj.lang;

import java.util.HashMap;

public class KtjField extends Compilable{

    public String type;

    public KtjField(Modifier modifier, String type, HashMap<String, String> uses, String file, int line){
        super(modifier, uses, file, line);
        this.type = type;
    }

    @Override
    public void validateTypes() {
        type = validateType(type);
    }
}
