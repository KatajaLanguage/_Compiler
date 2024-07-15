package com.github.ktj.lang;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjField extends Compilable{

    public String type, initValue;

    public KtjField(Modifier modifier, String type, String initValue, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, uses, statics, file, line);
        this.type = type;
        this.initValue = initValue;
    }

    @Override
    public void validateTypes() {
        type = validateType(type, true);
    }
}
