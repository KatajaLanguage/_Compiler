package com.github.ktj.compiler;

import com.github.ktj.lang.*;

import java.net.IDN;
import java.util.ArrayList;
import java.util.HashMap;

final class SyntacticParser {

    private static final class Scope{
        public final Scope last;
        private final HashMap<String, String> vars = new HashMap<>();

        Scope(Scope current){
            last = current;
        }

        Scope(String type, KtjMethod method){
            last = null;
            vars.put("this", type);
            vars.put("null", "java.lang.method");
            for(int i = 0;i < method.parameter.length;i++){
                if(vars.containsKey(method.parameter[i].name())) throw new RuntimeException(STR."\{method.parameter[i].name()} is already defined at \{method.file}:\{method.line+1}");
                vars.put(method.parameter[i].name(), method.parameter[i].type());
            }
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
    private String clazzName;
    private String[] code;
    private int index;

    AST[] parseAst(Compilable clazz, String clazzName, KtjMethod method, String code){
        if(code.trim().isEmpty()) return new AST[]{CompilerUtil.getDefaultReturn(method.returnType)};

        this.clazz = clazz;
        this.method = method;
        scope = new Scope(clazzName, method);
        this.clazzName = clazzName;
        this.code = code.split("\n");
        index = -1;

        ArrayList<AST> ast = new ArrayList<>();

        while(hasNext()){
            try {
                AST e = parseNextLine();
                if(e != null) ast.add(e);
                else if(th.toStringNonMarked().startsWith("}")) throw new RuntimeException("illegal argument");
            }catch(RuntimeException e){
                RuntimeException exception = new RuntimeException(STR."\{e.getMessage()} at \{method.file}:\{method.line+1}");
                exception.setStackTrace(e.getStackTrace());
                throw exception;
            }
        }

        if(!(ast.getLast() instanceof AST.Return)) ast.add(CompilerUtil.getDefaultReturn(method.returnType));

        return ast.toArray(new AST[0]);
    }

    private AST parseNextLine(){
        nextLine();
        if(th.isEmpty() || th.toStringNonMarked().startsWith("}")) return null;

        return switch(th.assertToken("}", Token.Type.IDENTIFIER).s()){
            case "while" -> parseWhile();
            case "if" -> parseIf();
            case "return" -> parseReturn();
            case "}" -> {
                th.assertNull();
                yield null;
            }
            default -> {
                th.last();
                yield parseVarAssignment();
            }
        };
    }

    private AST.Return parseReturn(){
        AST.Return ast = new AST.Return();

        if(th.hasNext()){
            ast.calc = parseCalc();
            ast.type = ast.calc.type;
        }else ast.type = "void";

        if(!ast.type.equals(method.returnType)) throw new RuntimeException(STR."Expected type \{method.returnType} got \{ast.type}");

        return ast;
    }

    private AST.While parseWhile(){
        AST.While ast = new AST.While();

        th.assertHasNext();
        ast.condition = parseCalc();

        if(!ast.condition.type.equals("boolean")) throw new RuntimeException(STR."Expected type boolean got \{ast.condition.type}");

        th.assertToken("{");
        th.assertNull();

        ast.ast = parseContent();
        th.assertNull();

        return ast;
    }

    private AST.If parseIf(){
        AST.If ast = new AST.If();

        th.assertHasNext();
        ast.condition = parseCalc();
        if(!ast.condition.type.equals("boolean")) throw new RuntimeException(STR."Expected type boolean got \{ast.condition.type}");
        th.assertToken("{");
        th.assertNull();

        ast.ast = parseContent();

        AST.If current = ast;
        boolean end = false;
        while(th.hasNext() && !end){
            current = (current.elif = new AST.If());
            th.assertToken("else");

            if(th.assertToken("if", "{").equals("if")){
                current.condition = parseCalc();
                if(!current.condition.type.equals("boolean")) throw new RuntimeException(STR."Expected type boolean got \{current.condition.type}");
                th.assertToken("{");
            }else end = true;

            current.ast = parseContent();
        }

        if(!th.toStringNonMarked().equals("} ")) throw new RuntimeException("illegal argument");

        return ast;
    }

    private AST[] parseContent(){
        ArrayList<AST> astList = new ArrayList<>();

        AST current = parseNextLine();
        if(current != null) astList.add(current);

        while(!th.toStringNonMarked().startsWith("}")){
            current = parseNextLine();
            if(current != null) astList.add(current);
        }

        if(th.toStringNonMarked().startsWith("}")){
            th.toFirst();
            th.assertToken("}");
        }else throw new RuntimeException("Expected }");

        return astList.toArray(new AST[0]);
    }

    private AST.VarAssignment parseVarAssignmentOld(){
        AST.VarAssignment ast = new AST.VarAssignment();

        ast.type = th.assertToken(Token.Type.IDENTIFIER).s();
        ast.name = th.assertToken("=", Token.Type.IDENTIFIER).equals("=") ? null : th.current().s();
        if(!th.current().equals("=")) th.assertToken("=");
        ast.calc = parseCalc();

        if(ast.name != null){
            if(!ast.calc.type.equals(ast.type)) throw new RuntimeException(STR."Expected type \{ast.type} got \{ast.calc.type}");
            if(scope.getType(ast.name) != null) throw new RuntimeException(STR."\{ast.name} is already defined");
            else scope.add(ast.name, ast.type);
        }else if(scope.getType(ast.type) == null){
            scope.add(ast.type, ast.calc.type);
            ast.name = ast.type;
            ast.type = ast.calc.type;
        }

        return ast;
    }

    private AST.VarAssignment parseVarAssignment(){
        AST.VarAssignment ast;

        String first = th.assertToken(Token.Type.IDENTIFIER).s();
        if(th.assertToken(Token.Type.IDENTIFIER, ".", "=").equals(".")){
            ast = new AST.PutField();
            throw new RuntimeException("illegal argument");
        }else{
            ast = new AST.VarAssignment();

            ast.type = first;
            if(!th.current().equals("=")){
                ast.name = th.current().s();
                th.assertToken("=");
            }
            ast.calc = parseCalc();

            if(ast.name != null){
                if(!ast.calc.type.equals(ast.type)) throw new RuntimeException(STR."Expected type \{ast.type} got \{ast.calc.type}");
                if(scope.getType(ast.name) != null) throw new RuntimeException(STR."\{ast.name} is already defined");
                scope.add(ast.name, ast.type);
            }else{
                if(scope.getType(ast.type) == null){
                    boolean hasField = false;

                    if(clazz instanceof KtjClass clazz){
                        if(clazz.fields.get(ast.type) != null){
                            AST.VarAssignment old = ast;
                            ast = new AST.PutField();

                            ast.name = old.type;
                            ast.type = old.calc.type;
                            ast.calc = old.calc;
                            ((AST.PutField)(ast)).clazz = clazzName;

                            if(clazz.fields.get(old.type).modifier.statik) ((AST.PutField)(ast)).statik = true;
                            if(!ast.type.equals(ast.calc.type)) throw new RuntimeException(STR."Expected type \{ast.type} got \{ast.calc.type}");

                            hasField = true;
                        }
                    }

                    if(!hasField) {
                        scope.add(ast.type, ast.calc.type);
                        ast.name = ast.type;
                        ast.type = ast.calc.type;
                    }
                }else{
                    ast.name = ast.type;
                    ast.type = ast.calc.type;
                }
            }
        }


        return ast;
    }

    private AST.Calc parseCalc(){
        AST.Calc ast = new AST.Calc();

        ast.value = parseValue();
        ast.type = ast.value.type;

        while(th.hasNext()){
            if(!th.next().equals(Token.Type.OPERATOR) || th.current().equals("->")){
                th.last();
                return ast;
            }

            ast.setRight();
            ast.op = th.current().s();
            ast.value = parseValue();
            ast.type = CompilerUtil.getOperatorReturnType(ast.right.type, ast.value.type, ast.op);

            if(ast.type == null) throw new RuntimeException(STR."Operator \{ast.op} is not defined for \{ast.right.type} and \{ast.value.type}");
        }

        return ast;
    }

    private AST.Value parseValue(){
        AST.Value ast = new AST.Value();

        switch(th.next().t()){
            case CHAR, SHORT, INTEGER, LONG, DOUBLE, FLOAT -> {
                ast.token = th.current();
                ast.type = th.current().t().toString();
            }
            case IDENTIFIER -> {
                if(scope.getType(th.current().s()) == null) throw new RuntimeException(STR."\{th.current()} is not defined");
                ast.load = new AST.Load();
                ast.load.name = th.current().s();
                ast.type = (ast.load.type = scope.getType(th.current().s()));
            }
            default -> throw new RuntimeException("illegal argument");
        }

        return ast;
    }

    private void nextLine(){
        clazz.line++;
        index++;
        th = Lexer.lex(code[index]);
    }

    private boolean hasNext(){
        return index < code.length - 1;
    }
}
