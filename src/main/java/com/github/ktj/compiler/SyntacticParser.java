package com.github.ktj.compiler;

import java.util.HashMap;

final class SyntacticParser {

    private final class Scope{
        public final Scope last;
        private final HashMap<String, String> vars = new HashMap<>();

        Scope(Scope current){
            last = current;
        }

        Scope(String type){
            last = null;
            vars.put("this", type);
        }

        String getType(String name){
            if(vars.containsKey(name))
                return vars.get(name);

            return last == null ? null : last.getType(name);
        }

        void add(String name, String type){
            if(!vars.containsKey(name))
                vars.put(name, type);
        }
    }

    static AST[] parseAst(String code){
        return null;
    }
}
