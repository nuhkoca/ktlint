package com.pinterest.ktlint.ruleset.experimental

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType.CLASS_BODY
import com.pinterest.ktlint.core.ast.ElementType.CONST_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.FILE
import com.pinterest.ktlint.core.ast.ElementType.IDENTIFIER
import com.pinterest.ktlint.core.ast.ElementType.MODIFIER_LIST
import com.pinterest.ktlint.core.ast.ElementType.OBJECT_DECLARATION
import com.pinterest.ktlint.core.ast.ElementType.OVERRIDE_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.PRIVATE_KEYWORD
import com.pinterest.ktlint.core.ast.ElementType.PROPERTY
import com.pinterest.ktlint.core.ast.ElementType.PROPERTY_ACCESSOR
import com.pinterest.ktlint.core.ast.ElementType.VAL_KEYWORD
import com.pinterest.ktlint.core.ast.children
import com.pinterest.ktlint.ruleset.experimental.internal.regExIgnoringDiacriticsAndStrokesOnLetters
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType

/**
 * https://kotlinlang.org/docs/coding-conventions.html#function-names
 * https://kotlinlang.org/docs/coding-conventions.html#property-names
 */
public class PropertyNamingRule : Rule("$EXPERIMENTAL_RULE_SET_ID:property-naming") {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        node
            .takeIf { node.elementType == PROPERTY }
            ?.let { property -> visitProperty(property, emit) }
    }

    private fun visitProperty(
        property: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        property
            .findChildByType(IDENTIFIER)
            ?.let { identifier ->
                when {
                    property.hasCustomGetter() -> {
                        // Can not reliably determine whether the value is immutable or not
                    }
                    property.isBackingProperty() -> {
                        visitBackingProperty(identifier, emit)
                    }
                    property.hasConstModifier() ||
                        property.isTopLevelValue() ||
                        property.isObjectValue() -> {
                        visitConstProperty(identifier, emit)
                    }
                    else -> {
                        visitNonConstProperty(identifier, emit)
                    }
                }
            }
    }

    private fun visitBackingProperty(
        identifier: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        identifier
            .text
            .takeUnless { it.matches(BACKING_PROPERTY_LOWER_CAMEL_CASE_REGEXP) }
            ?.let {
                emit(identifier.startOffset, "Backing property name should start with underscore followed by lower camel case", false)
            }
    }

    private fun visitConstProperty(
        identifier: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        identifier
            .text
            .takeUnless { it.matches(SCREAMING_SNAKE_CASE_REGEXP) }
            ?.let {
                emit(identifier.startOffset, "Property name should use the screaming snake case notation when the value can not be changed", false)
            }
    }

    private fun visitNonConstProperty(
        identifier: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        identifier
            .text
            .takeUnless { it.matches(LOWER_CAMEL_CASE_REGEXP) }
            ?.let {
                emit(identifier.startOffset, "Property name should start with a lowercase letter and use camel case", false)
            }
    }

    private fun ASTNode.hasCustomGetter() =
        findChildByType(PROPERTY_ACCESSOR)
            ?.firstChildNode
            ?.text == "get"

    private fun ASTNode.hasConstModifier() =
        hasModifier(CONST_KEYWORD)

    private fun ASTNode.hasModifier(iElementType: IElementType) =
        findChildByType(MODIFIER_LIST)
            ?.children()
            .orEmpty()
            .any { it.elementType == iElementType }

    private fun ASTNode.isTopLevelValue() =
        treeParent.elementType == FILE && containsValKeyword()

    private fun ASTNode.containsValKeyword() =
        children().any { it.elementType == VAL_KEYWORD }

    private fun ASTNode.isObjectValue() =
        treeParent.elementType == CLASS_BODY &&
            treeParent?.treeParent?.elementType == OBJECT_DECLARATION &&
            containsValKeyword() &&
            !hasModifier(OVERRIDE_KEYWORD)

    private fun ASTNode.isBackingProperty() =
        takeIf { hasModifier(PRIVATE_KEYWORD) }
            ?.findChildByType(IDENTIFIER)
            ?.takeIf { it.text.startsWith("_") }
            ?.let { identifier ->
                this.hasPublicProperty(identifier.text.removePrefix("_"))
            }
            ?: false

    private fun ASTNode.hasPublicProperty(identifier: String) =
        treeParent
            .children()
            .filter { it.elementType == PROPERTY }
            .mapNotNull { it.findChildByType(IDENTIFIER) }
            .any { it.text == identifier }

    private companion object {
        val LOWER_CAMEL_CASE_REGEXP = "[a-z][a-zA-Z0-9]*".regExIgnoringDiacriticsAndStrokesOnLetters()
        val SCREAMING_SNAKE_CASE_REGEXP = "[A-Z][_A-Z0-9]*".regExIgnoringDiacriticsAndStrokesOnLetters()
        val BACKING_PROPERTY_LOWER_CAMEL_CASE_REGEXP = "_[a-z][a-zA-Z0-9]*".regExIgnoringDiacriticsAndStrokesOnLetters()
    }
}
