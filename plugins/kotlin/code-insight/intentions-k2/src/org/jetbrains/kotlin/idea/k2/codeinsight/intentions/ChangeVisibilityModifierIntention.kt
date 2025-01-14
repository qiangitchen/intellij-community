// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfType
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfTypeSafe
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpected
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectedDeclarationIfAny
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

abstract class ChangeVisibilityModifierIntention protected constructor(
    val modifier: KtModifierKeywordToken
) : AbstractKotlinApplicableModCommandIntention<KtDeclaration>(KtDeclaration::class) {
    override fun getActionName(element: KtDeclaration): String {
        val targetVisibility = modifier.toVisibility()
        val explicitVisibility = element.modifierList?.visibilityModifierType()?.value
        return when {
            element is KtPropertyAccessor
                    && targetVisibility == Visibilities.Public
                    && element.isSetter
                    && explicitVisibility != null -> KotlinBundle.message("remove.0.modifier", explicitVisibility)

            element is KtPrimaryConstructor && !element.hasConstructorKeyword() -> {
                // The caret position is like: `class Foo<caret>()`, custom message is shown to clarify that the modifier will be added to
                // the primary constructor instead of the class
                KotlinBundle.message("make.primary.constructor.0", modifier.value)
            }

            else -> KotlinBundle.message("make.0", modifier.value)
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("make.0", modifier.value)

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclaration> = applicabilityRanges { declaration ->
        val keywordRange = when (declaration) {
            is KtNamedFunction -> declaration.funKeyword?.textRange
            is KtProperty -> declaration.valOrVarKeyword.textRange
            is KtPropertyAccessor -> declaration.namePlaceholder.textRange
            is KtClass -> declaration.getClassOrInterfaceKeyword()?.textRange
            is KtObjectDeclaration -> declaration.getObjectKeyword()?.textRange
            is KtPrimaryConstructor -> declaration.getConstructorKeyword()?.textRange
            is KtSecondaryConstructor -> declaration.getConstructorKeyword().textRange
            is KtParameter -> declaration.valOrVarKeyword?.textRange
            is KtTypeAlias -> declaration.getTypeAliasKeyword()?.textRange
            else -> null
        }
        val withoutKeywordRange = when (declaration) {
            is KtPrimaryConstructor -> declaration.valueParameterList?.let {
                TextRange.from(it.startOffset, 0) // first position before constructor e.g. Foo<caret>()
            }

            else -> null
        }
        listOfNotNull(declaration.modifierList?.range, keywordRange, withoutKeywordRange)
            .map { range -> range.relativeTo(declaration) }
    }

    override fun isApplicableByPsi(element: KtDeclaration): Boolean {
        val modifierList = element.modifierList
        if (modifierList?.hasModifier(modifier) == true) return false
        if (KtPsiUtil.isLocal((element as? KtPropertyAccessor)?.property ?: element)) return false
        return true
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtDeclaration): Boolean {
        val symbol = element.getSymbolOfTypeSafe<KtSymbolWithVisibility>()
        val targetVisibility = modifier.toVisibility()
        if (symbol?.visibility == targetVisibility) return false
        val modifierList = element.modifierList

        if (modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true) {
            val callableDescriptor = symbol as? KtCallableSymbol ?: return false
            // cannot make visibility less than (or non-comparable with) any of the supers
            if (callableDescriptor.getAllOverriddenSymbols()
                    .map { (it as? KtSymbolWithVisibility)?.visibility?.compareTo(targetVisibility) }
                    .any { it == null || it > 0 }
            ) return false
        }

        if (element is KtPropertyAccessor) {
            if (element.isGetter) return false
            if (targetVisibility == Visibilities.Public) {
                if (element.modifierList?.visibilityModifierType()?.value == null) return false
            } else {
                val propVisibility = element.property.getSymbolOfType<KtSymbolWithVisibility>().visibility
                if (propVisibility == targetVisibility) return false
                val compare = targetVisibility.compareTo(propVisibility)
                if (compare == null || compare > 0) return false
            }
        }
        return true
    }

    override fun apply(element: KtDeclaration, context: ActionContext, updater: ModPsiUpdater) {
        (element.actualsForExpected() + element.expectedDeclarationIfAny() + element).filterNotNull().forEach { declaration ->
            val psiFactory = KtPsiFactory(declaration.project)
            declaration.setVisibility(modifier)
            if (declaration is KtPropertyAccessor) {
                declaration.modifierList?.nextSibling?.replace(psiFactory.createWhiteSpace())
            }
        }
    }

    class Public : ChangeVisibilityModifierIntention(KtTokens.PUBLIC_KEYWORD), HighPriorityAction {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean {
            return element.canBePublic() && super.isApplicableByPsi(element)
        }
    }

    class Private : ChangeVisibilityModifierIntention(KtTokens.PRIVATE_KEYWORD), HighPriorityAction {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean {
            return element.canBePrivate() && super.isApplicableByPsi(element)
        }
    }

    class Protected : ChangeVisibilityModifierIntention(KtTokens.PROTECTED_KEYWORD) {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean {
            return element.canBeProtected() && super.isApplicableByPsi(element)
        }
    }

    class Internal : ChangeVisibilityModifierIntention(KtTokens.INTERNAL_KEYWORD) {
        override fun isApplicableByPsi(element: KtDeclaration): Boolean {
            return element.canBeInternal() && super.isApplicableByPsi(element)
        }
    }
}