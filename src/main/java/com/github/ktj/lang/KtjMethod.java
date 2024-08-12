/*
 * Copyright (C) 2024 Xaver Weste
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.ktj.lang;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjMethod extends Compilable{

    public static class Parameter{
        public final String name, type;
        public final boolean constant;

        public Parameter(boolean constant, String type, String name){
            this.constant = constant;
            this.name = name;
            this.type = type;
        }
    }

    public Parameter[] parameter;
    public String returnType;
    public final String code;

    public KtjMethod(Modifier modifier, ArrayList<GenericType> generics, String returnType, String code, Parameter[] parameter, HashMap<String, String> uses, ArrayList<String> statics, String file, int line){
        super(modifier, generics, uses, statics, file, line);
        this.parameter = parameter;
        this.returnType = returnType;
        this.code = code;
    }

    @Override
    public void validateTypes() {
        if(!returnType.equals("void")) returnType = validateType(returnType, true);

        ArrayList<Parameter> help = new ArrayList<>();
        for(Parameter p:parameter) help.add(new Parameter(p.constant, validateType(p.type, true), p.name));
        parameter = help.toArray(new Parameter[0]);
    }

    public boolean isAbstract(){
        return modifier.abstrakt || modifier.natife;
    }

    public int getLocals(){
        return parameter.length + 1;
    }
}
