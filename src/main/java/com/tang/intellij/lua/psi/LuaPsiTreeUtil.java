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

package com.tang.intellij.lua.psi;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.tang.intellij.lua.Constants;
import com.tang.intellij.lua.comment.LuaCommentUtil;
import com.tang.intellij.lua.comment.psi.LuaDocFunctionTy;
import com.tang.intellij.lua.comment.psi.LuaDocGenericDef;
import com.tang.intellij.lua.comment.psi.LuaDocPsiElement;
import com.tang.intellij.lua.comment.psi.LuaDocTagClass;
import com.tang.intellij.lua.comment.psi.api.LuaComment;
import com.tang.intellij.lua.psi.search.LuaShortNamesManager;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.ty.ITy;
import com.tang.intellij.lua.ty.ITyClass;
import com.tang.intellij.lua.ty.Ty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 * Created by tangzx on 2016/12/3.
 */
public class LuaPsiTreeUtil {

    public static void walkUpLabel(PsiElement current, Processor<LuaLabelStat> processor) {
        PsiElement prev = current.getPrevSibling();
        while (true) {
            if (prev == null)
                prev = current.getParent();
            if (prev == null || prev instanceof PsiFile)
                break;
            if (prev instanceof LuaLabelStat && !processor.process((LuaLabelStat) prev))
                break;
            current = prev;
            prev = prev.getPrevSibling();
        }
    }

    public static <T extends PsiElement> void walkTopLevelInFile(PsiElement element, Class<T> cls, Processor<T> processor) {
        if (element == null || processor == null)
            return;
        PsiElement parent = element;
        while (!(parent.getParent() instanceof PsiFile))
            parent = parent.getParent();

        for(PsiElement child = parent; child != null; child = child.getPrevSibling()) {
            if (cls.isInstance(child)) {
                if (!processor.process(cls.cast(child))) {
                    break;
                }
            }
        }
    }

