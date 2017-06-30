/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.psi

import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.LuaDocClassDef
import com.tang.intellij.lua.lang.type.LuaTableType


fun LuaAssignStat.getExprAt(index:Int) : LuaExpr? {
    val list = this.varExprList.exprList
    return list[index]
}

fun LuaExprList.getExprAt(idx: Int): LuaExpr? {
    return exprList[idx]
}

fun LuaLocalDef.getExprFor(nameDef: LuaNameDef): LuaExpr? {
    val nameList = this.nameList
    nameList ?: return null
    val exprList = this.exprList
    exprList ?: return null

    var next = nameList.firstChild
    var idx = 0
    var found = false
    while (next != null) {
        if (next is LuaNameDef) {
            if (next == nameDef) {
                found = true
                break
            }
            idx++
        }
        next = next.nextSibling
    }
    if (!found) return null
    return exprList.getExprAt(idx)
}

val LuaParamNameDef.funcBodyOwner: LuaFuncBodyOwner?
    get() = PsiTreeUtil.getParentOfType(this, LuaFuncBodyOwner::class.java)

val LuaParamNameDef.owner: LuaParametersOwner
    get() = PsiTreeUtil.getParentOfType(this, LuaParametersOwner::class.java)!!

enum class LuaLiteralKind {
    String,
    Bool,
    Number,
    Nil,
    Unknown
}

val LuaLiteralExpr.kind: LuaLiteralKind get() = when(node.firstChildNode.elementType) {
    LuaTypes.STRING -> LuaLiteralKind.String
    LuaTypes.TRUE -> LuaLiteralKind.Bool
    LuaTypes.FALSE -> LuaLiteralKind.Bool
    LuaTypes.NIL -> LuaLiteralKind.Nil
    LuaTypes.NUMBER -> LuaLiteralKind.Number
    else -> LuaLiteralKind.Unknown
}

val LuaDocClassDef.aliasName: String? get() {
    val owner = LuaCommentUtil.findOwner(this)
    when (owner) {
        is LuaAssignStat -> {
            val expr = owner.getExprAt(0)
            if (expr != null) return expr.text
        }

        is LuaLocalDef -> {
            val expr = owner.exprList?.getExprAt(0)
            if (expr is LuaTableExpr)
                return LuaTableType.getTypeName(expr)
        }
    }
    return null
}

val LuaIndexExpr.prefixExpr: LuaExpr get() {
    return firstChild as LuaExpr
}

val LuaTableField.shouldCreateStub: Boolean get() {
    id ?: return false

    val tableExpr = PsiTreeUtil.getStubOrPsiParentOfType(this, LuaTableExpr::class.java)
    tableExpr ?: return false
    return tableExpr.shouldCreateStub
}

val LuaTableExpr.shouldCreateStub: Boolean get() {
    return true
}