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

package com.github.ktj.compiler;

import com.github.ktj.lang.*;

import java.util.ArrayList;
import java.util.HashMap;

final class SyntacticParser {

    private static final class Scope{
        public final Scope last;
        private final HashMap<String, String> vars = new HashMap<>();
        private final ArrayList<String> constants = new ArrayList<>();

        Scope(Scope current){
            last = current;
        }

        Scope(String type, KtjMethod method){
            last = null;
            vars.put("this", type);
            vars.put("null", "java.lang.Object");
            for(int i = 0;i < method.parameter.length;i++){
                if(vars.containsKey(method.parameter[i].name)) throw new RuntimeException(method.parameter[i].name+" is already defined at "+method.file+":"+method.line);
                vars.put(method.parameter[i].name, method.correctType(method.parameter[i].type));
                if(method.parameter[i].constant) constants.add(method.parameter[i].name);
            }
        }

        String getType(String name){
            if(vars.containsKey(name))
                return vars.get(name);

            return last == null ? null : last.getType(name);
        }

        boolean isConst(String name){
            if(constants.contains(name)) return true;

            return last != null && last.isConst(name);
        }

        void add(String name, String type, boolean constant){
            if(!vars.containsKey(name))
                vars.put(name, type);
            if(constant)
                constants.add(name);
        }
    }

    private HashMap<String, String> typeValues;
    private TokenHandler th;
    private KtjMethod method;
    private Scope scope;
    private String clazzName;
    private boolean isConstructor;

    AST[] parseAst(String clazzName, boolean isConstructor, KtjMethod method, String code){
        if(code.trim().isEmpty()) return new AST[]{CompilerUtil.getDefaultReturn(method.returnType)};

        this.method = method;
        this.clazzName = clazzName;
        this.isConstructor = isConstructor;
        this.th = Lexer.lex(code, clazzName, method.line);
        scope = new Scope(clazzName, method);

        setUpTypeValues();
        ArrayList<AST> ast = new ArrayList<>();

        while(th.hasNext()){
            AST e = parseNextStatement(false);
            if(e != null) ast.add(e);
            else if(th.isNext("}")) err("illegal statement");
        }

        if(ast.isEmpty() || !(ast.get(ast.size() - 1) instanceof AST.Return)){
            if(isConstructor) ast.add(CompilerUtil.getDefaultReturn("void"));
            else ast.add(CompilerUtil.getDefaultReturn(method.correctType(method.returnType)));
        }

        return ast.toArray(new AST[0]);
    }

    private AST parseNextStatement(boolean inLoop){
        if(th.isNext(";") || !th.hasNext()) return null;

        if(th.isNext("}")){
            th.last();
            return null;
        }

        AST ast;

        switch(th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR, "_").s){
            case "for":
                ast = parseFor();
                break;
            case "while":
                ast = parseWhile();
                break;
            case "do":
                ast = parseDoWhile();
                break;
            case "if":
                ast = parseIf(inLoop);
                break;
            case "break":
                if(!inLoop) err("Nothing to break");
                th.assertEndOfStatement();
                ast = new AST.Break();
                break;
            case "return":
                ast = parseReturn();
                break;
            case "throw":
                ast = parseThrow();
                break;
            case "switch":
                ast = parseSwitch();
                break;
            case "try":
                ast = parseTry(inLoop);
                break;
            default:
                th.last();
                ast = parseStatement();

                if(ast instanceof AST.Load && (((AST.Load)(ast)).call == null || ((AST.Load)(ast)).call.argTypes == null)) err("not a statement");
                break;
        }

