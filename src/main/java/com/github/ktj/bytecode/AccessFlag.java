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

package com.github.ktj.bytecode;

public enum AccessFlag {
    ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_PACKAGE_PRIVATE;

    public static final int PUBLIC       = 0x0001;
    public static final int PRIVATE      = 0x0002;
    public static final int PROTECTED    = 0x0004;
    public static final int STATIC       = 0x0008;
    public static final int FINAL        = 0x0010;
    public static final int SUPER        = 0x0020;
    public static final int SYNCHRONIZED = 0x0020;
    public static final int VOLATILE     = 0x0040;
    public static final int BRIDGE       = 0x0040;
    public static final int TRANSIENT    = 0x0080;
    public static final int VARARGS      = 0x0080;
    public static final int NATIVE       = 0x0100;
    public static final int INTERFACE    = 0x0200;
    public static final int ABSTRACT     = 0x0400;
    public static final int STRICT       = 0x0800;
    public static final int SYNTHETIC    = 0x1000;
    public static final int ANNOTATION   = 0x2000;
    public static final int ENUM         = 0x4000;

    @Override
    public String toString() {
        switch(this){
            case ACC_PACKAGE_PRIVATE:
                return "package-private";
            case ACC_PUBLIC:
                return "public";
            case ACC_PRIVATE:
                return "private";
            case ACC_PROTECTED:
                return "protected";
        }

        return super.toString();
    }
}
