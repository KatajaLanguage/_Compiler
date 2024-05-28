package com.github.ktj.lang;

import java.util.Arrays;
import java.util.HashMap;

public class KtjMethod extends Compilable{

    public record Parameter(String type, String name){}

    public Parameter[] parameter;
    public String returnType;
    public final String code;

    public KtjMethod(Modifier modifier, String returnType, String code, Parameter[] parameter, HashMap<String, String> uses, String file, int line){
        super(modifier, uses, file, line);
        this.parameter = parameter;
        this.returnType = returnType;
        this.code = code;
    }

    @Override
    public void validateTypes() {
        if(!returnType.equals("void")) returnType = validateType(returnType);
        Arrays.stream(parameter).forEach(p -> new Parameter(validateType(p.type), p.name));
    }

    public boolean isAbstract(){
        return modifier.abstrakt;
    }
}