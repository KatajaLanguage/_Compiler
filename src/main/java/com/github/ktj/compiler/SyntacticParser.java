package com.github.ktj.compiler;

import com.github.ktj.lang.Compilable;
import com.github.ktj.lang.KtjMethod;

import java.util.ArrayList;
import java.util.HashMap;

final class SyntacticParser {

    private static final class Scope{
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

    private TokenHandler th;
    private Compilable clazz;
    private KtjMethod method;
    private Scope scope;
    private String[] code;
    private int index;

    AST[] parseAst(Compilable clazz, String clazzName, KtjMethod method, String code){
        if(code.trim().isEmpty()) return new AST[0];

        this.clazz = clazz;
        this.method = method;
        scope = new Scope(clazzName);
        this.code = code.split("\n");
        index = -1;

        ArrayList<AST> ast = new ArrayList<>();

        while(hasNext()) {

        }

        return ast.toArray(new AST[0]);
    }

    private void nextLine(){
        clazz.line++;
        th = Lexer.lex(code[index++]);
    }

    private boolean hasNext(){
        return index < code.length - 1;
    }
}
