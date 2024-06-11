package com.github.ktj.compiler;

abstract sealed class AST permits AST.Calc, AST.Call, AST.If, AST.Load, AST.Return, AST.Value, AST.VarAssignment, AST.While {

    String type = null;

    static final class Calc extends AST{
        String op = null;
        Value value = null;
        Calc left = null, right = null;

        public void setLeft(){
            Calc temp = new Calc();
            temp.left = left;
            temp.right = right;
            temp.op = op;
            temp.value = value;
            temp.type = type;

            left = temp;
            right = null;
            op = null;
            value = null;
        }

        public void setRight(){
            Calc temp = new Calc();
            temp.right = right;
            temp.left = left;
            temp.op = op;
            temp.value = value;
            temp.type = type;

            right = temp;
            left = null;
            op = null;
            value = null;
        }
    }

    static final class Value extends AST{
        Call call = null;
        Load load = null;
        String cast = null;
        Token token = null;
    }

    static final class Load extends AST{
        String name = null;
        String clazz = null;
        Call call = null;
        boolean finaly = false;
    }

    static sealed class Call extends AST permits StaticCall {
        boolean statik = false;
        String clazz = null;
        String call = null;
        Call prev = null;
        Calc[] argTypes = null;

        void setPrev(){
            Call help = prev;
            prev = this instanceof StaticCall ? new StaticCall() : new Call();
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

        StaticCall toStatic(){
            StaticCall help = new StaticCall();

            help.statik = statik;
            help.clazz = clazz;
            help.type = type;
            help.call = call;
            help.prev = prev;
            help.argTypes = argTypes;

            return help;
        }
    }

    static final class StaticCall extends Call{
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
}
