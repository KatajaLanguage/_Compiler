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

import com.github.ktj.bytecode.AccessFlag;

import java.util.ArrayList;
import java.util.HashMap;

public class KtjObject extends KtjClass{
    public KtjObject(Modifier modifier, HashMap<String, String> uses, ArrayList<String> statics, String file, int line) {
        super(modifier, null, uses, statics, file, line);
    }

    @Override
    public void validateInit(String className) {
        boolean initExist = false;

        for(String method: methods.keySet()) {
            if(method.startsWith("<init>")) {
                initExist = true;
                break;
            }
        }

        if(!initExist){
            methods.put("<init>", new KtjMethod(new Modifier(AccessFlag.ACC_PRIVATE), null,className, "", new KtjMethod.Parameter[0], uses, statics, file, Integer.MIN_VALUE));
        }
    }
}
