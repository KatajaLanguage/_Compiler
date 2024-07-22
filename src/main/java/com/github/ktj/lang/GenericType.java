package com.github.ktj.lang;

import java.util.ArrayList;

public class GenericType {
    public String name;
    public ArrayList<String> bounds;

    public GenericType(String name, ArrayList<String> bounds){
        this.name = name;
        this.bounds = bounds;
    }
}
