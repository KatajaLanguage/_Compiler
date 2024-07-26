package com.github.ktj.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

final class Lexer{

    private final Token[][] token;
    private final String file;
    private int line;
    private int i;

    Lexer(File file) throws FileNotFoundException{
        this.file = file.getPath()+"/"+file.getName();
        token = lex(file);
        line = 0;
        i = -1;
    }

    private Token[][] lex(File file) throws FileNotFoundException{
        List<Token[]> result = new ArrayList<>();
        List<Token> token = new ArrayList<>();

        Scanner sc = new Scanner(file);
        List<Character> chars = new ArrayList<>();
        StringBuilder value;
        line = 0;

        while(sc.hasNextLine()){
            for(char c:sc.nextLine().trim().toCharArray()) chars.add(c);
            chars.add('\n');
        }

        for(int i = 0; i < chars.size();i++){
            if(chars.get(i) == '\n'){
                result.add(token.toArray(new Token[0]));
                token = new ArrayList<>();
                line++;
            }else if(chars.get(i) == '#'){
                i++;
                while (i < chars.size() && chars.get(i) != '#' && chars.get(i) != '\n') i++;
            }else if(Character.isDigit(chars.get(i))){
                value = new StringBuilder();
                value.append(chars.get(i));
                Token.Type type;
                while(i + 1 < chars.size() && Character.isDigit(chars.get(i + 1))){
                    i++;
                    value.append(chars.get(i));
                }
                if(i + 1 < chars.size() && chars.get(i + 1) == '.'){
                    i++;
                    value.append(chars.get(i));
                    while(i + 1 < chars.size() && Character.isDigit(chars.get(i + 1))){
                        i++;
                        value.append(chars.get(i));
                    }
                    type = Token.Type.DOUBLE;
                }else type = Token.Type.INTEGER;

                if(i + 1 < chars.size() && chars.get(i + 1) == 'f') {
                    type = Token.Type.FLOAT;
                    i++;
                    value.append("f");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 'd'){
                    type = Token.Type.DOUBLE;
                    i++;
                    value.append("d");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 'i'){
                    type = Token.Type.INTEGER;
                    i++;
                    value.append("i");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 's'){
                    type = Token.Type.SHORT;
                    i++;
                    value.append("s");
                }else if(i + 1 < chars.size() && chars.get(i + 1) == 'l'){
                    type = Token.Type.LONG;
                    i++;
                    value.append("i");
                }

                token.add(new Token(value.toString(), type));
            }else if(Character.isLetter(chars.get(i))){
                value = new StringBuilder();
                value.append(chars.get(i));
                while(i + 1 < chars.size() && (Character.isDigit(chars.get(i + 1)) || Character.isLetter(chars.get(i + 1)) || chars.get(i + 1) == '_')){
                    i++;
                    value.append(chars.get(i));
                }
                token.add(new Token(value.toString(), Token.Type.IDENTIFIER));
            }else if(isOperator(chars.get(i))){
                value = new StringBuilder();
                value.append(chars.get(i));
                while(i + 1 < chars.size() && isOperator(chars.get(i + 1))){
                    i++;
                    value.append(chars.get(i + 1));
                }
                token.add(new Token(value.toString(), Token.Type.OPERATOR));
            }else if(chars.get(i) == '"'){
                value = new StringBuilder();
                value.append("\"");
                while(chars.get(i + 1) != '"'){
                    i++;
                    value.append(chars.get(i));
                }
                value.append("\"");
                i++;
                if(chars.get(i) != '"') throw new LexingException("Expected \"", this.file, line);
                token.add(new Token(value.toString(), Token.Type.STRING));
            }else if(chars.get(i) == '\''){
                if(chars.size() >= i + 2 && chars.get(i + 2) == '\''){
                    token.add(new Token("'"+chars.get(i + 1)+"'", Token.Type.CHAR));
                    i += 3;
                }else throw new LexingException("Expected '", this.file, line);
            }else if(!(chars.get(i) == '\r' || chars.get(i) == '\t' || chars.get(i) == ' ')) token.add(new Token(String.valueOf(chars.get(i)), Token.Type.SIMPLE));
        }

        return result.toArray(new Token[0][0]);
    }

    public static boolean isOperator(char c){
        return String.valueOf(c).matches("[-+*/!=<>%&|^~]");
    }
}
