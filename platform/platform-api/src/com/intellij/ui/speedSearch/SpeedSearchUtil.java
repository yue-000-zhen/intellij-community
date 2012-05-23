/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: spLeaner
 */
public final class SpeedSearchUtil {

  private SpeedSearchUtil() {
  }

  public static void appendFragmentsForSpeedSearch(@NotNull final JComponent speedSearchEnabledComponent, @NotNull final String text,
                                                   @NotNull final SimpleTextAttributes attributes, final boolean selected,
                                                   @NotNull final SimpleColoredComponent simpleColoredComponent) {
    final SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(speedSearchEnabledComponent);
    if (speedSearch != null) {
      final Iterable<TextRange> fragments = speedSearch.matchingFragments(text);
      if (fragments != null) {
        final Color fg = attributes.getFgColor();
        final Color bg = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        final int style = attributes.getStyle();
        final SimpleTextAttributes plain = new SimpleTextAttributes(style, fg);
        final SimpleTextAttributes highlighted = new SimpleTextAttributes(bg, fg, null, style | SimpleTextAttributes.STYLE_SEARCH_MATCH);
        appendColoredFragments(simpleColoredComponent, text, fragments, plain, highlighted);
        return;
      }
    }
    simpleColoredComponent.append(text, attributes);
  }

  public static void appendColoredFragmentForMatcher(final String text,
                                                     final SimpleColoredComponent component,
                                                     final SimpleTextAttributes attributes,
                                                     final Matcher matcher,
                                                     final Color selectedBg,
                                                     final boolean selected) {
    if (!(matcher instanceof MinusculeMatcher) || (Registry.is("ide.highlight.match.in.selected.only") && !selected)) {
      component.append(text, attributes);
      return;
    }

    final Iterable<TextRange> iterable = ((MinusculeMatcher)matcher).matchingFragments(text);
    if (iterable != null) {
      final Color fg = attributes.getFgColor();
      final int style = attributes.getStyle();
      final SimpleTextAttributes plain = new SimpleTextAttributes(style, fg);
      final SimpleTextAttributes highlighted = new SimpleTextAttributes(selectedBg, fg, null, style | SimpleTextAttributes.STYLE_SEARCH_MATCH);
      appendColoredFragments(component, text, iterable, plain, highlighted);
    }
    else {
      component.append(text, attributes);
    }
  }

  public static void appendColoredFragments(final SimpleColoredComponent simpleColoredComponent,
                                            final String text,
                                            Iterable<TextRange> colored,
                                            final SimpleTextAttributes plain, final SimpleTextAttributes highlighted) {
    final List<Pair<String, Integer>> searchTerms = new ArrayList<Pair<String, Integer>>();
    for (TextRange fragment : colored) {
      searchTerms.add(Pair.create(fragment.substring(text), fragment.getStartOffset()));
    }

    final int[] lastOffset = {0};
    ContainerUtil.process(searchTerms, new Processor<Pair<String, Integer>>() {
      @Override
      public boolean process(Pair<String, Integer> pair) {
        if (pair.second > lastOffset[0]) {
          simpleColoredComponent.append(text.substring(lastOffset[0], pair.second), plain);
        }

        simpleColoredComponent.append(text.substring(pair.second, pair.second + pair.first.length()), highlighted);
        lastOffset[0] = pair.second + pair.first.length();
        return true;
      }
    });

    if (lastOffset[0] < text.length()) {
      simpleColoredComponent.append(text.substring(lastOffset[0]), plain);
    }
  }
}
