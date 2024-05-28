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

        while(hasNext()) ast.add(parseNextLine());

        return ast.toArray(new AST[0]);
    }

    private AST parseNextLine(){
        nextLine();

        return parseVarAssignment();
    }

    private AST.VarAssignment parseVarAssignment(){
        AST.VarAssignment ast = new AST.VarAssignment();

        ast.type = th.assertToken(Token.Type.IDENTIFIER).s();
        ast.name = th.assertToken(Token.Type.IDENTIFIER).s();
        th.assertToken("=");
        ast.calc = parseCalc();

        return ast;
    }

    private AST.Calc parseCalc(){
        AST.Calc ast = new AST.Calc();

        ast.value = parseValue();
        ast.type = ast.value.type;

        return ast;
    }

    private AST.Value parseValue(){
        AST.Value ast = new AST.Value();

        switch(th.next().t()){
            case CHAR, SHORT, INTEGER, LONG, DOUBLE, FLOAT, STRING -> {
                ast.token = th.current();
                ast.type = th.current().t().toString();
            }
            default -> throw new RuntimeException("illegal argument");
        }

        return ast;
    }

    private void nextLine(){
        clazz.line++;
        th = Lexer.lex(code[index++]);
    }

    private boolean hasNext(){
        return index < code.length - 1;
    }
}
