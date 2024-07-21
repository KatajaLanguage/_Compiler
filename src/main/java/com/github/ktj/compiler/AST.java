package com.github.ktj.compiler;

import java.util.HashMap;

abstract class AST {

    String type = null;

    static final class Calc extends AST{
        String op = null;
        CalcArg arg = null;
        Calc left = null, right = null;

        public void setLeft(){
            Calc temp = new Calc();
            temp.left = left;
            temp.right = right;
            temp.op = op;
            temp.arg = arg;
            temp.type = type;

            left = temp;
            right = null;
            op = null;
            arg = null;
        }

        public void setRight(){
            Calc temp = new Calc();
            temp.right = right;
            temp.left = left;
            temp.op = op;
            temp.arg = arg;
            temp.type = type;

            right = temp;
            left = null;
            op = null;
            arg = null;
        }

        public boolean isSingleValue(){
            return left == null && right == null && op == null && arg instanceof AST.Value && ((Value) arg).op == null && ((Value) arg).token != null;
        }
    }

    static class CalcArg extends AST{}

    static final class Value extends CalcArg{
        String op = null;
        Load load = null;
        Token token = null;
    }

    static final class Cast extends CalcArg{
        Calc calc = null;
        String cast = null;
    }

    static final class ArrayCreation extends CalcArg{
        Calc[] calcs = null;
    }

    static class InlineIf extends CalcArg{
        Calc condition = null;
        Calc trueValue = null;
        Calc falseValue = null;
    }

    static final class Load extends AST{
        String name = null;
        String clazz = null;
        Call call = null;
        boolean finaly = false;
    }

    static class Call extends AST{
        boolean statik = false;
        String clazz = null;
        String name = null;
        String signature = null;
        Call prev = null;
        Calc[] argTypes = null;

        void setPrev(){
            Call help = prev;
            prev = new Call();
            prev.prev = help;
            prev.name = name;
            prev.type = type;
            prev.clazz = clazz;
            prev.statik = statik;
            prev.argTypes = argTypes;
            prev.signature = signature;

            statik = false;
            clazz = null;
            name = null;
            signature = null;
            argTypes = null;
            type = null;
        }
    }

    static final class Return extends AST{
        Calc calc = null;
    }

    static final class If extends AST{
        Calc condition = null;
        If elif = null;
        AST[] ast = null;
    }

    static final class While extends AST{
        boolean doWhile = false;
        Calc condition = null;
        AST[] ast = null;
    }

    static final class VarAssignment extends AST{
        Calc calc = null;
        Load load = null;
    }

    static final class Throw extends AST{
        Calc calc = null;
    }

    static final class Switch extends AST{
        HashMap<Token, Integer> values = new HashMap<>();
        AST[][] branches = null;
        AST[] defauld = null;
        Calc calc = null;
    }

    static class For extends AST{
        String variable = null;
        Load load = null;
        AST[] ast = null;
        Token from = null;
        Calc to = null;
        Token step = null;
    }

    static class Break extends AST{}

    static class TryCatch extends AST{
        AST[] tryAST = null;
        AST[] catchAST = null;
        String variable = null;
    }
}
