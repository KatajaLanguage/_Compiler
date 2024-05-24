package com.github.ktj.compiler;

abstract sealed class AST permits AST.Calc, AST.Call, AST.If, AST.Return, AST.Value, AST.While {

    String type = null;

    static final class Calc extends AST{
        String opp = null;
        Value value = null;
        Calc left = null, right = null;

        public void setLeft(){
            Calc temp = new Calc();
            temp.left = left;
            temp.right = right;
            temp.opp = opp;
            temp.value = value;
            temp.type = type;

            left = temp;
            right = null;
            opp = null;
            value = null;
        }

        public void setRight(){
            Calc temp = new Calc();
            temp.right = right;
            temp.left = left;
            temp.opp = opp;
            temp.value = value;
            temp.type = type;

            right = temp;
            left = null;
            opp = null;
            value = null;
        }
    }

    static final class Value extends AST{
        Call call = null;
        String cast = null;
        Token token = null;
    }

    static sealed class Call extends AST permits StaticCall{
        String clazz = null;
        String call = null;
        Call next = null;
        Calc[] argTypes;
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
}
