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

        return switch(th.assertToken(Token.Type.IDENTIFIER).s()){
            case "while" -> parseWhile();
            case "if" -> parseIf();
            case "return" -> parseReturn();
            default -> {
                th.last();
                yield parseStatement();
            }
        };
    }

    private AST.Return parseReturn(){
        AST.Return ast = new AST.Return();

        if(th.hasNext()){
            ast.calc = parseCalc();
            ast.type = ast.calc.type;
            th.assertNull();
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

    private AST parseStatement(){
        if(th.next().equals(Token.Type.IDENTIFIER) && th.next().equals(Token.Type.IDENTIFIER)){
            th.toFirst();

            String type = th.assertToken(Token.Type.IDENTIFIER).s();
            String name = th.assertToken(Token.Type.IDENTIFIER).s();
            th.assertToken("=");
            AST.Calc calc = parseCalc();
            th.assertNull();

            if(!type.equals(calc.type)) throw new RuntimeException(STR."Expected type \{type} got \{calc.type}");
            if(scope.getType(name) != null) throw new RuntimeException(STR."variable \{name} is already defined");

            AST.VarAssignment ast = new AST.VarAssignment();

            ast.calc = calc;
            ast.type = type;
            ast.load = new AST.Load();
            ast.load.type = type;
            ast.load.name = name;
            ast.load.clazz = type;

            return ast;
        }else {
            th.toFirst();
            AST.Load load = parseCall();

            if (th.hasNext()) {
                th.assertToken("=");
                AST.Calc calc = parseCalc();
                th.assertNull();

                if(!load.type.equals(calc.type)) throw new RuntimeException(STR."Expected type \{load.type} got \{calc.type}");

                AST.VarAssignment ast = new AST.VarAssignment();
                ast.calc = calc;
                ast.load = load;
                ast.type = ast.calc.type;

                return ast;
            } else return load;
        }
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
                if(th.current().equals("true") || th.current().equals("false")){
                    ast.token = th.current();
                    ast.type = "boolean";
                }else{
                    ast.load = parseCall();
                }
            }
            default -> throw new RuntimeException("illegal argument");
        }

        return ast;
    }

    private AST.Load parseCall(){
        AST.Load ast = new AST.Load();
        AST.Call current;

        String call = th.assertToken(Token.Type.IDENTIFIER).s();
        if(scope.getType(call) != null){
            ast.name = call;
            ast.type = scope.getType(call);
            ast.clazz = ast.type;
            current = null;
        }else if(method.uses.containsKey(call)){
            String type = call;
            th.assertToken(".");
            call = th.assertToken(Token.Type.IDENTIFIER).s();

            if(CompilerUtil.getFieldType(type, call, true) == null) throw new RuntimeException(STR."static Field \{call} is not defined for class \{type}");

            ast.call = current = new AST.Call();
            current.type = CompilerUtil.getFieldType(type, call, true);
            current.clazz = type;
            current.call = call;
            current.statik = true;
            ast.finaly = CompilerUtil.isFinal(type, call);
            ast.type = current.type;
        }else{
            if(CompilerUtil.getFieldType(clazzName, call, true) != null){
                ast.call = current = new AST.Call();
                current.type = CompilerUtil.getFieldType(clazzName, call, true);
                current.clazz = clazzName;
                current.call = call;
                current.statik = true;
                ast.type = current.type;
            }else if(CompilerUtil.getFieldType(clazzName, call, false) != null){
                ast.call = current = new AST.Call();
                current.type = CompilerUtil.getFieldType(clazzName, call, false);
                current.clazz = clazzName;
                current.call = call;
                ast.call = current = current.toStatic();
                ast.type = current.type;
            }else throw new RuntimeException(STR."Field \{call} is not defined");

            ast.finaly = CompilerUtil.isFinal(clazzName, call);
        }

        while(th.hasNext()){
            if(!th.next().equals(".")){
                th.last();
                break;
            }

            String currentType;

            if(current == null){
                current = new AST.Call();
                ast.call = current;
                currentType = ast.type;
            }else{
                currentType = current.type;
                current.setPrev();
            }

            current.call = th.assertToken(Token.Type.IDENTIFIER).s();
            current.type = CompilerUtil.getFieldType(currentType, current.call, false);

            if(current.type == null) throw new RuntimeException(STR."Field \{current.call} is not defined for class \{currentType}");

            ast.finaly = CompilerUtil.isFinal(currentType, call);
            ast.type = current.type;
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
