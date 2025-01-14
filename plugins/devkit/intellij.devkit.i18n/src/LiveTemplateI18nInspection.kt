// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n

import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.jetbrains.idea.devkit.dom.liveTemplates.Template
import org.jetbrains.idea.devkit.dom.liveTemplates.TemplateSet
import org.jetbrains.idea.devkit.i18n.PluginXmlI18nInspection.highlightNonLocalizableElement

class LiveTemplateI18nInspection: BasicDomElementsInspection<TemplateSet>(TemplateSet::class.java) {
  override fun checkDomElement(element: DomElement, holder: DomElementAnnotationHolder, helper: DomHighlightingHelper) {
    if (element is Template) {
      highlightNonLocalizableElement(holder, element.description, "description", Fix())
    }
  }

  private class Fix: PluginXmlI18nInspection.InspectionI18NQuickFix("description") {
    override fun getName(xml: XmlTag): String? {
      return xml.getAttributeValue("name")
    }

    override fun setBundle(xml: XmlTag, bundleQName: String) {
      xml.setAttribute("resource-bundle", bundleQName)
    }
  }
}