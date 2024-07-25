package com.github.ktj.lang;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjField extends Compilable{

    public String type, initValue;

    public KtjField(Modifier modifier, String type, ArrayList<GenericType> genericTypes, String initValue, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, genericTypes, uses, statics, file, line);
        this.type = type;
        this.initValue = initValue;
    }

    public String correctType(){
        int i = genericIndex(type);
        if(i == -1) return type;
        assert genericTypes != null;
        return genericTypes.get(i).type;
    }

    @Override
    public void validateTypes() {
        type = validateType(type, true);
    }
}