    @Nullable
    public static <T extends PsiElement> T findElementOfClassAtOffset(@NotNull PsiFile file, int offset, @NotNull Class<T> clazz, boolean strictStart) {
        T t = PsiTreeUtil.findElementOfClassAtOffset(file, offset, clazz, strictStart);
        if (t == null)
            t = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, clazz, strictStart);
        return t;
    }

    public static <T extends PsiElement> T findAncestorOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, @NotNull Class... skips) {
        if (element == null) {
            return null;
        } else {
            element = element.getParent();

            while (element != null && (!aClass.isInstance(element) || PsiTreeUtil.instanceOf(element, skips))) {
                if (element instanceof PsiFile) {
                    return null;
                }

                element = element.getParent();
            }

            return aClass.cast(element);
        }
    }

    @Nullable
    private static LuaDocGenericDef findOwnerClassGenericDef(LuaFuncBodyOwner funcBodyOwner, String name) {
        SearchContext context = SearchContext.Companion.get(funcBodyOwner.getProject());
        ITy parentType = funcBodyOwner.guessParentType(context);

        if (parentType instanceof ITyClass) {
            ITyClass cls = (ITyClass) parentType;
            LuaClass luaClass = LuaShortNamesManager.Companion.getInstance(context.getProject()).findClass(cls.getClassName(), context);

            if (luaClass instanceof LuaDocTagClass) {
                LuaDocTagClass docTagClass = (LuaDocTagClass) luaClass;

                for (LuaDocGenericDef genericDef : docTagClass.getGenericDefList()) {
                    if (name.equals(genericDef.getId().getText()))
                    {
                        return genericDef;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static LuaDocGenericDef findOwnerClassGenericDef(@Nullable LuaCommentOwner commentOwner, String name) {
        if (commentOwner instanceof LuaAssignStat) {
            LuaAssignStat assignStat = (LuaAssignStat) commentOwner;
            LuaExprList exprList = assignStat.getValueExprList();

            if (exprList == null || exprList.getExprList().size() != 1) {
                return null;
            }

            commentOwner = exprList.getExprList().get(0);
        }

        if (commentOwner instanceof LuaFuncBodyOwner) {
            LuaFuncBodyOwner funcBodyOwner = (LuaFuncBodyOwner) commentOwner;
            return findOwnerClassGenericDef(funcBodyOwner, name);
        }

        return null;
    }

    private static final Class[] WS = {PsiWhiteSpace.class};
    private static final Class[] WS_COMMENTS = {PsiWhiteSpace.class, PsiComment.class};

    public static PsiElement skipWhitespacesBackward(@Nullable PsiElement element) {
        return PsiTreeUtil.skipSiblingsBackward(element, WS);
    }

    public static PsiElement skipWhitespacesAndCommentsBackward(@Nullable PsiElement element) {
        return PsiTreeUtil.skipSiblingsBackward(element, WS_COMMENTS);
    }

    public static PsiElement skipWhitespacesForward(@Nullable PsiElement element) {
        return PsiTreeUtil.skipSiblingsForward(element, WS);
    }

    public static PsiElement skipWhitespacesAndCommentsForward(@Nullable PsiElement element) {
        return PsiTreeUtil.skipSiblingsForward(element, WS_COMMENTS);
    }

    @NotNull
    public static ITy findContextClass(PsiElement current, SearchContext context) {
        while (!(current instanceof PsiFile)) {
            if (current instanceof LuaFuncBodyOwner) {
                LuaFuncBodyOwner funcBodyOwner = (LuaFuncBodyOwner) current;
                ITy ty = funcBodyOwner.guessParentType(context);

                if (ty != null) {
                    return ty;
                }
            } else if (current instanceof LuaAssignStat) {
                LuaAssignStat assignStat = (LuaAssignStat) current;
                LuaExprList valueExprList = assignStat.getValueExprList();

                if (valueExprList != null && valueExprList.getExprList().size() == 1) {
                    LuaExpr luaExpr = valueExprList.getExprList().get(0);
                    LuaExpr varExpr = assignStat.getVarExprList().getExprList().get(0);

                    if (luaExpr instanceof LuaFuncBodyOwner && varExpr instanceof LuaIndexExpr) {
                        LuaIndexExpr indexExpr = (LuaIndexExpr) varExpr;

                        if (indexExpr.getExprList().size() != 1 || !indexExpr.getFirstChild().getText().equals(Constants.WORD_SELF)) {
                            ITy ty = indexExpr.guessParentType(context);

                            if (ty != Ty.Companion.getUNKNOWN()) {
                                return ty;
                            }
                        }
                    }
                }
            }

            current = current.getParent();
        }
        return Ty.Companion.getUNKNOWN();
    }

    @Nullable
    public static LuaDocGenericDef findGenericDef(String name, PsiElement current, boolean ancestralOnly) {
        LuaDocFunctionTy fn = findAncestorOfType(current, LuaDocFunctionTy.class);

        while (fn != null) {
            for (LuaDocGenericDef genericDef : fn.getGenericDefList()) {
                if (name.equals(genericDef.getId().getText())) {
                    return genericDef;
                }
            }

            fn = findAncestorOfType(fn, LuaDocFunctionTy.class);
        }

        if (current instanceof LuaDocPsiElement) {
            LuaComment comment = LuaCommentUtil.INSTANCE.findContainer((LuaDocPsiElement) current);
            LuaDocGenericDef genericDef = comment.findGeneric(name);

            if (genericDef != null) {
                return genericDef;
            }
        }

        if (current instanceof LuaCommentOwner) {
            LuaCommentOwner commentOwner = (LuaCommentOwner) current;

            if (!ancestralOnly) {
                LuaComment comment = commentOwner.getComment();
                LuaDocGenericDef genericDef = comment != null ? comment.findGeneric(name) : null;

                if (genericDef != null) {
                    return genericDef;
                }
            }

            LuaDocGenericDef classGenericDef = findOwnerClassGenericDef(commentOwner, name);

            if (classGenericDef != null) {
                return classGenericDef;
            }
        }

        LuaCommentOwner ancestralCommentOwner = findAncestorOfType(current, LuaCommentOwner.class);

        while (ancestralCommentOwner != null) {
            LuaComment ancestorComment = ancestralCommentOwner.getComment();
            LuaDocGenericDef genericDef = ancestorComment != null ? ancestorComment.findGeneric(name) : null;

            if (genericDef != null) {
                return genericDef;
            }

            LuaDocGenericDef classGenericDef = findOwnerClassGenericDef(ancestralCommentOwner, name);

            if (classGenericDef != null) {
                return classGenericDef;
            }

            ancestralCommentOwner = findAncestorOfType(ancestralCommentOwner, LuaCommentOwner.class);
        }

        return null;
    }

    @Nullable
    public static LuaDocGenericDef findGenericDef(String name, PsiElement current) {
        return findGenericDef(name, current, false);
    }

    @Nullable
    public static LuaDocGenericDef findGenericDef(String name, SearchContext searchContext) {
        PsiElement element = searchContext.getElement();
        return element != null ? findGenericDef(name, element) : null;
    }

    @Nullable
    public static LuaClass findClass(String name, SearchContext searchContext) {
        LuaDocGenericDef luaDocGenericDef = findGenericDef(name, searchContext);

        return luaDocGenericDef != null ? luaDocGenericDef
                : LuaShortNamesManager.Companion.getInstance(searchContext.getProject()).findClass(name, searchContext);
    }

    @Nullable
    public static LuaTypeDef findType(String name, SearchContext searchContext) {
        LuaDocGenericDef luaDocGenericDef = findGenericDef(name, searchContext);

        return luaDocGenericDef != null ? luaDocGenericDef
                : LuaShortNamesManager.Companion.getInstance(searchContext.getProject()).findType(name, searchContext);
    }

    public static void processChildren(PsiElement parent, PsiElementProcessor<PsiElement> processor) {
        PsiElement child = parent.getFirstChild();
        while (child != null) {
            if (processor.execute(child)) {
                child = child.getNextSibling();
            } else break;
        }
    }
}
