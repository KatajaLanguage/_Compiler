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

package com.github.ktj.lang;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjConstructor extends KtjMethod{

    public String superCall;

    public KtjConstructor(Modifier modifier, ArrayList<GenericType> generics, String returnType, String superCall, String code, Parameter[] parameter, HashMap<String, String> uses, ArrayList<String> statics, String file, int line) {
        super(modifier, generics, returnType, code, parameter, uses, statics, file, line);
        this.superCall = superCall;
    }

    public void validate(String superClass){
        if(!superCall.isEmpty()) {
            String type = validateType(superCall.split("\\(")[0], true);
            if (!type.equals(superClass))
                throw new RuntimeException("expected type " + superClass + " got " + type + " at " + file + ":" + line);
        }
    }
}
