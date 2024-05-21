package com.github.ktj.lang;

import java.util.HashMap;

public class KtjField extends Compilable{

    public String type;

    public KtjField(Modifier modifier, String type, HashMap<String, String> uses) {
        super(modifier, uses);
        this.type = type;
    }

    @Override
    public void validateTypes() {
        type = validateType(type);
    }
}
