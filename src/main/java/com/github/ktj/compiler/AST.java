package com.github.ktj.compiler;

abstract sealed class AST permits AST.Value{

    String type = null;

    static final class Value extends AST{
        Token token = null;
    }
}
