/*
 * Copyright (c) 2023 Risu
 *
 *  This source code is licensed under the MIT license found in the
 *  LICENSE file in the root directory of this source tree.
 *
 */

package io.github.risu729.nistir;

import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("RegExpAnonymousGroup")
@UtilityClass
class JcampDx {

  private final Pattern X_FACTOR = Pattern.compile("##XFACTOR=(.+)");
  private final Pattern XY_DATA = Pattern.compile("##XYDATA=.+\\R([\\s\\S]+)\\R##END=");
  private final Pattern XY_DATA_DELIMITERS = Pattern.compile("[ -]");

  long getPeekWavenumber(@NotNull String jdx) {

    var xFactor = Math.round(Float.parseFloat(getCapturingGroup(jdx, X_FACTOR)));

    var xyData =
        getCapturingGroup(jdx, XY_DATA)
            .lines()
            .map(
                line -> XY_DATA_DELIMITERS
                        .splitAsStream(line)
                        .mapToDouble(Double::parseDouble)
                        .toArray())
            .<Map.Entry<Long, Double>>mapMulti(
                ((arr, consumer) -> {
                  var xStart = Math.round(arr[0]);
                  for (int i = 1; i < arr.length; i++) {
                    consumer.accept(Map.entry(xStart + (long) (i - 1) * xFactor, arr[i]));
                  }
                }))
            .toList();

    var comparator =
        Comparator.comparingDouble((Map.Entry<Long, Double> entry) -> entry.getValue());
    if (jdx.contains("TRANSMITTANCE")) {
      comparator = comparator.reversed();
    }

    return xyData.stream().max(comparator).map(Map.Entry::getKey).orElseThrow();
  }

  private @NotNull String getCapturingGroup(@NotNull String input, @NotNull Pattern pattern) {
    return pattern
        .matcher(input)
        .results()
        .map(result -> result.group(1))
        .findFirst()
        .orElseThrow();
  }
}
