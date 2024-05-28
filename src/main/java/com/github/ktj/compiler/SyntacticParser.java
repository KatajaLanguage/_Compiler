package com.github.ktj.compiler;

import com.github.ktj.lang.Compilable;
import com.github.ktj.lang.KtjMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

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
            try {
                ast.addAll(parseNext());
            } catch (RuntimeException e) {
                RuntimeException err = new RuntimeException(STR."\{e.getMessage()} at \{clazz.file}:\{clazz.line}");
                err.setStackTrace(e.getStackTrace());
                throw err;
            }
            index++;
        }

        return ast.toArray(new AST[0]);
    }

    private ArrayList<AST> parseNext(){
        ArrayList<AST> ast = new ArrayList<>();
        nextLine();

        if(!th.isEmpty()) ast.addAll(parseNextLine());

        return ast;
    }

    private AST.If parseIf(){
        AST.If statement = new AST.If();

        statement.condition = parseCalc();

        if(!statement.condition.type.equals("boolean"))
            throw new RuntimeException(STR."Expected boolean got \{statement.condition.type}");

        th.assertToken("{");

        ArrayList<AST> ast = new ArrayList<>();
        scope = new Scope(scope);

        nextLine();
        while (!th.toStringNonMarked().startsWith("} ")){
            if(!hasNext()) throw new RuntimeException("Expected Statement");
            ast.addAll(parseNext());
            nextLine();
        }

        scope = scope.last;
        statement.ast = ast.toArray(new AST[0]);

        if(th.toStringNonMarked().startsWith("} else ")){
            th.assertToken("}");
            th.assertToken("else");

            if(th.assertToken("{", "if").equals("{")){
                ast = new ArrayList<>();
                scope = new Scope(scope);
                nextLine();
                while (!th.toStringNonMarked().startsWith("} ")){
                    if(!hasNext()) throw new RuntimeException("Expected Statement");
                    ast.addAll(parseNext());
                    nextLine();
                }

                th.assertToken("}");
                th.assertNull();

                AST.If elseCase = new AST.If();
                elseCase.ast = ast.toArray(new AST[0]);
                scope = scope.last;

                statement.elif = elseCase;
            }else{
                th.assertToken("if");
                statement.elif = parseIf();
            }
        }

        return statement;
    }

    private AST.While parseWhile(){
        AST.While statement = new AST.While();
        statement.condition = parseCalc();

        if(!statement.condition.type.equals("boolean"))
            throw new RuntimeException(STR."Expected boolean got \{statement.condition.type}");

        ArrayList<AST> ast = new ArrayList<>();

        nextLine();
        while (!th.toStringNonMarked().startsWith("} ")){
            if(!hasNext()) throw new RuntimeException("Expected Statement");
            ast.addAll(parseNext());
            nextLine();
        }

        th.assertToken("}");
        th.assertNull();

        statement.ast = ast.toArray(new AST[0]);

        return statement;
    }

    private AST.Return parseReturn(){
        AST.Return ast = new AST.Return();
        ast.calc = parseCalc();
        ast.type = ast.calc.type;

        if(!ast.type.equals(method.returnType)) throw new RuntimeException(STR."Expected type \{method.returnType} got \{ast.type}");

        return ast;
    }

    private ArrayList<AST> parseNextLine(){
        ArrayList<AST> ast = new ArrayList<>();
        while (th.hasNext()){
            ast.add(parseStatement());
            if(th.hasNext()) th.assertToken(";");
        }
        return ast;
    }

    private AST parseStatement(){
        return switch(th.assertToken(Token.Type.IDENTIFIER).s()){
            case "if" -> parseIf();
            case "while" -> parseWhile();
            case "return" -> parseReturn();
            default -> null;
        };
    }

    private AST.Calc parseCalc(){
        AST.Calc calc;

        if(th.next().equals("(")){
            calc = parseCalc();
            th.assertToken(")");
        }else{
            th.last();
            calc = new AST.Calc();
            calc.value = parseValue();
            calc.type = calc.value.type;
        }

        while(th.hasNext()){
            if(th.next().t() != Token.Type.OPERATOR || th.current().equals("->")){
                th.last();
                return calc;
            }

            calc.setRight();
            calc.opp = th.current().s();
            th.assertHasNext();

            if(th.next().equals("(")){
                calc.left = parseCalc();
                th.assertToken(")");

                String returnType = CompilerUtil.getOperatorReturnType(calc.right.type, calc.left.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException(STR."Operator \{calc.opp} is not defined for \{calc.left.type} and \{calc.value.type}");

                calc.type = returnType;
            }else{
                th.last();
                calc.value = parseValue();

                String returnType = CompilerUtil.getOperatorReturnType(calc.right.type, calc.value.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException(STR."Operator \{calc.opp} is not defined for \{calc.right.type} and \{calc.value.type}");

                calc.type = returnType;
            }
        }

        return calc;
    }

    private AST.Value parseValue(){
        AST.Value value = new AST.Value();

        switch (th.next().t()){
            case INTEGER, DOUBLE, FLOAT, LONG, SHORT, CHAR -> {
                value.token = th.current();
                value.type = th.current().t().toString();
            }
            case IDENTIFIER -> {
                if(th.current().equals("true") || th.current().equals("false")){
                    value.token = th.current();
                    value.type = "boolean";
                }else throw new RuntimeException("illegal argument");
            }
            default -> throw new RuntimeException("illegal argument");
        }

        return value;
    }

    private void nextLine(){
        clazz.line++;
        th = Lexer.lex(code[index++]);
    }

    private boolean hasNext(){
        return index < code.length - 1;
    }
}
