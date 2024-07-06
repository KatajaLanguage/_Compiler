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

    static final class Load extends AST{
        String name = null;
        String clazz = null;
        Call call = null;
        boolean finaly = false;
    }

    static class Call extends AST{
        boolean statik = false;
        String clazz = null;
        String call = null;
        Call prev = null;
        Calc[] argTypes = null;

        void setPrev(){
            Call help = prev;
            prev = new Call();
            prev.prev = help;
            prev.call = call;
            prev.type = type;
            prev.clazz = clazz;
            prev.statik = statik;
            prev.argTypes = argTypes;

            statik = false;
            clazz = null;
            call = null;
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
}