        th.isNext(";");
        return ast;
    }

    private AST.Throw parseThrow(){
        AST.Throw ast = new AST.Throw();

        ast.calc = parseCalc();

        if(!CompilerUtil.isSuperClass(ast.calc.type, "java.lang.Throwable")) err("Expected type java.lang.Throwable got "+ast.calc.type);

        return ast;
    }

    private AST.Return parseReturn(){
        AST.Return ast = new AST.Return();

        if(th.hasNext()){
            ast.calc = parseCalc();
            ast.type = ast.calc.type;
            th.assertEndOfStatement();
        }else ast.type = "void";

        if(isConstructor){
            if(!ast.type.equals("void")) err("Expected type void got "+ast.type);
        }else if(!ast.type.equals(method.correctType(method.returnType))  && !(ast.type.equals("null") && !CompilerUtil.PRIMITIVES.contains(method.returnType))) err("Expected type "+method.returnType+" got "+ast.type);

        return ast;
    }

    private AST.While parseWhile(){
        AST.While ast = new AST.While();

        th.assertHasNext();
        ast.condition = parseCalc();

        if(!ast.condition.type.equals("boolean")) err("Expected type boolean got "+ast.condition.type);

        scope = new Scope(scope);

        if(th.assertToken("{", "->").equals("->")){
            ast.ast = new AST[]{parseNextStatement(true)};
        }else ast.ast = parseContent(true);

        scope = scope.last;

        return ast;
    }

    private AST.While parseDoWhile(){
        AST.While ast = new AST.While();
        ast.doWhile = true;
        th.assertToken("{");

        scope = new Scope(scope);
        ast.ast = parseContent(true);
        scope = scope.last;

        th.assertToken("while");
        ast.condition = parseCalc();
        th.assertEndOfStatement();

        if(!ast.condition.type.equals("boolean")) err("Expected type boolean got "+ast.condition.type);

        return ast;
    }

    private AST.If parseIf(boolean inLoop){
        AST.If ast = new AST.If();

        th.assertHasNext();
        ast.condition = parseCalc();
        if(!ast.condition.type.equals("boolean")) err("Expected type boolean got "+ast.condition.type);

        scope = new Scope(scope);

        if(th.assertToken("->", "{").equals("->")){
            ast.ast = new AST[]{parseNextStatement(inLoop)};

            if(th.hasNext()){
                String index = th.getIndex();
                if(th.isNext("else")) th.last();
                else{
                    th.setIndex(index);
                }
            }
        }else{
            ast.ast = parseContent(inLoop);
            if (!th.current().equals("}")) err("illegal argument");
        }

        scope = scope.last;

        AST.If current = ast;
        boolean end = false;
        while (th.hasNext() && !end) {
            current = (current.elif = new AST.If());
            th.assertToken("else");

            if (th.assertToken("if", "{", "->").equals("if")) {
                current.condition = parseCalc();
                if (!current.condition.type.equals("boolean"))
                    err("Expected type boolean got " + current.condition.type);
                th.assertToken("{", "->");
            }else end = true;

            if(th.current().equals("->")){
                scope = new Scope(scope);
                current.ast = new AST[]{parseNextStatement(inLoop)};
                scope = scope.last;

                if(th.hasNext()){
                    String index = th.getIndex();
                    if(th.isNext("else")) th.last();
                    else{
                        th.setIndex(index);
                    }
                }
            }else if(th.current().equals("{")){
                scope = new Scope(scope);

                current.ast = parseContent(inLoop);
                if (!th.current().equals("}")) err("illegal argument");

                scope = scope.last;
            }
        }

        return ast;
    }

    private AST.Switch parseSwitch(){
        AST.Switch ast = new AST.Switch();

        ast.calc = parseCalc();
        ast.type = ast.calc.type;
        th.assertToken("{");

        if(!(ast.type.equals("int") || ast.type.equals("short") || ast.type.equals("byte") || ast.type.equals("char") || CompilerUtil.isSuperClass(ast.type, "java.lang.Enum") || ast.type.equals("java.lang.String"))) err("illegal type "+ast.type);

        ArrayList<AST[]> branches = new ArrayList<>();
        while(th.hasNext()){
            if(!th.isNext(";")){
                if(th.isNext("}")) break;

                boolean defauld = false;

                do{
                    Token t = th.assertTokenTypes(Token.Type.INTEGER, Token.Type.SHORT, Token.Type.CHAR, Token.Type.IDENTIFIER, Token.Type.STRING);

                    if(t.equals("default")){
                        defauld = true;
                        break;
                    }

                    ast.values.put(t, branches.size());

                    if (!(t.t.toString().equals(ast.type) || (t.t == Token.Type.IDENTIFIER && CompilerUtil.getFieldType(ast.type, t.s, true, clazzName) != null))) err("Expected type " + ast.type + " got " + t.t.toString());
                }while(th.isNext(","));

                if(th.assertToken("->", "{").equals("->")){
                    branches.add(new AST[]{parseNextStatement(true)});
                }else{
                    branches.add(parseContent(true));
                    if (!th.current().equals("}")) err("illegal argument");
                }

                if(defauld){
                    if(ast.defauld != null) err("default is already defined");
                    ast.defauld = branches.get(branches.size() - 1);
                    branches.remove(branches.size() - 1);
                }
            }
        }

        ast.branches = branches.toArray(new AST[0][0]);
        if(!th.current().equals("}")) err("Expected }");

        return ast;
    }

    private AST.For parseFor(){
        AST.For ast = new AST.For();
        ast.variable = th.assertToken(Token.Type.IDENTIFIER).s;
        if(scope.getType(ast.variable) != null) err("Variable "+ast.variable+" is already defined");
        th.assertToken("in");
        if(th.isNext("{")){
            th.assertTokenTypes(Token.Type.INTEGER, Token.Type.SHORT, Token.Type.DOUBLE, Token.Type.FLOAT);
            Token.Type type = th.current().t;
            ast.from = th.current();
            th.assertToken(",");
            ast.step = th.assertToken(type);
            th.assertToken(".");
            th.assertToken(".");
            ast.to = parseCalc();
            if(!ast.to.type.equals(type.toString())) err("Expected type "+type+" got " + ast.to.type);
            ast.type = type.toString();
            th.assertToken("}");
        }else {
            ast.load = parseCall();
            if (!CompilerUtil.isSuperClass(ast.load.type, "java.lang.Iterable") && !ast.load.type.startsWith("["))
                err("Expected type java.lang.Iterable got " + ast.load.type);
            ast.type = ast.load.type;
        }

        scope.add(ast.variable, ast.type, false);

        if(th.assertToken("{", "->").equals("->")){
            ast.ast = new AST[]{parseNextStatement(true)};
        }else ast.ast = parseContent(true);

        return ast;
    }

    private AST.TryCatch parseTry(boolean inLoop){
        AST.TryCatch ast = new AST.TryCatch();

        scope = new Scope(scope);

        if(th.assertToken("->", "{").equals("->")) ast.tryAST = new AST[]{parseNextStatement(inLoop)};
        else{
            ast.tryAST = parseContent(inLoop);
            if (!th.current().equals("}")) err("illegal argument");
        }

        scope = scope.last;

        th.assertToken("catch");
        th.assertToken("(");
        ast.type = th.assertToken(Token.Type.IDENTIFIER).s;
        if(!method.uses.containsKey(ast.type)) err("Unknown type "+ast.type);
        ast.type = method.uses.get(ast.type);
        if(!CompilerUtil.isSuperClass(ast.type, "java.lang.Exception")) err("Expected type java.lang.Exception got "+ast.type);
        ast.variable = th.assertToken(Token.Type.IDENTIFIER).s;
        if(scope.getType(ast.variable) != null) err("Variable "+ast.variable+" is already defined");
        scope.add(ast.variable, ast.type, false);
        th.assertToken(")");

        scope = new Scope(scope);

        if(th.assertToken("->", "{").equals("->")){
            ast.catchAST = new AST[]{parseNextStatement(inLoop)};
        }else{
            ast.catchAST = parseContent(inLoop);
            if (!th.current().equals("}")) err("illegal argument");
        }

        scope = scope.last;
        return ast;
    }

    private AST[] parseContent(boolean inLoop){
        scope = new Scope(scope);
        ArrayList<AST> astList = new ArrayList<>();

        while(!th.isNext("}")){
            AST current = parseNextStatement(inLoop);
            if(current != null) astList.add(current);
        }

        if(!th.current().equals("}")) err("Expected }");

        scope = scope.last;
        return astList.toArray(new AST[0]);
    }

    private AST parseStatement(){
        if(!th.isNext(Token.Type.IDENTIFIER) && !th.isNext("_") && !th.isNext(Token.Type.OPERATOR)) err("illegal argument");

        boolean constant = th.current().equals("const");
        if(constant) th.assertToken(Token.Type.IDENTIFIER, "_");

        if(th.current().equals("_")){
            String name = th.assertToken(Token.Type.IDENTIFIER).s;
            th.assertToken("=");
            AST.Calc calc = parseCalc();
            th.assertEndOfStatement();

            if(scope.getType(name) != null) err("variable "+name+" is already defined");
            scope.add(name, calc.type, constant);

            AST.VarAssignment ast = new AST.VarAssignment();
            ast.calc = calc;
            ast.type = calc.type;
            ast.load = new AST.Load();
            ast.load.type = ast.type;
            ast.load.name = name;
            ast.load.clazz = ast.type;

            return ast;
        }else{
            if(th.current().equals(Token.Type.OPERATOR)){
                th.last();
                return parseAssignment();
            }else if(th.isNext(Token.Type.OPERATOR) || th.isNext(".") || th.isNext("(")){
                if(th.current().equals("<")){
                    th.last();
                }else{
                    if (constant) err("illegal argument");
                    th.last();
                    return parseAssignment();
                }
            }

            String i = th.getIndex();
            if(th.isNext("<")){
                do{
                    if(!(th.isNext(Token.Type.IDENTIFIER) && method.uses.containsKey(th.current().s))){
                        th.setIndex(i);
                        th.last();
                        return parseAssignment();
                    }
                }while(th.isNext(","));
                th.assertToken(">");
            }

            while(th.isNext("[")){
                if(!th.isNext("]")){
                    if(constant) err("illegal argument");
                    th.setIndex(i);
                    th.last();
                    return parseAssignment();
                }
            }

            th.setIndex(i);
            StringBuilder type = new StringBuilder(method.validateType(th.current().s, false));

            StringBuilder genericType = new StringBuilder();

            if(th.isNext("<")){
                do{
                    th.assertToken(Token.Type.IDENTIFIER);
                    if(genericType.length() != 0) genericType.append("%");
                    genericType.append(method.uses.get(th.current().s));
                }while(th.isNext(","));
                th.assertToken(">");

                if(!CompilerUtil.validateGenericTypes(type.toString(), genericType.toString().split("%"))) err("invalid generic Types "+genericType+" for class "+type);
            }

            while(th.isNext("[")){
                th.assertToken("]");
                type.insert(0, "[");
            }

            if(genericType.length() != 0) type.append("|").append(genericType);

            String name = th.assertToken(Token.Type.IDENTIFIER).s;
            th.assertToken("=");
            AST.Calc calc = parseCalc();
            th.assertEndOfStatement();

            if(CompilerUtil.PRIMITIVES.contains(type.toString())){
                if(!type.toString().equals(calc.type)) err("Expected type "+type+" got "+calc.type);
            }else if(!calc.type.equals("null")){
                if(!CompilerUtil.isSuperClass(calc.type, type.toString())) err("Expected type "+type+" got "+calc.type);
            }
            if(scope.getType(name) != null) err("variable "+name+" is already defined");

            scope.add(name, type.toString(), constant);

            AST.VarAssignment ast = new AST.VarAssignment();

            ast.calc = calc;
            ast.type = type.toString();
            ast.load = new AST.Load();
            ast.load.type = type.toString();
            ast.load.name = name;
            ast.load.clazz = type.toString();

            return ast;
        }
    }

    private AST parseAssignment(){
        if(th.current().equals(Token.Type.OPERATOR)) return parseCalc();

        th.last();
        String i = th.getIndex();
        AST.Load load = parseCall();
        if(load.finaly) th.assertEndOfStatement();

        if(th.isNext("=")){
            AST.Calc calc = parseCalc();
            th.assertEndOfStatement();

            if(load.call == null && load.name != null && scope.isConst(load.name)) err(load.name+" is constant and can't be modified");
            if(!load.type.equals(calc.type) && !(calc.type.equals("null") && !CompilerUtil.PRIMITIVES.contains(load.type)) && !CompilerUtil.isSuperClass(calc.type, load.type)) err("Expected type "+load.type+" got "+calc.type);

            AST.VarAssignment ast = new AST.VarAssignment();
            ast.calc = calc;
            ast.load = load;
            ast.type = ast.calc.type;

            return ast;
        }else if(th.hasNext()){
            th.setIndex(i);
            return parseCalc();
        }

        return load;
    }

    private AST.Calc parseCalc(){
        AST.Calc ast = new AST.Calc();

        if(th.isNext("(")){
            ast = parseCalc();
            th.assertToken(")");

            if(th.isNext(".")){
                AST.Call call = new AST.Call();
                call.calc = ast;
                call.type = ast.type;
                call = parseCallArg(call, call.type);

                AST.Value value = new AST.Value();
                value.load = new AST.Load();
                value.load.call = call;
                value.load.type = call.type;

                ast = new AST.Calc();
                ast.arg = value;
                ast.type = call.type;
            }
        }else{
            AST.CalcArg arg = parseValue();

            if(arg instanceof AST.Value && ((AST.Value) arg).op != null && CompilerUtil.PRIMITIVES.contains(arg.type)){
                if(((AST.Value) arg).op.equals("++") || ((AST.Value) arg).op.equals("--")){
                    String op = ((AST.Value) arg).op.substring(1);
                    ((AST.Value) arg).op = null;
                    ast.arg = arg;
                    ast.type = arg.type;
                    ast.setRight();
                    ast.op = "=";
                    ast.type = arg.type;
                    ast.left = new AST.Calc();
                    ast.left.type = ast.type;
                    ast.left.op = op;
                    AST.Value value = new AST.Value();
                    value.token = new Token("1", Token.Type.value(ast.type));
                    value.type = ast.type;
                    ast.left.arg = value;
                    ast.left.right = new AST.Calc();
                    ast.left.right.type = ast.type;
                    ast.left.right.arg = arg;
                }else{
                    ((AST.Value) arg).op = null;
                    ast.arg = arg;
                    ast.type = arg.type;
                    ast.setRight();
                    ast.type = "boolean";
                    ast.op = "!=";
                    AST.Value value = new AST.Value();
                    value.token = new Token("true", Token.Type.IDENTIFIER);
                    value.type = ast.type;
                    ast.arg = value;
                    ast.right = new AST.Calc();
                    ast.right.type = ast.type;
                    ast.right.arg = arg;
                }
            }else{
                ast.arg = arg;
                ast.type = arg.type;
            }
        }

        while(th.hasNext()){
            if((!th.next().equals(Token.Type.OPERATOR) || th.current().equals("->")) && !th.current().equals("?")){
                th.last();
                return ast;
            }

            if(th.current().equals("?")){
                AST.InlineIf arg = new AST.InlineIf();
                if(!ast.type.equals("boolean")) err("Expected type boolean got "+ast.type);
                arg.condition = ast;
                arg.trueValue = parseCalc();
                arg.type = arg.trueValue.type;
                th.assertToken(":");
                arg.falseValue = parseCalc();
                if(!arg.type.equals(arg.falseValue.type)) err("Expected type "+ast.type+" got "+arg.falseValue.type);

                ast = new AST.Calc();
                ast.arg = arg;
                ast.type = ast.arg.type;

                return ast;
            }

            ast.setRight();
            ast.op = th.current().s;

            if(ast.op.equals("=")){
                ast.left = parseCalc();
            }else if(CompilerUtil.PRIMITIVES.contains(ast.right.type) && (ast.op.equals("+=") || ast.op.equals("-=") || ast.op.equals("*=") || ast.op.equals("/=") || ast.op.equals("%="))){
                String op = ast.op.substring(0, 1);
                ast.op = "=";
                AST.Calc left = new AST.Calc();
                left.arg = ast.right.arg;
                left.type = ast.type;
                left.setRight();
                left.op = op;
                left.type = ast.type;
                left.left = parseCalc();
                ast.left = left;
            }else if(ast.op.equals(">>") && !CompilerUtil.PRIMITIVES.contains(ast.right.type) && !CompilerUtil.PRIMITIVES.contains(th.assertToken(Token.Type.IDENTIFIER).s)){
                AST.Value value = new AST.Value();

                value.token = th.current();
                if(!method.uses.containsKey(value.token.s)) err("Class "+value.token.s+" is not defined");
                value.token = new Token(method.uses.get(value.token.s), null);

                ast.arg = value;
                ast.type = "boolean";
            }else{
                if(th.isNext("(")){
                    ast.left = parseCalc();
                    th.assertToken(")");

                    if(th.isNext(".")){
                        AST.Call call = new AST.Call();
                        call.calc = ast.left;
                        call.type = ast.left.type;
                        call = parseCallArg(call, call.type);

                        AST.Value value = new AST.Value();
                        value.load = new AST.Load();
                        value.load.call = call;
                        value.load.type = call.type;

                        ast.left = new AST.Calc();
                        ast.left.arg = value;
                        ast.left.type = call.type;
                    }else {
                        String[] methodSpecs = CompilerUtil.getOperatorReturnType(ast.right.type, ast.left.type, ast.op);
                        if (methodSpecs == null)
                            err("Operator " + ast.op + " is not defined for " + ast.right.type + " and " + ast.left.type);
                        ast.type = methodSpecs[0];
                        ast.op = methodSpecs[1];
                    }
                }else{
                    AST.CalcArg arg = parseValue();

                    if(arg instanceof AST.Value && ((AST.Value) arg).op != null && CompilerUtil.PRIMITIVES.contains(arg.type)){
                        if(((AST.Value) arg).op.equals("++") || ((AST.Value) arg).op.equals("--")){
                            String op = ((AST.Value) arg).op.substring(1);
                            ((AST.Value) arg).op = null;
                            AST.Calc left = new AST.Calc();
                            left.arg = arg;
                            left.type = arg.type;
                            left.setRight();
                            left.op = "=";
                            left.type = arg.type;
                            left.left = new AST.Calc();
                            left.left.type = left.type;
                            left.left.op = op;
                            AST.Value value = new AST.Value();
                            value.token = new Token("1", Token.Type.value(left.type));
                            value.type = left.type;
                            left.left.arg = value;
                            left.left.right = new AST.Calc();
                            left.left.right.type = left.type;
                            left.left.right.arg = arg;

                            ast.left = left;
                            ast.type = arg.type;
                        }else{
                            ((AST.Value) arg).op = null;
                            AST.Calc left = new AST.Calc();
                            left.arg = arg;
                            left.type = arg.type;
                            left.setRight();
                            left.type = "boolean";
                            left.op = "!=";
                            AST.Value value = new AST.Value();
                            value.token = new Token("true", Token.Type.IDENTIFIER);
                            value.type = left.type;
                            left.arg = value;
                            left.right = new AST.Calc();
                            left.right.type = left.type;
                            left.right.arg = arg;

                            ast.left = left;
                            ast.type = "boolean";
                        }
                    }else{
                        ast.arg = arg;
                        String[] methodSpecs = CompilerUtil.getOperatorReturnType(ast.right.type, ast.arg.type, ast.op);
                        if(methodSpecs == null) err("Operator "+ast.op+" is not defined for "+ast.right.type+" and "+ast.arg.type);
                        ast.type = methodSpecs[0];
                        ast.op = methodSpecs[1];
                    }
                }
            }

            if(ast.type == null) err("Operator "+ast.op+" is not defined for "+ast.right.type+" and "+ast.arg.type);
        }

        return ast;
    }

    private AST.CalcArg parseValue(){
        th.next();
        String index = th.getIndex();

        if(th.current().equals(Token.Type.IDENTIFIER) && (CompilerUtil.PRIMITIVES.contains(th.current().s) || method.uses.containsKey(th.current().s))){
            String type = CompilerUtil.PRIMITIVES.contains(th.current().s) ? th.current().s : method.uses.get(th.current().s);
            if(th.isNext("[")){
                if(th.isNext("]")){
                    type = "["+type;
                }else type = null;
            }

            if(type != null) {
                AST.Cast ast = new AST.Cast();

                ast.cast = type;
                ast.calc = parseCalc();
                ast.type = ast.cast;

                if (!CompilerUtil.canCast(ast.calc.type, ast.cast))
                    err("Unable to cast " + ast.calc.type + " to " + ast.cast);

                return ast;
            }
        }

        th.setIndex(index);
        AST.Value ast = new AST.Value();

        switch(th.current().t){
            case CHAR:
            case SHORT:
            case INTEGER:
            case LONG:
            case DOUBLE:
            case FLOAT:
                ast.token = th.current();
                ast.type = th.current().t.toString();
                break;
            case IDENTIFIER:
                if(th.current().equals("true") || th.current().equals("false")){
                    ast.token = th.current();
                    ast.type = "boolean";
                }else if(th.current().equals("null")){
                    ast.token = th.current();
                    ast.type = "null";
                }else{
                    if(typeValues.containsKey(th.current().s)){
                        if(th.isNext("(") || th.isNext(".")) th.last();
                        else{
                            String value = th.current().s;
                            String clazz = typeValues.get(th.current().s);
                            ast.type = clazz;
                            ast.load = new AST.Load();
                            ast.load.type = clazz;
                            ast.load.call = new AST.Call();
                            ast.load.call.type = clazz;
                            ast.load.call.name = value;
                            ast.load.call.clazz = clazz;
                            ast.load.call.statik = true;
                            break;
                        }
                    }
                    th.last();
                    ast.load = parseCall();
                    ast.type = ast.load.type;
                }
                break;
            case STRING:
                ast.token = th.current();
                ast.type = "java.lang.String";
                break;
            case SIMPLE:
                if(th.current().equals("{")) return parseArrayCreation();
                else err("illegal argument "+ th.current());
            case OPERATOR:
                if(th.current().equals("-") && (th.isNext(Token.Type.INTEGER) || th.isNext(Token.Type.DOUBLE) || th.isNext(Token.Type.LONG) || th.isNext(Token.Type.SHORT) || th.isNext(Token.Type.FLOAT))){
                    ast.token = new Token("-"+ th.current().s, th.current().t);
                    ast.type = th.current().t.toString();
                    break;
                }else{
                    String op = th.current().s;
                    AST.CalcArg arg = parseValue();

                    if(arg instanceof AST.Cast) err("Expected value");

                    ((AST.Value) arg).op = op;
                    String[] methodsSpecs = CompilerUtil.getOperatorReturnType(arg.type, op);
                    if(methodsSpecs == null) err("Operator "+op+" is not defined for "+arg.type);
                    arg.type = methodsSpecs[0];
                    ((AST.Value) arg).op = methodsSpecs[1];

                    return arg;
                }
            default:
                err("illegal argument "+ th.current());
        }

        return ast;
    }

    private AST.ArrayCreation parseArrayCreation(){
        if(th.isNext("}")) err("Expected values");
        ArrayList<AST.Calc> calcs = new ArrayList<>();

        AST.Calc calc = parseCalc();
        String type = calc.type;
        calcs.add(calc);

        while(th.assertToken(",", ".", "}").equals(",")){
            //if(th.isNext("{")) throw new RuntimeException("illegal argument");
            calc = parseCalc();
            calcs.add(calc);
            if(!calc.type.equals(type)) err("Expected type "+type+" got "+calc.type);
        }

        if(th.current().equals(".")){
            th.assertToken(".");
            if(calcs.size() != 2) err("Illegal number of arguments");
            calc = parseCalc();
            calcs.add(calc);
            if(!calc.type.equals(type)) err("Expected type "+type+" got "+calc.type);
            for(AST.Calc c:calcs) if(!c.isSingleValue()) err("illegal argument");
            switch(type){
                case "int":
                case "short":
                    int current = Integer.parseInt(((AST.Value)(calcs.get(0).arg)).token.s);
                    int step = Integer.parseInt(((AST.Value)(calcs.get(1).arg)).token.s) - current;
                    int end = Integer.parseInt(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increase = current < end;
                    if(step == 0 || increase != (step > 0)) err("illegal argument");
                    calcs = new ArrayList<>();
                    while(increase ? current <= end : current >= end){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(current), Token.Type.value(type));
                        calcs.add(calc);
                        current += step;
                    }
                    break;
                case "long":
                    long currentL = Long.parseLong(((AST.Value)(calcs.get(0).arg)).token.s);
                    long stepL = Long.parseLong(((AST.Value)(calcs.get(1).arg)).token.s) - currentL;
                    long endL = Long.parseLong(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increaseL = currentL < endL;
                    if(stepL == 0 || increaseL != (stepL > 0)) err("illegal argument");
                    calcs = new ArrayList<>();
                    while(increaseL ? currentL <= endL : currentL >= endL){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(currentL), Token.Type.LONG);
                        calcs.add(calc);
                        currentL += stepL;
                    }
                    break;
                case "double":
                    double currentD = Double.parseDouble(((AST.Value)(calcs.get(0).arg)).token.s);
                    double stepD = Double.parseDouble(((AST.Value)(calcs.get(1).arg)).token.s) - currentD;
                    double endD = Double.parseDouble(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increaseD = currentD < endD;
                    if(stepD == 0 || increaseD != (stepD > 0)) err("illegal argument");
                    calcs = new ArrayList<>();
                    while(increaseD ? currentD <= endD : currentD >= endD){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(currentD), Token.Type.DOUBLE);
                        calcs.add(calc);
                        currentD += stepD;
                    }
                    break;
                case "float":
                    float currentF = Float.parseFloat(((AST.Value)(calcs.get(0).arg)).token.s);
                    float stepF = Float.parseFloat(((AST.Value)(calcs.get(1).arg)).token.s) - currentF;
                    float endF = Float.parseFloat(((AST.Value)(calcs.get(2).arg)).token.s);
                    boolean increaseF = currentF < endF;
                    if(stepF == 0 || increaseF != (stepF > 0)) err("illegal argument");
                    calcs = new ArrayList<>();
                    while(increaseF ? currentF <= endF : currentF >= endF){
                        calc = new AST.Calc();
                        calc.arg = new AST.Value();
                        calc.type = type;
                        calc.arg.type = type;
                        ((AST.Value)(calc.arg)).token = new Token(String.valueOf(currentF), Token.Type.FLOAT);
                        calcs.add(calc);
                        currentF += stepF;
                    }
                    break;
                default:
                    err("expected number got "+type);
            }

            th.assertToken("}");
        }

        AST.ArrayCreation ast = new AST.ArrayCreation();
        ast.type = "["+type;
        ast.calcs = calcs.toArray(new AST.Calc[0]);
        return ast;
    }

    private AST.Load parseCall(){
        AST.Load ast = new AST.Load();

        String call = th.assertToken(Token.Type.IDENTIFIER).s;

        if(CompilerUtil.PRIMITIVES.contains(call) || method.uses.containsKey(call)){
            ast.call = new AST.Call();

            StringBuilder genericType = new StringBuilder();

            if(th.isNext("<")){
                if(CompilerUtil.PRIMITIVES.contains(call)) err("illegal argument");

                do{
                    th.assertToken(Token.Type.IDENTIFIER);
                    if(genericType.length() != 0) genericType.append("%");
                    genericType.append(method.uses.get(th.current().s));
                }while(th.isNext(","));
                th.assertToken(">");

                if(!CompilerUtil.validateGenericTypes(method.uses.get(call), genericType.toString().split("%"))) err("invalid generic Types "+genericType+" for class "+method.uses.get(call));
            }

            if(th.isNext("[")){
                th.last();

                ast.call.clazz = CompilerUtil.PRIMITIVES.contains(call) ? call : method.uses.get(call);
                ast.call.name = "<init>";

                ArrayList<AST.Calc> args = new ArrayList<>();

                while(th.isNext("[")){
                    args.add(parseCalc());
                    ast.call.clazz = "["+ast.call.clazz;

                    if(!args.get(args.size() - 1).type.equals("int")) err("Expected int got "+args.get(args.size() - 1).type);

                    th.assertToken("]");
                }

                ast.call.argTypes = args.toArray(new AST.Calc[0]);
                ast.call.type = ast.call.clazz;
                ast.type = ast.call.type;
            }else if(th.isNext("(")){
                if(CompilerUtil.PRIMITIVES.contains(call)) err("illegal type "+call);

                ast.call.clazz = method.uses.get(call);
                ast.call.name = "<init>";

                StringBuilder desc = new StringBuilder("<init>");
                ArrayList<AST.Calc> args = new ArrayList<>();

                if(!th.isNext(")")){
                    while(th.hasNext()){
                        args.add(parseCalc());
                        if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                        else break;
                    }
                }

                for (AST.Calc calc:args) desc.append("%").append(calc.type);

                String[] methodSpecs = CompilerUtil.getMethod(ast.call.clazz, false, desc.toString(), clazzName);
                if(methodSpecs == null)
                    err("Method "+desc+" is not defined for class "+ast.call.clazz);

                ast.call.type = methodSpecs[0];
                ast.call.signature = methodSpecs[1];
                ast.call.argTypes = args.toArray(new AST.Calc[0]);
                if(genericType.length() != 0) ast.call.type = ast.call.type+"|"+genericType;
                ast.type = ast.call.type;
            }else{
                if(CompilerUtil.PRIMITIVES.contains(call)) err("illegal type "+call);

                th.assertToken(".");
                ast.call.clazz = method.uses.get(call);

                call = th.assertToken(Token.Type.IDENTIFIER).s;

                if(th.isNext("(")) {
                    StringBuilder desc = new StringBuilder(call);
                    ArrayList<AST.Calc> args = new ArrayList<>();

                    if (!th.isNext(")")) {
                        while (th.hasNext()) {
                            args.add(parseCalc());
                            if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                            else break;
                        }
                    }

                    for (AST.Calc calc : args) desc.append("%").append(calc.type);

                    String[] methodSpecs = CompilerUtil.getMethod(ast.call.clazz, true, desc.toString(), clazzName);
                    if(methodSpecs == null)
                        err("Static Method "+desc+" is not defined for class "+ast.call.clazz);

                    ast.call.name = call;
                    ast.call.type = methodSpecs[0];
                    ast.call.signature = methodSpecs[1];
                    if(methodSpecs.length == 3) ast.call.cast = methodSpecs[2];
                    ast.type = ast.call.type;
                    ast.call.statik = true;
                    ast.call.argTypes = args.toArray(new AST.Calc[0]);
                }else{
                    ast.call.name = call;
                    String[] fieldSpecs = CompilerUtil.getFieldType(ast.call.clazz, call, true, clazzName);
                    if(fieldSpecs == null) err("Static Field "+call+" is not defined for class "+ ast.call.clazz);

                    ast.call.type = fieldSpecs[1] != null ? fieldSpecs[1] : fieldSpecs[0];
                    ast.call.cast = fieldSpecs[1];
                    ast.call.signature = fieldSpecs[0];

                    ast.call.statik = true;
                    ast.type = ast.call.type;
                }
            }
        }else{
            if(scope.getType(call) != null) {
                ast.name = call;
                ast.type = scope.getType(call);
                ast.clazz = ast.type;
            }else if(th.isNext("(")){
                ast.call = new AST.Call();
                ast.call.clazz = clazzName;
                ast.call.name = call;

                StringBuilder desc = new StringBuilder(call);
                ArrayList<AST.Calc> args = new ArrayList<>();

                if (!th.isNext(")")) {
                    while (th.hasNext()) {
                        args.add(parseCalc());
                        if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                        else break;
                    }
                }

                for (AST.Calc calc : args) desc.append("%").append(calc.type);

                String[] methodSpecs = CompilerUtil.getMethod(ast.call.clazz, false, desc.toString(), clazzName);

                if(methodSpecs == null){
                    methodSpecs = CompilerUtil.getMethod(ast.call.clazz, true, desc.toString(), clazzName);
                    ast.call.statik = true;
                }

                if(methodSpecs == null){
                    ast.call.clazz = getClazzFromMethod(desc.toString());
                    if(ast.call.clazz == null)
                        err("Method " + desc + " is not defined for class " + clazzName);
                    methodSpecs = CompilerUtil.getMethod(ast.call.clazz, true, desc.toString(), clazzName);
                    ast.call.statik = true;
                }

                if(methodSpecs == null) err("Method "+desc+" is not defined for Class "+ast.call.clazz);

                ast.call.type = methodSpecs[0];
                ast.call.signature = methodSpecs[1];
                if(methodSpecs.length == 3) ast.call.cast = methodSpecs[2];
                ast.call.argTypes = args.toArray(new AST.Calc[0]);
                ast.type = ast.call.type;
            }else{
                ast.call = new AST.Call();
                ast.call.clazz = clazzName;

                ast.call.name = call;
                String[] fieldSpecs = CompilerUtil.getFieldType(clazzName, call, false, clazzName);

                if(fieldSpecs == null){
                    fieldSpecs = CompilerUtil.getFieldType(clazzName, call, true, clazzName);
                    ast.call.statik = true;
                }

                if(fieldSpecs == null){
                    ast.call.clazz = getClazzFromField(call);
                    if(ast.call.clazz == null)
                        err("Field " + call + " is not defined for class " + clazzName);
                    fieldSpecs = CompilerUtil.getFieldType(ast.call.clazz, call, true, clazzName);
                    ast.call.statik = true;
                }

                assert fieldSpecs != null;
                ast.call.type = fieldSpecs[1] != null ? fieldSpecs[1] : fieldSpecs[0];
                ast.call.cast = fieldSpecs[1];
                ast.call.signature = fieldSpecs[0];
                ast.type = ast.call.type;
            }

            while(th.isNext("[")){
                if(ast.call == null){
                    ast.call = new AST.Call();
                    ast.call.clazz = ast.type;
                }else {
                    if (!ast.call.type.startsWith("["))
                        err("Expected array got " + ast.call.type);

                    ast.call.setPrev();
                    ast.call.clazz = ast.call.prev.type;
                }

                ast.call.type = ast.call.clazz.substring(1);
                ast.call.argTypes = new AST.Calc[]{parseCalc()};
                ast.call.name = "";
                th.assertToken("]");

                if(!ast.call.argTypes[0].type.equals("int")) err("Expected type int got "+ast.call.argTypes[0].type);
            }

            if(ast.call != null) ast.type = ast.call.type;
        }

        if(th.isNext(".")) ast.call = parseCallArg(ast.call, ast.type);

        if(ast.call != null) ast.type = ast.call.type;

        return ast;
    }

    private AST.Call parseCallArg(AST.Call call, String currentClass){
        if(call != null) call.setPrev();
        else call = new AST.Call();

        String name = th.assertToken(Token.Type.IDENTIFIER).s;
        call.clazz = currentClass;

        if(th.isNext("(")){
            StringBuilder desc = new StringBuilder(name);
            ArrayList<AST.Calc> args = new ArrayList<>();

            if(!th.isNext(")")){
                while (th.hasNext()) {
                    args.add(parseCalc());
                    if (th.assertToken(",", ")").equals(",")) th.assertHasNext();
                    else break;
                }
            }

            for (AST.Calc calc:args) desc.append("%").append(calc.type);

            call.argTypes = args.toArray(new AST.Calc[0]);

            String[] methodSpecs = CompilerUtil.getMethod(call.clazz, false, desc.toString(), clazzName);
            if(methodSpecs == null)
                err("Method "+desc+" is not defined for class "+currentClass);

            call.name = name;
            call.type = methodSpecs[0];
            call.signature = methodSpecs[1];
            if(methodSpecs.length == 3) call.cast = methodSpecs[2];
        }else{
            call.name = name;
            String[] fieldSpecs = CompilerUtil.getFieldType(call.clazz, name, false, clazzName);

            if(fieldSpecs == null) err("Field "+name+" is not defined for class "+currentClass);

            call.cast = fieldSpecs[1];
            call.signature = fieldSpecs[0];
            call.type = call.cast == null ? call.signature : call.cast;
        }

        while(th.isNext("[")){
            if(!call.type.startsWith("[")) err("Expected array got "+call.type);

            call.setPrev();
            call.clazz = call.prev.type;
            call.type = call.clazz.substring(1);
            call.argTypes = new AST.Calc[]{parseCalc()};
            call.name = "";
            th.assertToken("]");

            if(!call.argTypes[0].type.equals("int")) err("Expected type int got "+call.argTypes[0].type);
        }

        if(th.isNext(".")) call = parseCallArg(call, call.type);

        return call;
    }

    private String getClazzFromMethod(String method){
        for(String name:this.method.statics){
            if(CompilerUtil.getMethod(this.method.uses.get(name), true, method, clazzName) != null) return this.method.uses.get(name);
        }
        return null;
    }

    private String getClazzFromField(String field){
        for(String name:method.statics){
            String[] type = CompilerUtil.getFieldType(method.uses.get(name), field, true, clazzName);
            if(type != null) return method.uses.get(name);
        }
        return null;
    }

    private void setUpTypeValues(){
        typeValues = new HashMap<>();

        for(String clazz:method.uses.values()) if(CompilerUtil.isType(clazz)) for(String type:CompilerUtil.getTypes(clazz)) if(!typeValues.containsKey(type)) typeValues.put(type, clazz);
    }

    private void err(String message) throws ParsingException{
        throw new ParsingException(message, clazzName, th.getLine());
    }
}
